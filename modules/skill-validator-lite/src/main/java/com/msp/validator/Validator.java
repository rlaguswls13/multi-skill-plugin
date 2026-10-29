package com.msp.validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Fluent parameter and DTO validator supporting chaining, custom predicates, and comprehensive built-in checks.
 *
 * @param <T> the type of object to validate
 */
public final class Validator<T> {

    private final List<Rule<T>> rules;

    private Validator(List<Rule<T>> rules) {
        this.rules = List.copyOf(rules);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Validates the given target object against all registered rules.
     *
     * @param target the object to validate
     * @return {@link ValidationResult} with success status and any detected errors
     */
    public ValidationResult validate(T target) {
        if (target == null) {
            return ValidationResult.of(List.of(ValidationError.of("target", "Target object to validate must not be null")));
        }
        List<ValidationError> errors = new ArrayList<>();
        for (Rule<T> rule : rules) {
            rule.validate(target).ifPresent(errors::add);
        }
        return ValidationResult.of(errors);
    }

    /**
     * Validates the given target and throws {@link ValidationException} if any errors exist.
     *
     * @param target the object to validate
     */
    public void validateAndThrow(T target) {
        validate(target).throwIfInvalid();
    }

    /**
     * Builder for constructing immutable {@link Validator} instances.
     *
     * @param <T> target object type
     */
    public static final class Builder<T> {
        private final List<Rule<T>> rules = new ArrayList<>();

        public Builder<T> rule(Rule<T> rule) {
            Objects.requireNonNull(rule, "rule must not be null");
            rules.add(rule);
            return this;
        }

        public Builder<T> check(Predicate<T> predicate, String errorMessage) {
            Objects.requireNonNull(predicate, "predicate must not be null");
            return rule(target -> predicate.test(target)
                    ? Optional.empty()
                    : Optional.of(ValidationError.of("general", errorMessage, target)));
        }

        public <R> Builder<T> field(String fieldName, Function<T, R> getter, Predicate<R> predicate, String errorMessage) {
            Objects.requireNonNull(fieldName, "fieldName must not be null");
            Objects.requireNonNull(getter, "getter must not be null");
            Objects.requireNonNull(predicate, "predicate must not be null");
            rules.add(target -> {
                R val = getter.apply(target);
                if (!predicate.test(val)) {
                    return Optional.of(ValidationError.of(fieldName, errorMessage, val));
                }
                return Optional.empty();
            });
            return this;
        }

        public Builder<T> notNull(String fieldName, Function<T, ?> getter, String errorMessage) {
            return field(fieldName, getter, Objects::nonNull, errorMessage);
        }

        public Builder<T> notBlank(String fieldName, Function<T, ? extends CharSequence> getter, String errorMessage) {
            return field(fieldName, getter, Validators::isNotBlank, errorMessage);
        }

        public Builder<T> notEmpty(String fieldName, Function<T, ? extends Collection<?>> getter, String errorMessage) {
            return field(fieldName, getter, Validators::isNotEmpty, errorMessage);
        }

        public Builder<T> email(String fieldName, Function<T, String> getter, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.isEmail(val), errorMessage);
        }

        public Builder<T> url(String fieldName, Function<T, String> getter, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.isUrl(val), errorMessage);
        }

        public Builder<T> ipv4(String fieldName, Function<T, String> getter, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.isIpv4(val), errorMessage);
        }

        public Builder<T> ipv6(String fieldName, Function<T, String> getter, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.isIpv6(val), errorMessage);
        }

        public Builder<T> pattern(String fieldName, Function<T, String> getter, Pattern pattern, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.matches(val, pattern), errorMessage);
        }

        public Builder<T> pattern(String fieldName, Function<T, String> getter, String regex, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.matches(val, regex), errorMessage);
        }

        public Builder<T> length(String fieldName, Function<T, ? extends CharSequence> getter, int min, int max, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.hasLength(val, min, max), errorMessage);
        }

        public <N extends Comparable<N>> Builder<T> range(String fieldName, Function<T, N> getter, N min, N max, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.inRange(val, min, max), errorMessage);
        }

        public <N extends Comparable<N>> Builder<T> min(String fieldName, Function<T, N> getter, N min, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.inRange(val, min, null), errorMessage);
        }

        public <N extends Comparable<N>> Builder<T> max(String fieldName, Function<T, N> getter, N max, String errorMessage) {
            return field(fieldName, getter, val -> val != null && Validators.inRange(val, null, max), errorMessage);
        }

        public Validator<T> build() {
            return new Validator<>(rules);
        }
    }
}
