/**
 * @package com.nopaper.work.gateway.transformer -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 11:04:51 pm
 * @git 
 */
package com.nopaper.work.gateway.transformer;

/**
 * 
 */

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ServerWebExchange;

// A functional interface for any response transformation logic.
@FunctionalInterface
public interface ResponseTransformer {
    Object transform(ServerWebExchange exchange, JsonNode originalBody);
}