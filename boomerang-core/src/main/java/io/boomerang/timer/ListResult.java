package io.boomerang.timer;

import java.util.Collection;

/**
 * A result object for paginated task listings.
 *
 * @param <T> the type of items in the list
 * @since 1.0.0
 */
public record ListResult<T>(Collection<T> items, String nextToken) {}
