/**
 * @package com.nopaper.work.gateway.configurations -> gateway
 * @author saikatbarman
 * @date 2025 07-Aug-2025 2:35:44 pm
 * @git 
 */
package com.nopaper.work.gateway.filter;

/**
 * 
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.transformer.ResponseTransformerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class StandardResponseFilter implements GlobalFilter, Ordered {

    private final ResponseTransformerFactory transformerFactory;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                
                // This is the most critical part from StandardResponseFilter.java
                // It ensures that we only modify successful responses.
                if (!getStatusCode().is2xxSuccessful()) {
                    return super.writeWith(body);
                }

                return DataBufferUtils.join(body)
                    .flatMap(dataBuffer -> {
                        // 1. Buffer the original response body.
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        DataBufferUtils.release(dataBuffer);
                        String originalBody = new String(content, StandardCharsets.UTF_8);

                        if (originalBody.isEmpty()) {
                            return getDelegate().writeWith(Mono.empty());
                        }

                        try {
                            // 2. Parse the body and get the correct dynamic transformer.
                            JsonNode jsonBody = objectMapper.readTree(originalBody);
                            var transformer = transformerFactory.getTransformer(exchange);
                            
                            // 3. Apply the transformation to create the final response object.
                            Object transformedBody = transformer.transform(exchange, jsonBody);
                            byte[] finalResponseBytes = objectMapper.writeValueAsBytes(transformedBody);

                            // 4. Set headers and write the new, transformed body.
                            getDelegate().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            getDelegate().getHeaders().setContentLength(finalResponseBytes.length);
                            return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(finalResponseBytes)));

                        } catch (Exception e) {
                            log.error("Error processing response. Returning original content. Error: {}", e.getMessage());
                            // In case of an error, safely return the original content.
                            return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(content)));
                        }
                    });
            }
        };

        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    @Override
    public int getOrder() {
        // This filter should run late to wrap the final response.
        return -2;
    }
}