package com.msp.flow;

/**
 * Execution edge mode for debouncing high-frequency actions.
 */
public enum DebounceMode {
    /**
     * Standard trailing edge: defers execution until a quiet period (delay)
     * has elapsed without any new invocations. Only the latest action executes.
     */
    TRAILING,

    /**
     * Leading edge: executes the action immediately upon the initial trigger,
     * then ignores subsequent calls until a quiet period (delay) elapses with no new calls.
     */
    LEADING
}
