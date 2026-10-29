package com.msp.jsonpath;

/**
 * Sealed hierarchy for JSONPath navigation tokens.
 */
sealed interface PathSegment permits PathSegment.Property, PathSegment.Index {

    record Property(String name) implements PathSegment {
    }

    record Index(int index) implements PathSegment {
    }
}
