package com.msp.regexsafe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReDoS-safe compiled regular expression pattern wrapper with step counting and time bounds.
 */
public class SafePattern {

    private final Pattern pattern;
    private final SafeRegexOptions options;
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    SafePattern(Pattern pattern, SafeRegexOptions options) {
        this.pattern = Objects.requireNonNull(pattern, "pattern cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
    }

    public Pattern pattern() {
        return pattern;
    }

    public SafeRegexOptions options() {
        return options;
    }

    public RegexMatchResult matches(CharSequence input) {
        return executeMatch(input, true);
    }

    public RegexMatchResult find(CharSequence input) {
        return executeMatch(input, false);
    }

    private RegexMatchResult executeMatch(CharSequence input, boolean fullMatch) {
        Objects.requireNonNull(input, "input cannot be null");
        long startNanos = System.nanoTime();
        StepCountingCharSequence counted = new StepCountingCharSequence(input, options.maxSteps(), options.timeout());

        Future<RegexMatchResult> future = EXECUTOR.submit(() -> {
            Matcher matcher = pattern.matcher(counted);
            boolean matched = fullMatch ? matcher.matches() : matcher.find();
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            long steps = counted.getStepCount();

            if (matched) {
                String text = matcher.group();
                List<String> groups = new ArrayList<>();
                for (int i = 0; i <= matcher.groupCount(); i++) {
                    groups.add(matcher.group(i));
                }
                return RegexMatchResult.matched(steps, duration, text, groups);
            } else {
                return RegexMatchResult.unmatched(steps, duration);
            }
        });

        try {
            return future.get(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            RegexMatchResult res = RegexMatchResult.timedOut(counted.getStepCount(), duration, te);
            if (options.throwOnFailure()) {
                throw new RegexTimeoutException("Regex evaluation exceeded timeout: " + options.timeout(), duration);
            }
            return res;
        } catch (ExecutionException ee) {
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            Throwable cause = ee.getCause();
            if (cause instanceof CatastrophicBacktrackingException cbe) {
                if (options.throwOnFailure()) {
                    throw cbe;
                }
                return RegexMatchResult.stepLimitExceeded(cbe.getSteps(), duration, cbe);
            } else if (cause instanceof RegexTimeoutException rte) {
                if (options.throwOnFailure()) {
                    throw rte;
                }
                return RegexMatchResult.timedOut(counted.getStepCount(), duration, rte);
            } else {
                if (options.throwOnFailure()) {
                    throw new RuntimeException("Regex execution failed", cause);
                }
                return RegexMatchResult.failure(counted.getStepCount(), duration, cause);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            return RegexMatchResult.failure(counted.getStepCount(), duration, ie);
        }
    }
}
