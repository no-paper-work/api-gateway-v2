/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 11:36:22 pm
 * @git 
 */
package com.nopaper.work.gateway.filter;

/**
 * 
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.crypto.CryptoService;
import com.nopaper.work.gateway.transformer.ResponseTransformerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class DynamicResponseWrapperFilter implements GlobalFilter, Ordered {

    private final ResponseTransformerFactory transformerFactory;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Use a ServerHttpResponseDecorator for full control over the response.
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // Only process successful responses with a streaming body.
                if (getStatusCode().is2xxSuccessful() && body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    return fluxBody.collectList().flatMap(dataBuffers -> {
                        DataBufferFactory bufferFactory = getDelegate().bufferFactory();
                        
                        // 1. Buffer the original response to handle it as a whole.
                        DataBuffer fullBodyBuffer = bufferFactory.join(dataBuffers);
                        byte[] content = new byte[fullBodyBuffer.readableByteCount()];
                        fullBodyBuffer.read(content);
                        DataBufferUtils.release(fullBodyBuffer); // Prevent memory leaks.
                        
                        String originalBody = new String(content, StandardCharsets.UTF_8);

                        // If the original body is empty, don't try to process it.
                        if (originalBody.isEmpty()) {
                            return getDelegate().writeWith(Flux.empty());
                        }

                        try {
                            // 2. Parse the raw string body into a flexible JsonNode.
                            // This correctly handles both JSON objects and arrays.
                            JsonNode jsonBody = objectMapper.readTree(originalBody);

                            // 3. Transform the response using the dynamic template logic.
                            var transformer = transformerFactory.getTransformer(exchange);
                            Object transformedBody = transformer.transform(exchange, jsonBody);
                            String jsonResponse = objectMapper.writeValueAsString(transformedBody);

                            // 4. Check for an encryption key on the matched route.
                            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                            String encryptionKey = (route != null) ? (String) route.getMetadata().get("encryption_key") : null;

                            byte[] finalResponseBytes;
                            // 5. Conditionally encrypt the entire transformed JSON response.
                            if (encryptionKey != null) {
                                log.debug("Encrypting response for route: {}", route.getId());
                                getDelegate().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                                finalResponseBytes = cryptoService.encrypt(jsonResponse, encryptionKey).getBytes(StandardCharsets.UTF_8);
                            } else {
                                getDelegate().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                finalResponseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                            }

                            // 6. Write the new, final response body.
                            getDelegate().getHeaders().setContentLength(finalResponseBytes.length);
                            return getDelegate().writeWith(Mono.just(bufferFactory.wrap(finalResponseBytes)));

                        } catch (Exception e) {
                            log.error("Error processing response body, falling back to original. Error: {}", e.getMessage());
                            // In case of any error, safely return the original response to the client.
                            return getDelegate().writeWith(Flux.fromIterable(dataBuffers));
                        }
                    });
                }
                // If not a successful response, pass it through without modification.
                return super.writeWith(body);
            }
        };

        // Apply the decorator to the filter chain.
        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    @Override
    public int getOrder() {
        // Run before the final response is written but after most other logic.
        return -2;
    }
}