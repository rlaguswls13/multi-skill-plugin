package com.msp.result.validation;

import com.msp.result.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 복수의 유효성 검증 실패({@link ValidationError})를 한 번에 누적하여 반환하는 검증 결과 객체.
 *
 * @param <T> 검증 대상 데이터 타입
 */
public record ValidationResult<T>(
        T target,
        List<ValidationError> errors
) {
    public ValidationResult {
        errors = errors == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static <T> ValidationResult<T> valid(T target) {
        return new ValidationResult<>(target, Collections.emptyList());
    }

    public static <T> ValidationResult<T> invalid(T target, List<ValidationError> errors) {
        return new ValidationResult<>(target, errors);
    }

    public static <T> Builder<T> of(T target) {
        return new Builder<>(target);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean isInvalid() {
        return !isValid();
    }

    /**
     * 검증 통과 시 성공 Result, 실패 시 ValidationError 목록을 담은 실패 Result로 변환합니다.
     */
    public Result<T, List<ValidationError>> toResult() {
        return isValid() ? Result.success(target) : Result.failure(errors);
    }

    public static class Builder<T> {
        private final T target;
        private final List<ValidationError> errors = new ArrayList<>();

        public Builder(T target) {
            this.target = target;
        }

        public Builder<T> check(Predicate<T> condition, String fieldName, Object rejectedValue, String message) {
            Objects.requireNonNull(condition);
            if (!condition.test(target)) {
                errors.add(ValidationError.of(fieldName, rejectedValue, message));
            }
            return this;
        }

        public Builder<T> checkNotEmpty(String str, String fieldName, String message) {
            if (str == null || str.trim().isEmpty()) {
                errors.add(ValidationError.of(fieldName, str, message));
            }
            return this;
        }

        public ValidationResult<T> build() {
            return new ValidationResult<>(target, errors);
        }
    }
}
