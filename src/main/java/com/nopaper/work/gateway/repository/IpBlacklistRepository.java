/**
 * @package com.nopaper.work.gateway.repository -> gateway
 * @author saikatbarman
 * @date 2025 02-Aug-2025 2:02:17 am
 * @git 
 */

package com.nopaper.work.gateway.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import com.nopaper.work.gateway.entity.IpBlacklist;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for accessing and managing blacklisted IP addresses in
 * the database.
 */
@Repository
public interface IpBlacklistRepository extends R2dbcRepository<IpBlacklist, Long> {
	Flux<IpBlacklist> findByIpAddress(String ipAddress);

	/**
	 * Checks if an IP address exists in the blacklist.
	 *
	 * @param ipAddress The IP address to check.
	 * @return A Mono emitting true if the IP exists, false otherwise.
	 */
	Mono<Boolean> existsByIpAddress(String ipAddress);
}