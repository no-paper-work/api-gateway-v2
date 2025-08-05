/**
 * @package com.nopaper.work.gateway.configurations -> gateway
 * @author saikatbarman
 * @date 2025 03-Aug-2025 4:46:42 pm
 * @git 
 */
package com.nopaper.work.gateway.configurations;

import java.util.Objects;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * This bean defines how to resolve the key for rate limiting.
     * Here, we are using the client's IP address as the key.
     * The bean name "ipKeyResolver" matches what we stored in the database.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress());
    }
    
 // ✅ Add this bean definition
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // Provide default values for replenish rate and burst capacity.
        // These are used if a route doesn't have specific rate limit values.
        return new RedisRateLimiter(10, 20);
    }
}