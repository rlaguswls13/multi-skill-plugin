package com.msp.problemdetails;

import java.util.Objects;

/**
 * Common extension element representing a field or parameter validation failure
 * according to RFC 9457 Appendix A / RFC 7807 conventions.
 */
public record InvalidParam(String name, String reason) {

    public InvalidParam {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
