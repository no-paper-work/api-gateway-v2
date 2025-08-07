/**
 * @package com.nopaper.work.gateway.templates -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 11:03:32 pm
 * @git 
 */
package com.nopaper.work.gateway.dto.templates;

/**
 * 
 */

import lombok.Builder;
import lombok.Data;

// A template that conforms to a hypothetical LTI standard.
@Data
@Builder
public class LtiApiResponse {
    private boolean lti_success;
    private String lti_message;
    private Object lti_payload;
}