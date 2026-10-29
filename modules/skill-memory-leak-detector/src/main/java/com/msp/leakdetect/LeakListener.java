package com.msp.leakdetect;

/**
 * Listener for receiving memory leak detection warnings.
 */
@FunctionalInterface
public interface LeakListener {

    /**
     * Invoked when a memory leak condition is detected on a monitored target.
     *
     * @param warning leak warning event
     */
    void onLeakWarning(LeakWarning warning);
}
