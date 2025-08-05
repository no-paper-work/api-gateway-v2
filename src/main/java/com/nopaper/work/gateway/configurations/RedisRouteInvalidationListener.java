/**
 * @package com.nopaper.work.gateway.configurations -> gateway
 * @author saikatbarman
 * @date 2025 03-Aug-2025 12:48:09 am
 * @git 
 */
package com.nopaper.work.gateway.configurations;

/**
 * 
 */

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisRouteInvalidationListener {

    private static final String INVALIDATION_CHANNEL = "route-updates";
    private static final String GATEWAY_ROUTES_CACHE_KEY = "gateway_routes_v1";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @PostConstruct
    public void setupSubscription() {
        log.info("Subscribing to Redis channel: {}", INVALIDATION_CHANNEL);
        redisTemplate.listenTo(ChannelTopic.of(INVALIDATION_CHANNEL))
            .map(ReactiveSubscription.Message::getMessage)
            .doOnNext(message -> log.info("Received invalidation message. Clearing route cache."))
            .flatMap(message -> redisTemplate.opsForValue().delete(GATEWAY_ROUTES_CACHE_KEY))
            .subscribe(
                success -> log.info("Route cache cleared successfully."),
                error -> log.error("Error on cache invalidation.", error)
            );
    }
}