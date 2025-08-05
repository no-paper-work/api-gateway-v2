/**
 * @package com.nopaper.work.gateway.configurations -> gateway
 * @author saikatbarman
 * @date 2025 02-Aug-2025 11:21:11 pm
 * @git 
 */
package com.nopaper.work.gateway.configurations;

/**
 * 
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.entity.Routes;
import com.nopaper.work.gateway.repository.RouteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

//@Component
@Slf4j
@RequiredArgsConstructor // Using Lombok for constructor injection
public class DatabaseRouteDefinitionLocator implements RouteDefinitionLocator {

    private final RouteRepository apiRouteRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return apiRouteRepository.findAll()
            .flatMap(this::convertToRouteDefinition)
            .doOnNext(routeDef -> log.info("Loaded route from DB: {} - with {} ", routeDef.getId(), routeDef.toString()))
            .doOnError(error -> log.error("Error loading routes from DB", error));
    }

    private Mono<RouteDefinition> convertToRouteDefinition(Routes apiRoute) {
        try {
            RouteDefinition routeDefinition = new RouteDefinition();
            routeDefinition.setId(apiRoute.getRouteId());
            routeDefinition.setUri(new URI(apiRoute.getUri()));
            
            // Add the encryption key to the route's metadata for later retrieval in filters
            if (StringUtils.hasText(apiRoute.getEncryptionKey())) {
                 routeDefinition.getMetadata().put("encryption_key", apiRoute.getEncryptionKey());
            }

            // Jackson TypeReferences to correctly deserialize the JSON arrays
            TypeReference<List<PredicateDefinition>> predicateTypeRef = new TypeReference<>() {}; // Parse predicates (e.g., Path, Method) from the JSON string
            TypeReference<List<FilterDefinition>> filterTypeRef = new TypeReference<>() {}; // Parse filters and dynamically add the rate limiter if configured

            routeDefinition.setPredicates(objectMapper.readValue(apiRoute.getPredicates(), predicateTypeRef));
            routeDefinition.setFilters(objectMapper.readValue(apiRoute.getFilters(), filterTypeRef));

            return Mono.just(routeDefinition);
        } catch (Exception e) {
            log.error("Failed to parse route definition: {}", apiRoute.getRouteId(), e);
            return Mono.empty(); // Skip invalid routes
        }
    }
}