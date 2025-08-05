/**
 * @package com.nopaper.work.gateway.entity -> gateway
 * @author saikatbarman
 * @date 2025 02-Aug-2025 2:15:44 am
 * @git 
 */
package com.nopaper.work.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.nopaper.work.gateway.entity.audit.AbstractAuditEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 
 */

@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "ip_blacklist", schema = "gateway")
public class IpBlacklist extends AbstractAuditEntity {

	private static final long serialVersionUID = 7989064709093876462L;

	@Id
	private Long id;
	
	@Column("ip_address")
	private String ipAddress;
	
	private String reason;

}