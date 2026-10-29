package com.msp.slowquery;

/**
 * Callback listener invoked when a query execution exceeds the configured slow threshold.
 */
@FunctionalInterface
public interface SlowQueryListener {

    /**
     * Invoked when a query is determined to be slow.
     *
     * @param alert details regarding the slow query event
     */
    void onSlowQuery(SlowQueryAlert alert);
}
