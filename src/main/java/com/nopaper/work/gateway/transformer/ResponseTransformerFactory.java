/**
 * @package com.nopaper.work.gateway.transformer -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 11:08:39 pm
 * @git 
 */
package com.nopaper.work.gateway.transformer;

/**
 * 
 */

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ResponseTransformerFactory {

    public ResponseTransformer getTransformer(ServerWebExchange exchange) {
        String complianceProfile = Optional.ofNullable(
            exchange.getRequest().getHeaders().getFirst("X-Compliance-Profile")
        ).orElse("DEFAULT");

        return switch (complianceProfile.toUpperCase()) {
            case "OWASP" -> StandardTransformers::owaspTransformer;
            case "LTI" -> StandardTransformers::ltiTransformer;
            default -> StandardTransformers::defaultTransformer;
        };
    }
}