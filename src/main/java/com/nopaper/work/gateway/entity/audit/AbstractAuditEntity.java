package com.nopaper.work.gateway.entity.audit;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.relational.core.mapping.Column;

import lombok.Data;

@Data
public abstract class AbstractAuditEntity implements Serializable {
	
	private static final long serialVersionUID = -83482496832686800L;
	@Column("created_by")
	private String createdBy;
	@Column("created_date")
	private Instant createdDate = Instant.now();
	@Column("last_modified_by")
	private String lastModifiedBy;
	@Column("last_modified_date")
	private Instant lastModifiedDate = Instant.now();
}
