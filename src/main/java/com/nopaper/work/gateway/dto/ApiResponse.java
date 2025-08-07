/**
 * @package com.nopaper.work.gateway.dto -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 6:31:23 pm
 * @git 
 */
package com.nopaper.work.gateway.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * A standardized, generic class for all API responses.
 * @param <T> The type of the data payload.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from the JSON output
public class ApiResponse<T> {

    @Builder.Default
    private Instant timestamp = Instant.now();
    private int statusCode; // HTTP status code.
    private HttpStatus status; // 
    private String message; // Short human-readable description of the response.
    private T data; // For successful responses, The actual payload (if applicable)
    private String metadata; // Any additional information (like pagination).
    private Map<String, String> errors; // For validation or detailed errors
    private String path; // The request path where the error occurred OR Hyperlinks for navigation (optional).
    private UUID trace_identity; // For internal tracing, we need to add open telemetry.
}
