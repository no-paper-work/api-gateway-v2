/**
 * @package com.nopaper.work.gateway.services -> gateway
 * @author saikatbarman
 * @date 2025 02-Aug-2025 2:52:16 am
 * @git 
 */
package com.nopaper.work.gateway.services;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopaper.work.gateway.entity.RequestLog;
import com.nopaper.work.gateway.repository.RequestLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service dedicated to handling persistent logging of requests.
 * Operations are designed to be asynchronous to avoid blocking gateway threads.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoggingService {

    private final RequestLogRepository requestLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Asynchronously saves a record of the request and its outcome to the database.
     * This operation is performed on a separate, bounded elastic thread pool
     * to ensure it doesn't interfere with the main event loop.
     *
     * @param exchange The completed server web exchange, containing request and response details.
     * @param duration The total processing time for the request in milliseconds.
     * @return A Mono<RequestLog> that completes when the log is saved.
     */
    public Mono<RequestLog> logRequest(ServerWebExchange exchange, long duration) {
        RequestLog logEntry = new RequestLog();

        // Extract details from the exchange
        logEntry.setRequestId((String) exchange.getAttributes().get("requestId"));
        logEntry.setTraceId((String) exchange.getAttributes().get("traceId"));
        logEntry.setHttpMethod(exchange.getRequest().getMethod().name());
        logEntry.setUri(exchange.getRequest().getURI().toString());
        logEntry.setStatusCode(Objects.requireNonNull(exchange.getResponse().getStatusCode()).value());
        logEntry.setClientIp(Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress());
        logEntry.setDurationMs(duration);
        logEntry.setCreatedDate(Instant.now());

        try {
            // Serialize headers to JSON strings for storage
            logEntry.setRequestHeaders(objectMapper.writeValueAsString(exchange.getRequest().getHeaders()));
            logEntry.setResponseHeaders(objectMapper.writeValueAsString(exchange.getResponse().getHeaders()));
        } catch (JsonProcessingException e) {
            log.error("Error serializing headers to JSON for logging", e);
        }

        // Save the log entry to the database on a separate thread pool
        return requestLogRepository.save(logEntry)
                .doOnSuccess(savedLog -> log.debug("Successfully saved request log with ID: {}", savedLog.getId()))
                .doOnError(error -> log.error("Failed to save request log", error))
                .subscribeOn(Schedulers.boundedElastic()); // Ensures DB write doesn't block
    }
}