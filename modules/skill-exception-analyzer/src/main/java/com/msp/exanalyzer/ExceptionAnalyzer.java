package com.msp.exanalyzer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * High-performance, zero-dependency exception analyzer for root cause extraction,
 * cyclic-cause safe chain traversal, stack trace condensation, and category detection.
 */
public final class ExceptionAnalyzer {

    private static final Set<String> DEFAULT_INTERNAL_PREFIXES = Set.of(
            "java.lang.reflect.",
            "jdk.internal.reflect.",
            "sun.reflect.",
            "org.junit.",
            "org.gradle.",
            "jdk.internal.misc.",
            "java.base/java.lang.reflect.",
            "java.base/jdk.internal."
    );

    private ExceptionAnalyzer() {
    }

    /**
     * Traverses the cause chain and returns the deepest root cause.
     * Guaranteed to be cycle-safe against circular causes.
     *
     * @param t the root or top-level throwable (nullable)
     * @return the deepest root cause, or the throwable itself if it has no cause, or null if input is null
     */
    public static Throwable getRootCause(Throwable t) {
        if (t == null) return null;
        List<Throwable> chain = getCauseChain(t);
        return chain.isEmpty() ? t : chain.get(chain.size() - 1);
    }

    /**
     * Extracts all causes starting from the input throwable down to the root cause.
     * Cycle-safe: terminates immediately if a cause refers to an ancestor already visited.
     *
     * @param t the top-level throwable (nullable)
     * @return unmodifiable list of throwables in cause order (empty list if input is null)
     */
    public static List<Throwable> getCauseChain(Throwable t) {
        if (t == null) return Collections.emptyList();

        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        Throwable current = t;
        while (current != null && seen.add(current)) {
            chain.add(current);
            current = current.getCause();
        }

        return Collections.unmodifiableList(chain);
    }

    /**
     * Finds the first throwable in the cause chain matching the specified type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> Optional<T> findCause(Throwable t, Class<T> targetType) {
        if (t == null || targetType == null) return Optional.empty();
        for (Throwable cause : getCauseChain(t)) {
            if (targetType.isInstance(cause)) {
                return Optional.of((T) cause);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if any throwable in the cause chain is an instance of the specified type.
     */
    public static boolean matches(Throwable t, Class<? extends Throwable> targetType) {
        return findCause(t, targetType).isPresent();
    }

    /**
     * Detects whether any exception in the cause chain represents a timeout error.
     */
    public static boolean isTimeout(Throwable t) {
        if (t == null) return false;
        for (Throwable cause : getCauseChain(t)) {
            String className = cause.getClass().getName();
            if (className.contains("TimeoutException") || className.contains("SocketTimeoutException")) {
                return true;
            }
            String msg = cause.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("timed out") || lower.contains("timeout") || lower.contains("timed_out")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects whether any exception in the cause chain represents a network/connectivity error.
     */
    public static boolean isNetworkError(Throwable t) {
        if (t == null) return false;
        for (Throwable cause : getCauseChain(t)) {
            if (cause instanceof SocketException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof PortUnreachableException) {
                return true;
            }
            String className = cause.getClass().getName();
            if (className.contains("SocketException")
                    || className.contains("ConnectException")
                    || className.contains("NetworkException")
                    || className.contains("HttpHostConnectException")
                    || className.contains("UnknownHostException")) {
                return true;
            }
            String msg = cause.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("connection refused")
                        || lower.contains("connection reset")
                        || lower.contains("broken pipe")
                        || lower.contains("network unreachable")
                        || lower.contains("unknown host")
                        || lower.contains("no route to host")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects whether any exception in the cause chain represents a security/access error.
     */
    public static boolean isSecurityError(Throwable t) {
        if (t == null) return false;
        for (Throwable cause : getCauseChain(t)) {
            if (cause instanceof SecurityException) {
                return true;
            }
            String className = cause.getClass().getName();
            if (className.contains("AccessDenied")
                    || className.contains("Unauthorized")
                    || className.contains("Authentication")
                    || className.contains("Authorization")
                    || className.contains("Forbidden")
                    || className.contains("BadCredentials")
                    || className.contains("SecurityException")) {
                return true;
            }
            String msg = cause.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("unauthorized")
                        || lower.contains("access denied")
                        || lower.contains("forbidden")
                        || lower.contains("permission denied")
                        || lower.contains("bad credentials")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects whether any exception in the cause chain represents a validation/bad input error.
     */
    public static boolean isValidationError(Throwable t) {
        if (t == null) return false;
        for (Throwable cause : getCauseChain(t)) {
            if (cause instanceof IllegalArgumentException
                    || cause instanceof IllegalStateException
                    || cause instanceof NullPointerException
                    || cause instanceof IndexOutOfBoundsException) {
                return true;
            }
            String className = cause.getClass().getName();
            if (className.contains("Validation")
                    || className.contains("ConstraintViolation")
                    || className.contains("Invalid")
                    || className.contains("BadParameter")
                    || className.contains("MethodArgumentNotValid")) {
                return true;
            }
            String msg = cause.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("validation failed")
                        || lower.contains("must not be null")
                        || lower.contains("must not be empty")
                        || lower.contains("invalid argument")
                        || lower.contains("constraint violation")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Categorizes a throwable into one of the standard high-level exception categories.
     */
    public static ExceptionCategory categorize(Throwable t) {
        if (t == null) return ExceptionCategory.UNKNOWN;
        if (isTimeout(t)) return ExceptionCategory.TIMEOUT;
        if (isNetworkError(t)) return ExceptionCategory.NETWORK;
        if (isSecurityError(t)) return ExceptionCategory.SECURITY;
        if (isValidationError(t)) return ExceptionCategory.VALIDATION;
        return ExceptionCategory.UNKNOWN;
    }

    /**
     * Condenses a throwable's stack trace into structured frames, grouping omitted internal frames.
     *
     * @param t                          the throwable
     * @param applicationPackagePrefixes package prefixes considered application code (e.g. "com.msp")
     */
    public static List<CondensedFrame> condense(Throwable t, Set<String> applicationPackagePrefixes) {
        if (t == null) return Collections.emptyList();

        StackTraceElement[] elements = t.getStackTrace();
        List<CondensedFrame> frames = new ArrayList<>();
        int omittedCount = 0;

        for (StackTraceElement elem : elements) {
            String className = elem.getClassName();
            boolean isInternal = isInternalFrame(className);
            boolean isApp = isApplicationFrame(className, applicationPackagePrefixes);

            if (isInternal && !isApp) {
                omittedCount++;
            } else {
                if (omittedCount > 0) {
                    frames.add(new CondensedFrame.OmittedFrames(omittedCount, "JDK / Reflection"));
                    omittedCount = 0;
                }
                if (isApp) {
                    frames.add(new CondensedFrame.AppFrame(elem));
                } else {
                    frames.add(new CondensedFrame.OtherFrame(elem));
                }
            }
        }

        if (omittedCount > 0) {
            frames.add(new CondensedFrame.OmittedFrames(omittedCount, "JDK / Reflection"));
        }

        return frames;
    }

    /**
     * Formats a condensed, clean, and highlighted stack trace string for logging or inspection.
     */
    public static String formatCondensedStackTrace(Throwable t, Set<String> applicationPackagePrefixes) {
        if (t == null) return "";

        StringBuilder sb = new StringBuilder(512);
        List<Throwable> chain = getCauseChain(t);

        for (int i = 0; i < chain.size(); i++) {
            Throwable cause = chain.get(i);
            if (i == 0) {
                sb.append(cause.getClass().getName());
                if (cause.getMessage() != null) {
                    sb.append(": ").append(cause.getMessage());
                }
                sb.append(System.lineSeparator());
            } else {
                sb.append("Caused by: ").append(cause.getClass().getName());
                if (cause.getMessage() != null) {
                    sb.append(": ").append(cause.getMessage());
                }
                sb.append(System.lineSeparator());
            }

            List<CondensedFrame> condensed = condense(cause, applicationPackagePrefixes);
            for (CondensedFrame frame : condensed) {
                switch (frame) {
                    case CondensedFrame.AppFrame(StackTraceElement elem) ->
                            sb.append("\tat ").append(elem).append(" [APP]").append(System.lineSeparator());
                    case CondensedFrame.OtherFrame(StackTraceElement elem) ->
                            sb.append("\tat ").append(elem).append(System.lineSeparator());
                    case CondensedFrame.OmittedFrames(int count, String reason) ->
                            sb.append("\t... ").append(count).append(" omitted frames (").append(reason).append(")").append(System.lineSeparator());
                }
            }
        }

        return sb.toString().trim();
    }

    private static boolean isInternalFrame(String className) {
        for (String prefix : DEFAULT_INTERNAL_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApplicationFrame(String className, Set<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) return false;
        for (String prefix : prefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
