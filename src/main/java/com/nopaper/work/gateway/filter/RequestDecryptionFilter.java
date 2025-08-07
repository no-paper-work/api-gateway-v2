/**
 * @package com.nopaper.work.gateway.filter -> gateway
 * @author saikatbarman
 * @date 2025 07-Aug-2025 12:27:51 pm
 * @git 
 */
package com.nopaper.work.gateway.filter;

/**
 * 
 */

import com.nopaper.work.gateway.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestDecryptionFilter implements GlobalFilter, Ordered {

    private final CryptoService cryptoService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String encryptionKey = (route != null) ? (String) route.getMetadata().get("encryption_key") : null;

        if (encryptionKey == null) {
            return chain.filter(exchange);
        }

        ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().collectList().flatMapMany(dataBuffers -> {
                    
                    // ✅ This is the crucial fix. Get the buffer factory from the exchange's response.
                    DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                    
                    DataBuffer fullBodyBuffer = bufferFactory.join(dataBuffers);
                    byte[] content = new byte[fullBodyBuffer.readableByteCount()];
                    fullBodyBuffer.read(content);
                    DataBufferUtils.release(fullBodyBuffer);
                    String encryptedBody = new String(content, StandardCharsets.UTF_8);

                    if (!encryptedBody.isEmpty()) {
                        log.debug("Decrypting request body for route: {}", route.getId());
                        String decryptedBody = cryptoService.decrypt(encryptedBody, encryptionKey);
                        byte[] decryptedBytes = decryptedBody.getBytes(StandardCharsets.UTF_8);
                        return Flux.just(bufferFactory.wrap(decryptedBytes));
                    }
                    return Flux.empty();
                });
            }
        };

        return chain.filter(exchange.mutate().request(requestDecorator).build());
    }

    @Override
    public int getOrder() {
        return -10;
    }
}