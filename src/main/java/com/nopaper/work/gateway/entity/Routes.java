package com.nopaper.work.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.nopaper.work.gateway.entity.audit.AbstractAuditEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "routes", schema = "gateway")

public class Routes extends AbstractAuditEntity {
	
    private static final long serialVersionUID = 3722111234619415632L;
	
    @Id
    private Long id;
    
    @Column("route_id")
    private String routeId;
    
    private String uri;
    private String predicates;
    private String filters;
    
    @Column("rate_limit_enabled")
    private boolean rateLimitEnabled;
    
    @Column("rateLimit_replenish_rate")
    private Integer rateLimitReplenishRate;
    
    @Column("rateLimit_burst_capacity")
    private Integer rateLimitBurstCapacity;
    
    @Column("encryption_key")
    private String encryptionKey;
    
    private boolean enabled;
    
    @Column("key_resolver_name")
    private String keyResolverName;
}