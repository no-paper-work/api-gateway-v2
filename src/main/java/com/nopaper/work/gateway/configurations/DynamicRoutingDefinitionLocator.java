/**
 * @package com.nopaper.work.gateway.configurations -> gateway
 * @author saikatbarman
 * @date 2025 03-Aug-2025 12:49:16 am
 * @git 
 */
package com.nopaper.work.gateway.configurations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.constants.GatewayConstant;
import com.nopaper.work.gateway.entity.Routes;
import com.nopaper.work.gateway.repository.RouteRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
//@RequiredArgsConstructor
public class DynamicRoutingDefinitionLocator implements RouteDefinitionLocator {

    private final RouteRepository apiRouteRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    
    public DynamicRoutingDefinitionLocator(
            RouteRepository apiRouteRepository,
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${gateway.routes.cache-ttl}") Duration cacheTtl)
    {
        this.apiRouteRepository = apiRouteRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        // Try to get from cache first, otherwise fetch from DB and cache the result
        return redisTemplate.opsForValue().get(GatewayConstant.GATEWAY_ROUTES_CACHE_KEY)
                .flatMapMany(this::deserializeRoutes)
                .switchIfEmpty(fetchFromDbAndCache())
                .doOnError(error -> log.error("Failed to load routes, either from cache or DB.", error));
    }

    private Flux<RouteDefinition> fetchFromDbAndCache() {
        log.info("Cache empty or expired. Fetching routes from PostgreSQL.");
        return apiRouteRepository.findAll()
                .flatMap(this::convertToRouteDefinition)
                .collectList()
                .doOnNext(this::cacheRouteDefinitions)
                .flatMapMany(Flux::fromIterable);
    }

    private void cacheRouteDefinitions(List<RouteDefinition> routeDefinitions) {
        log.info("Caching {} routes with TTL: {}", routeDefinitions.size(), cacheTtl);
        try {
            String serializedRoutes = objectMapper.writeValueAsString(routeDefinitions);
            redisTemplate.opsForValue()
                         .set(GatewayConstant.GATEWAY_ROUTES_CACHE_KEY, serializedRoutes, cacheTtl)
                         .subscribe();
        } catch (JsonProcessingException e) {
            log.error("Could not serialize routes for caching.", e);
        }
    }

    private Flux<RouteDefinition> deserializeRoutes(String serializedRoutes) {
        log.info("Loading routes from Redis cache.");
        try {
            TypeReference<List<RouteDefinition>> typeRef = new TypeReference<>() {};
            List<RouteDefinition> routeDefinitions = objectMapper.readValue(serializedRoutes, typeRef);
            return Flux.fromIterable(routeDefinitions);
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize routes from cache. Will attempt to fetch from DB.", e);
            // If deserialization fails, clear the bad cache entry and fetch from DB
            return redisTemplate.opsForValue().delete(GatewayConstant.GATEWAY_ROUTES_CACHE_KEY).thenMany(fetchFromDbAndCache());
        }
    }
/*
    private Mono<RouteDefinition> convertToRouteDefinition(Routes apiRoute) {
        try {
            
            RouteDefinition routeDefinition = new RouteDefinition();
            
            routeDefinition.setId(apiRoute.getRouteId());
            routeDefinition.setUri(new URI(apiRoute.getUri()));
            
            // Jackson TypeReferences to correctly deserialize the JSON arrays
            TypeReference<List<org.springframework.cloud.gateway.handler.predicate.PredicateDefinition>> predicateTypeRef = new TypeReference<>() {};
            TypeReference<List<org.springframework.cloud.gateway.filter.FilterDefinition>> filterTypeRef = new TypeReference<>() {};
            
            routeDefinition.setPredicates(objectMapper.readValue(apiRoute.getPredicates(), predicateTypeRef)); // Parse predicates (e.g., Path, Method) from the JSON string
            routeDefinition.setFilters(objectMapper.readValue(apiRoute.getFilters(), filterTypeRef)); // Parse filters and dynamically add the rate limiter if configured
            
            return Mono.just(routeDefinition);
        } catch (Exception e) {
            log.error("Failed to parse route definition from DB: {}", apiRoute.getRouteId(), e);
            return Mono.empty();
        }
    }
*/    
    private Mono<RouteDefinition> convertToRouteDefinition(Routes apiRoute) {
        try {
            RouteDefinition routeDefinition = new RouteDefinition();
            routeDefinition.setId(apiRoute.getRouteId());
            routeDefinition.setUri(new URI(apiRoute.getUri()));
            
            // Add the encryption key to the route's metadata for later retrieval in filters
            if (StringUtils.hasText(apiRoute.getEncryptionKey())) {
                 routeDefinition.getMetadata().put("encryption_key", apiRoute.getEncryptionKey());
            }
            
            // --- Jackson TypeReferences to correctly deserialize the JSON arrays. The existing predicate and filter parsing logic ---
            TypeReference<List<PredicateDefinition>> predicateTypeRef = new TypeReference<>() {};
            TypeReference<List<FilterDefinition>> filterTypeRef = new TypeReference<>() {};

            // Make the filter list mutable
            List<FilterDefinition> filters = new java.util.ArrayList<>(
                    objectMapper.readValue(apiRoute.getFilters(), filterTypeRef)
            );
            routeDefinition.setPredicates(objectMapper.readValue(apiRoute.getPredicates(), predicateTypeRef));

            // --- ✅ Dynamically add the rate limiter filter ---
            if (apiRoute.isRateLimitEnabled()) {
                FilterDefinition rateLimiterFilter = new FilterDefinition();
                rateLimiterFilter.setName("RequestRateLimiter");

                Map<String, String> args = new HashMap<>();
                // args.put("redis-rate-limiter.replenishRate", String.valueOf(apiRoute.getRateLimitReplenishRate()));
                // args.put("redis-rate-limiter.burstCapacity", String.valueOf(apiRoute.getRateLimitBurstCapacity()));
                // ✅ Use camelCase keys without the prefix
                args.put("replenishRate", String.valueOf(apiRoute.getRateLimitReplenishRate()));
                args.put("burstCapacity", String.valueOf(apiRoute.getRateLimitBurstCapacity()));
                
                // Use SpEL to reference the bean by the name we stored in the DB
                log.info("Loading getKeyResolverName {}", apiRoute.getKeyResolverName());
                args.put("key-resolver", "#{@" + apiRoute.getKeyResolverName() + "}");

                rateLimiterFilter.setArgs(args);
                filters.add(rateLimiterFilter);
            }

            routeDefinition.setFilters(filters);
            return Mono.just(routeDefinition);
        } catch (Exception e) {
            log.error("Failed to parse route definition from DB: {}", apiRoute.getRouteId(), e);
            return Mono.empty();
        }
    }
    
}