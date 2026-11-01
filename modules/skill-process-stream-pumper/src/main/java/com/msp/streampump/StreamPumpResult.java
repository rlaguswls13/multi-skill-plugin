package com.msp.streampump;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Result metrics and status of an asynchronous stream pumping operation.
 *
 * @param bytesPumped total bytes successfully transferred
 * @param duration    elapsed transfer duration
 * @param isSuccess   true if the transfer completed normally without errors
 * @param error       error encountered during pumping, or null if successful
 */
public record StreamPumpResult(long bytesPumped, Duration duration, boolean isSuccess, Throwable error) {

    public StreamPumpResult {
        Objects.requireNonNull(duration, "duration must not be null");
    }

    /**
     * Factory for a successful pumping operation.
     *
     * @param bytesPumped bytes transferred
     * @param duration    time taken
     * @return StreamPumpResult indicating success
     */
    public static StreamPumpResult success(long bytesPumped, Duration duration) {
        return new StreamPumpResult(bytesPumped, duration, true, null);
    }

    /**
     * Factory for a failed pumping operation.
     *
     * @param bytesPumped bytes transferred before failure
     * @param duration    time elapsed before failure
     * @param error       cause of failure
     * @return StreamPumpResult indicating failure
     */
    public static StreamPumpResult failure(long bytesPumped, Duration duration, Throwable error) {
        return new StreamPumpResult(bytesPumped, duration, false, Objects.requireNonNull(error, "error must not be null"));
    }

    /**
     * Returns an Optional wrapping the error if failed, or empty if successful.
     *
     * @return Optional of Throwable
     */
    public Optional<Throwable> errorOptional() {
        return Optional.ofNullable(error);
    }
}
