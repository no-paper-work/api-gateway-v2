package com.nopaper.work.gateway.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import com.nopaper.work.gateway.entity.Routes;

import reactor.core.publisher.Flux;

/**
 * @package com.nopaper.work.gateway.repository -> gateway
 * @author saikatbarman
 * @date 2025 14-Jul-2025 8:37:32 am
 * @git
 */
@Repository
public interface RouteRepository extends R2dbcRepository<Routes, Long> {
	Flux<Routes> findByEnabled(boolean enabled);
}