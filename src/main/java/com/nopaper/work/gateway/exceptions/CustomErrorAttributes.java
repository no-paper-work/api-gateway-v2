/**
 * @package com.nopaper.work.gateway.errors -> gateway
 * @author saikatbarman
 * @date 2025 08-Aug-2025 11:31:18 am
 * @git 
 */
package com.nopaper.work.gateway.exceptions;

/**
 * 
 */

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Primary // Adding @Primary to ensure it's the preferred bean
public class CustomErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        // Start with the default attributes provided by Spring
        Map<String, Object> errorAttributes = super.getErrorAttributes(request, options);

        // Extract the status code.
        int status = (int) errorAttributes.get("status");
        
        // ✅ This is the crucial fix.
        // If the error is a 404, we override the default message.
        if (status == HttpStatus.NOT_FOUND.value()) {
            errorAttributes.put("message", "The requested resource was not found.");
        } else {
            // For any other error, use the message from the exception if available.
            errorAttributes.put("message", errorAttributes.get("error"));
        }
        
        // Create a new map to control the order and content
        Map<String, Object> customAttributes = new LinkedHashMap<>();
        
        // Populate our standard error response structure
        customAttributes.put("timestamp", Instant.now().toString());
        customAttributes.put("status", errorAttributes.get("status"));
        customAttributes.put("error", errorAttributes.get("error"));
        // Use a more generic message if the default is too specific
        customAttributes.put("message", errorAttributes.get("message")); 
        customAttributes.put("path", errorAttributes.get("path"));

        return customAttributes;
    }
}