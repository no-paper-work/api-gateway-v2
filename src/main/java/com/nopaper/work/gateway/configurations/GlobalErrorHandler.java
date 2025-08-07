/**
 * @package com.nopaper.work.gateway.configurations -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 6:38:50 pm
 * @git 
 */
package com.nopaper.work.gateway.configurations;

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
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID; // Import UUID

@Component
@Order(-1)
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred.";

        if (ex instanceof ResponseStatusException rse) {
            status = (HttpStatus) rse.getStatusCode();
            message = rse.getReason();
        } else {
            log.error("Unhandled exception in gateway: ", ex);
        }

        // ✅ Retrieve the requestId from the exchange attributes
        // This attribute is set in your RequestLoggingFilter.java
        UUID traceId = null;
        Object traceIdAttr = exchange.getAttributes().get("requestId");
        if (traceIdAttr instanceof String) {
            try {
                traceId = UUID.fromString((String) traceIdAttr);
            } catch (IllegalArgumentException e) {
                log.warn("Could not parse requestId attribute to UUID: {}", traceIdAttr);
            }
        }

        // Build the standardized API response, now with trace_identity
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .statusCode(status.value())
                .status(status)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .trace_identity(traceId) // Set the trace identity
                .build();

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] errorBytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer dataBuffer = bufferFactory.wrap(errorBytes);
            return exchange.getResponse().writeWith(Mono.just(dataBuffer));
        } catch (JsonProcessingException e) {
            log.error("Error writing JSON error response", e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }
}