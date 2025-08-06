/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 12:53:34 am
 * @git 
 */
package com.nopaper.work.gateway.filter;

/**
 * 
 */

import com.nopaper.work.gateway.services.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * A global filter that checks if the IP address of an incoming request
 * is blacklisted. This filter runs before any routing logic.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IpBlacklistFilter implements GlobalFilter, Ordered {

    private final SecurityService securityService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();

        // Check if the IP is blacklisted using the caching service
        return securityService.isIpBlacklisted(clientIp)
            .flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    log.warn("Blocking request from blacklisted IP: {}", clientIp);
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete(); // Block the request
                }
                log.debug("IP {} is not blacklisted. Allowing request to proceed.", clientIp);
                return chain.filter(exchange); // Allow the request to continue
            });
    }

    /**
     * Sets the order of the filter. We want this to run very early in the chain.
     * HIGHEST_PRECEDENCE ensures it runs before almost all other filters.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}