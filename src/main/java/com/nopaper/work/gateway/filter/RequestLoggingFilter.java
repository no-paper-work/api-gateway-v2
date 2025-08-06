/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 1:05:46 am
 * @git 
 */
package com.nopaper.work.gateway.filter;

/**
 * 
 */

import com.nopaper.work.gateway.services.LoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * A global filter responsible for logging every incoming request and its outcome.
 * It captures details before and after the request is processed by the gateway.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private final LoggingService loggingService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        // Add unique identifiers to the exchange for downstream services or logs
        exchange.getAttributes().put("requestId", requestId);
        // Assuming a traceId might be passed from an upstream system, or we generate one
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-ID");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        exchange.getAttributes().put("traceId", traceId);

        // The .then() operator executes after the filter chain has completed
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request {} to {} completed with status {} in {} ms",
                    requestId,
                    exchange.getRequest().getURI(),
                    exchange.getResponse().getStatusCode(),
                    duration);

            // Use the existing LoggingService to save the log asynchronously
            loggingService.logRequest(exchange, duration).subscribe();
        }));
    }

    /**
     * Set the order. Run this filter after the IP Blacklist filter
     * to avoid logging requests that are immediately blocked.
     */
    @Override
    public int getOrder() {
        // HIGHEST_PRECEDENCE is -2147483648. We set this to run just after.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}