/**
 * @package com.nopaper.work.gateway.transformer -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 11:05:17 pm
 * @git 
 */
package com.nopaper.work.gateway.transformer;

/**
 * 
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.nopaper.work.gateway.dto.ApiResponse;
import com.nopaper.work.gateway.dto.templates.LtiApiResponse;
import com.nopaper.work.gateway.dto.templates.OwaspSecureApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import java.util.UUID;

@Component
public class StandardTransformers {

    // Default transformer using the existing ApiResponse
    public static Object defaultTransformer(ServerWebExchange exchange, JsonNode body) {
        return ApiResponse.builder()
                .statusCode(exchange.getResponse().getStatusCode().value())
                .status(HttpStatus.valueOf(exchange.getResponse().getStatusCode().value()))
                .message("Request processed successfully")
                .data(body)
                .trace_identity(getTraceId(exchange))
                .build();
    }

    // OWASP-compliant transformer
    public static Object owaspTransformer(ServerWebExchange exchange, JsonNode body) {
        return OwaspSecureApiResponse.builder()
                .message("Success")
                .payload(body)
                .correlationId(getTraceId(exchange))
                .build();
    }
    
    // LTI-compliant transformer
    public static Object ltiTransformer(ServerWebExchange exchange, JsonNode body) {
        return LtiApiResponse.builder()
                .lti_success(true)
                .lti_message("Completed")
                .lti_payload(body)
                .build();
    }

    private static UUID getTraceId(ServerWebExchange exchange) {
        Object traceIdAttr = exchange.getAttributes().get("requestId");
        if (traceIdAttr instanceof String) {
            try {
                return UUID.fromString((String) traceIdAttr);
            } catch (Exception e) { /* ignore */ }
        }
        return null;
    }
}