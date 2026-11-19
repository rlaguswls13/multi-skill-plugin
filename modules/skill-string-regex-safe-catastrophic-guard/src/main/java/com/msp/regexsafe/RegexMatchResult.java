package com.msp.regexsafe;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Result record of a safe regular expression match evaluation.
 */
public record RegexMatchResult(
        boolean isMatched,
        boolean isTimedOut,
        boolean isStepLimitExceeded,
        long stepCount,
        Duration duration,
        String matchedText,
        List<String> groups,
        Throwable failureCause
) {
    public RegexMatchResult {
        groups = groups == null ? List.of() : List.copyOf(groups);
        matchedText = matchedText == null ? "" : matchedText;
    }

    public static RegexMatchResult matched(long steps, Duration duration, String matchedText, List<String> groups) {
        return new RegexMatchResult(true, false, false, steps, duration, matchedText, groups, null);
    }

    public static RegexMatchResult unmatched(long steps, Duration duration) {
        return new RegexMatchResult(false, false, false, steps, duration, "", List.of(), null);
    }

    public static RegexMatchResult timedOut(long steps, Duration duration, Throwable cause) {
        return new RegexMatchResult(false, true, false, steps, duration, "", List.of(), cause);
    }

    public static RegexMatchResult stepLimitExceeded(long steps, Duration duration, Throwable cause) {
        return new RegexMatchResult(false, false, true, steps, duration, "", List.of(), cause);
    }

    public static RegexMatchResult failure(long steps, Duration duration, Throwable cause) {
        return new RegexMatchResult(false, false, false, steps, duration, "", List.of(), cause);
    }

    public Optional<Throwable> optionalFailureCause() {
        return Optional.ofNullable(failureCause);
    }
}
