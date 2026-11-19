package com.msp.signotifier;

import java.time.Duration;

/**
 * Handle returned upon registering a signal listener, allowing inspection and unregistration.
 */
public record ListenerRegistration(
        String id,
        String name,
        SignalType signalType,
        int priority,
        Duration timeout,
        Runnable unregisterAction
) {
    public boolean unregister() {
        if (unregisterAction != null) {
            unregisterAction.run();
            return true;
        }
        return false;
    }
}
