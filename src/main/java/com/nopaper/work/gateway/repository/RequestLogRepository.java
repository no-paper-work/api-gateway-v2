/**
 * @package com.nopaper.work.gateway.repository -> gateway
 * @author saikatbarman
 * @date 2025 02-Aug-2025 2:02:17 am
 * @git 
 */
package com.nopaper.work.gateway.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import com.nopaper.work.gateway.entity.RequestLog;

import reactor.core.publisher.Flux;

/**
 * Reactive repository for persisting request logs to the database for audit and analytics.
 */

@Repository
public interface RequestLogRepository extends R2dbcRepository<RequestLog, Long> {
	// R2dbcRepository provides the necessary save() method. No additional methods needed for now.
	Flux<RequestLog> findByTraceId(String traceId);
}