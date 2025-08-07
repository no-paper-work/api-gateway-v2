/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 07-Aug-2025 1:13:56 pm
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
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class UnifiedGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;
    private final ResponseTransformerFactory transformerFactory;
    private final ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFactory;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String encryptionKey = (route != null) ? (String) route.getMetadata().get("encryption_key") : null;

        // Create the response decorator first
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!getStatusCode().is2xxSuccessful()) {
                    return super.writeWith(body);
                }

                Mono<DataBuffer> modifiedBody = DataBufferUtils.join(body).flatMap(dataBuffer -> {
                    byte[] content = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(content);
                    DataBufferUtils.release(dataBuffer);
                    String originalBody = new String(content, StandardCharsets.UTF_8);

                    if (originalBody.isEmpty()) {
                        return Mono.just(getDelegate().bufferFactory().wrap(new byte[0]));
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
                        return Mono.just(getDelegate().bufferFactory().wrap(finalResponseBytes));
                    } catch (Exception e) {
                        log.error("Error processing response. Returning original body. Error: {}", e.getMessage());
                        return Mono.just(dataBuffer); // Return original buffer on error
                    }
                });

                return super.writeWith(modifiedBody);
            }
        };

        // If encryption is needed, decrypt the request first using the stable factory.
        if (encryptionKey != null) {
            var config = new ModifyRequestBodyGatewayFilterFactory.Config()
                .setInClass(String.class).setOutClass(String.class)
                .setRewriteFunction((ex, body) -> {
                    String encryptedBody = (String) body;
                    if (encryptedBody != null && !encryptedBody.isEmpty()) {
                        return Mono.just(cryptoService.decrypt(encryptedBody, encryptionKey));
                    }
                    return Mono.empty();
                });
            // Apply request decryption THEN response decoration
            return modifyRequestBodyFactory.apply(config).filter(exchange.mutate().response(responseDecorator).build(), chain);
        }

        // If no encryption, just apply the response decorator.
        return chain.filter(exchange.mutate().response(responseDecorator).build());
    }

    @Override
    public int getOrder() {
        // A single filter handling everything should run late to wrap the final response.
        return -2;
    }
}