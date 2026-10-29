package com.msp.health;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Functional interface for executing a health check.
 */
@FunctionalInterface
public interface HealthCheck {

    /**
     * Executes the health check and returns a HealthResult.
     *
     * @return health result
     * @throws Exception if an error occurs during execution
     */
    HealthResult check() throws Exception;

    static HealthCheck of(Supplier<HealthResult> supplier) {
        return supplier::get;
    }

    static HealthCheck ofStatus(Supplier<HealthStatus> supplier) {
        return () -> {
            HealthStatus status = supplier.get();
            return new HealthResult(status != null ? status : HealthStatus.UNKNOWN, "Status: " + status, null, null, null);
        };
    }

    static HealthCheck ofBoolean(BooleanSupplier supplier, String successMsg, String failureMsg) {
        return () -> supplier.getAsBoolean()
                ? HealthResult.up(successMsg)
                : HealthResult.down(failureMsg);
    }
}
