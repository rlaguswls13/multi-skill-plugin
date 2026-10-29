package com.msp.singleflight;

/**
 * Result of a single-flight execution.
 *
 * @param value  the computed or retrieved value
 * @param shared true if this caller shared the in-flight execution of another caller;
 *               false if this caller was the leader that executed the callable
 * @param <V>    value type
 */
public record SingleFlightResult<V>(V value, boolean shared) {
}
