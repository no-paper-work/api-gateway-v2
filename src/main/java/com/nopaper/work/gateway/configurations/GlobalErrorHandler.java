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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(-1) // This gives our handler the highest precedence to catch all errors first.
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; // Default status
        String message = "An unexpected internal server error occurred.";

        // ✅ This is the crucial logic block to handle different exception types.
        if (ex instanceof ResponseStatusException rse) {
            // This handles standard Spring REST exceptions (e.g., 400 Bad Request).
            status = (HttpStatus) rse.getStatusCode();
            message = rse.getReason();
        } else if (ex.getClass().getName().equals("org.springframework.web.reactive.resource.NoResourceFoundException")) {
            // This specifically targets the exception that causes the Whitelabel 404 page.
            status = HttpStatus.NOT_FOUND;
            message = "The requested resource was not found.";
        } else {
            // This is a catch-all for any other unexpected exceptions.
            log.error("Unhandled exception in gateway: ", ex);
        }

        // Build the standardized API response DTO.
        ApiResponse<Object> errorResponse = ApiResponse.builder()
                .statusCode(status.value())
                .status(status)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .trace_identity(getTraceId(exchange))
                .build();

        // Set the response status and headers.
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            // Serialize the ApiResponse DTO and write it to the response body.
            byte[] errorBytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(errorBytes);
            return exchange.getResponse().writeWith(Mono.just(dataBuffer));
        } catch (JsonProcessingException e) {
            log.error("Error while serializing error response to JSON", e);
            // Fallback for when serialization fails.
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