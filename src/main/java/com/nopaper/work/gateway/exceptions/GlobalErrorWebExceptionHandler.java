/**
 * @package com.nopaper.work.gateway.exceptions -> gateway
 * @author saikatbarman
 * @date 2025 08-Aug-2025 11:32:31 am
 * @git 
 */
package com.nopaper.work.gateway.exceptions;

/**
 * 
 */


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
@Order(-1) // This gives our handler the highest precedence.
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final CustomErrorAttributes globalErrorAttributes; // Inject our custom attributes class.

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        
        // Create a ServerRequest to pass to the attributes class.
        ServerRequest serverRequest = ServerRequest.create(exchange, java.util.Collections.emptyList());
        Map<String, Object> errorAttributes = this.globalErrorAttributes.getErrorAttributes(serverRequest, org.springframework.boot.web.error.ErrorAttributeOptions.defaults());

        int statusCode = (int) errorAttributes.getOrDefault("status", 500);
        String message = (String) errorAttributes.getOrDefault("message", "An unexpected error occurred.");
        
        HttpStatus status = HttpStatus.valueOf(statusCode);
        
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Unhandled exception in gateway: ", ex);
        }

        // Build the final, standardized API response.
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .statusCode(status.value())
                .status(status)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .trace_identity(getTraceId(exchange))
                .build();

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] errorBytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(errorBytes);
            return exchange.getResponse().writeWith(Mono.just(dataBuffer));
        } catch (JsonProcessingException e) {
            log.error("Error writing JSON error response", e);
            return exchange.getResponse().setComplete();
        }
    }

    private UUID getTraceId(ServerWebExchange exchange) {
        Object traceIdAttr = exchange.getAttributes().get("requestId");
        if (traceIdAttr instanceof String) {
            try {
                return UUID.fromString((String) traceIdAttr);
            } catch (IllegalArgumentException e) {
                log.warn("Could not parse requestId attribute to UUID: {}", traceIdAttr);
            }
        }
        return null;
    }
}