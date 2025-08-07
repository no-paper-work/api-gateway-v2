/**
 * @package com.nopaper.work.gateway.templates -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 11:02:51 pm
 * @git 
 */
package com.nopaper.work.gateway.dto.templates;

/**
 * 
 */

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

// A more secure template that doesn't reveal internal status codes or stack info.
@Data
@Builder
public class OwaspSecureApiResponse {
    private String message;
    private Object payload; // Renamed from "data"
    private UUID correlationId; // Renamed from "trace_identity"
}
