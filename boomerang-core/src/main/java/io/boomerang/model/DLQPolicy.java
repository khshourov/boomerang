package io.boomerang.model;

/**
 * Custom data class for dead-letter queue (DLQ) policies.
 *
 * @param destination the target identifier for tasks that exceed max retries
 * @since 1.0.0
 */
public record DLQPolicy(String destination) {}
