/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 07-Aug-2025 11:22:22 am
 * @git 
 */
package com.nopaper.work.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;

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
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class ResponseWrappingAndEncryptionFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;
    private final ResponseTransformerFactory transformerFactory;
    private final ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFactory;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String encryptionKey = (route != null) ? (String) route.getMetadata().get("encryption_key") : null;

        // Apply response decorator for templating and encryption
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!getStatusCode().is2xxSuccessful()) {
                    return super.writeWith(body);
                }

                // Buffer the entire body to transform it as a single unit
                return DataBufferUtils.join(body).flatMap(dataBuffer -> {
                    byte[] content = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(content);
                    DataBufferUtils.release(dataBuffer);
                    String originalBody = new String(content, StandardCharsets.UTF_8);

                    if (originalBody.isEmpty()) {
                        return getDelegate().writeWith(Mono.empty());
                    }

                    try {
                        JsonNode jsonBody = objectMapper.readTree(originalBody);
                        var transformer = transformerFactory.getTransformer(exchange);
                        Object transformedBody = transformer.transform(exchange, jsonBody);
                        String jsonResponse = objectMapper.writeValueAsString(transformedBody);

                        byte[] finalResponseBytes;
                        if (encryptionKey != null) {
                            log.debug("Encrypting response for route: {}", route.getId());
                            getDelegate().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            finalResponseBytes = cryptoService.encrypt(jsonResponse, encryptionKey).getBytes(StandardCharsets.UTF_8);
                        } else {
                            getDelegate().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            finalResponseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        }
                        
                        getDelegate().getHeaders().setContentLength(finalResponseBytes.length);
                        return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(finalResponseBytes)));
                    } catch (Exception e) {
                        log.error("Error processing response. Returning original body. Error: {}", e.getMessage());
                        return getDelegate().writeWith(Mono.just(dataBuffer));
                    }
                });
            }
        };

        // If encryption is needed, decrypt the request first.
        if (encryptionKey != null) {
            var config = new ModifyRequestBodyGatewayFilterFactory.Config()
                .setInClass(String.class).setOutClass(String.class)
                .setRewriteFunction((ex, body) -> {
                	String encryptedBodyAsString = (String) body;
                    if (body != null) return Mono.just(cryptoService.decrypt(encryptedBodyAsString, encryptionKey));
                    return Mono.empty();
                });
            return modifyRequestBodyFactory.apply(config).filter(exchange.mutate().response(responseDecorator).build(), chain);
        }

        // Otherwise, just apply the response decorator.
        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    @Override
    public int getOrder() {
        return -2; // This filter should run late in the chain to modify the final response.
    }
}