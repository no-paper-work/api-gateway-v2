package com.nopaper.work.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.nopaper.work.gateway.entity.audit.AbstractAuditEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "request_logs", schema = "gateway")

public class RequestLog extends AbstractAuditEntity {
	private static final long serialVersionUID = 5963906945919181612L;

	@Id
	private Long id;
	
	@Column("request_id")
	private String requestId;
	
	@Column("trace_id")
	private String traceId;
	
	@Column("http_method")
	private String httpMethod;
	
	private String uri;
	
	@Column("status_code")
	private int statusCode;
    
	@Column("client_ip")
	private String clientIp;
	
	@Column("request_headers")
    private String requestHeaders;
	
	@Column("response_headers")
    private String responseHeaders;
	
	@Column("duration_ms")
    private Long durationMs;
    
}
