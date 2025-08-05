package com.nopaper.work.gateway.services;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.nopaper.work.gateway.repository.IpBlacklistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service class to handle security-related operations,
 * such as checking for blacklisted IP addresses.
 * Uses caching to optimize performance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityService {

    private final IpBlacklistRepository ipBlacklistRepository;

    /**
     * Checks if a given IP address is blacklisted.
     * The result of this check is cached in Redis under the "ip_blacklist" cache name.
     * The cache key is the IP address itself.
     *
     * @param ipAddress The IP address to validate.
     * @return A Mono emitting true if the IP is blacklisted, false otherwise.
     */
    @Cacheable(value = "ip_blacklist", key = "#ipAddress")
    public Mono<Boolean> isIpBlacklisted(String ipAddress) {
        log.info("Checking database for IP: {}. (This should not appear often if caching is working)", ipAddress);
        return ipBlacklistRepository.existsByIpAddress(ipAddress);
    }
}
