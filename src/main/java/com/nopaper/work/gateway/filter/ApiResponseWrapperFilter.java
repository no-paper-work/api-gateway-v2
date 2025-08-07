/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 8:45:43 pm
 * @git 
 */
package com.nopaper.work.gateway.filter;

/**
 * 
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// @Component
@Slf4j
@RequiredArgsConstructor
public class ApiResponseWrapperFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (getStatusCode().is2xxSuccessful() && body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return fluxBody.collectList().flatMap(dataBuffers -> {
                        // Combine buffers and read the original body
                        DataBuffer fullBodyBuffer = bufferFactory.join(dataBuffers);
                        byte[] content = new byte[fullBodyBuffer.readableByteCount()];
                        fullBodyBuffer.read(content);
                        DataBufferUtils.release(fullBodyBuffer);
                        String originalBody = new String(content, StandardCharsets.UTF_8);

                        // Safely get the traceId and status
                        UUID traceId = getTraceId(exchange);
                        HttpStatus responseStatus = getHttpStatus(getStatusCode());

                        // Build the standard success response
                        ApiResponse<Object> apiResponse = ApiResponse.builder()
                                .statusCode(responseStatus.value())
                                .status(responseStatus) // ✅ Correctly uses HttpStatus
                                .message("Request processed successfully")
                                .data(parseBody(originalBody))
                                .trace_identity(traceId)
                                .build();
                        
                        // Serialize the new response
                        byte[] newResponseBytes = serializeResponse(apiResponse, traceId);

                        // Update headers and write the new response
                        getHeaders().clear();
                        getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        getHeaders().setContentLength(newResponseBytes.length);
                        
                        // ✅ Always return the correct type
                        return getDelegate().writeWith(Mono.just(bufferFactory.wrap(newResponseBytes)));
                    }).then();
                }
                return super.writeWith(body);
            }
        };
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private byte[] serializeResponse(ApiResponse<?> apiResponse, UUID traceId) {
        try {
            return objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            log.error("Trace ID: {}. Error serializing standard API response", traceId, e);
            ApiResponse<Object> errorResponse = ApiResponse.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Error creating response object")
                    .trace_identity(traceId)
                    .build();
            try {
                return objectMapper.writeValueAsBytes(errorResponse);
            } catch (JsonProcessingException ex) {
                // Fallback to a plain string if even the error response fails to serialize
                return "{\"status\":500,\"message\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
            }
        }
    }

    private HttpStatus getHttpStatus(HttpStatusCode statusCode) {
        if (statusCode instanceof HttpStatus) {
            return (HttpStatus) statusCode;
        }
        // Fallback for non-enum HttpStatusCode
        return HttpStatus.valueOf(statusCode.value());
    }
    
    // Helper methods getTraceId and parseBody remain the same as before...

    private UUID getTraceId(ServerWebExchange exchange) {
        Object traceIdAttr = exchange.getAttributes().get("requestId");
        if (traceIdAttr instanceof String) {
            try {
                return UUID.fromString((String) traceIdAttr);
            } catch (IllegalArgumentException e) {
                log.warn("Could not parse requestId to UUID: {}", traceIdAttr);
            }
        }
        return null;
    }

    private Object parseBody(String body) {
        if (body == null || body.isEmpty()) {
            return null; // Return null for empty bodies
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (JsonProcessingException e) {
            return body;
        }
    }

    @Override
    public int getOrder() {
        // Run after routing but before the final write
        return -2;
    }
}