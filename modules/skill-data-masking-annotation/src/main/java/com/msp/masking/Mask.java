package com.msp.masking;

import java.lang.annotation.*;

/**
 * 로깅 및 직렬화 시 마스킹 처리할 필드에 부착하는 어노테이션.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Mask {
    MaskType type() default MaskType.NAME;
    char maskChar() default '*';
    int prefixKeep() default 1;
    int suffixKeep() default 1;
}
