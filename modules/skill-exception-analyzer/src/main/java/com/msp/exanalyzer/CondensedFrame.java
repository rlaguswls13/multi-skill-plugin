package com.msp.exanalyzer;

import java.util.Objects;

/**
 * Sealed hierarchy representing either an included stack trace frame or a collapsed block of omitted internal frames.
 */
public sealed interface CondensedFrame permits CondensedFrame.AppFrame, CondensedFrame.OtherFrame, CondensedFrame.OmittedFrames {

    /**
     * An application frame highlighted by the analyzer.
     */
    record AppFrame(StackTraceElement element) implements CondensedFrame {
        public AppFrame {
            Objects.requireNonNull(element, "element must not be null");
        }
    }

    /**
     * A non-application, non-internal library frame.
     */
    record OtherFrame(StackTraceElement element) implements CondensedFrame {
        public OtherFrame {
            Objects.requireNonNull(element, "element must not be null");
        }
    }

    /**
     * A collapsed sequence of omitted internal JDK or reflection frames.
     */
    record OmittedFrames(int count, String reason) implements CondensedFrame {
        public OmittedFrames {
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
