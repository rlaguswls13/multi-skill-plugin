package com.msp.sfv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * RFC 8941 / RFC 9651 Bare Item types: Integer, Decimal, String, Token, Byte Sequence, Boolean.
 */
public sealed interface BareItem permits
        BareItem.SfInteger,
        BareItem.SfDecimal,
        BareItem.SfString,
        BareItem.SfToken,
        BareItem.SfByteSequence,
        BareItem.SfBoolean {

    long MAX_INTEGER = 999_999_999_999_999L;
    long MIN_INTEGER = -999_999_999_999_999L;
    BigDecimal MAX_DECIMAL = new BigDecimal("999999999999.999");
    BigDecimal MIN_DECIMAL = new BigDecimal("-999999999999.999");

    static SfInteger ofInteger(long value) {
        return new SfInteger(value);
    }

    static SfDecimal ofDecimal(BigDecimal value) {
        return new SfDecimal(value);
    }

    static SfDecimal ofDecimal(double value) {
        return new SfDecimal(BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_EVEN).stripTrailingZeros());
    }

    static SfString ofString(String value) {
        return new SfString(value);
    }

    static SfToken ofToken(String value) {
        return new SfToken(value);
    }

    static SfByteSequence ofBytes(byte[] value) {
        return new SfByteSequence(value);
    }

    static SfBoolean ofBoolean(boolean value) {
        return value ? SfBoolean.TRUE : SfBoolean.FALSE;
    }

    /**
     * RFC 8941 Section 3.3.1: Integer (-999,999,999,999,999 to 999,999,999,999,999).
     */
    record SfInteger(long value) implements BareItem {
        public SfInteger {
            if (value < MIN_INTEGER || value > MAX_INTEGER) {
                throw new StructuredFieldException("RFC 8941 integer out of 15-digit range: " + value);
            }
        }
    }

    /**
     * RFC 8941 Section 3.3.2: Decimal (up to 12 integer digits and up to 3 fractional digits).
     */
    record SfDecimal(BigDecimal value) implements BareItem {
        public SfDecimal {
            Objects.requireNonNull(value, "Decimal value must not be null");
            BigDecimal rounded = value.scale() > 3
                    ? value.setScale(3, RoundingMode.HALF_EVEN)
                    : value;
            BigDecimal normalized = rounded.stripTrailingZeros();
            if (normalized.scale() < 1) {
                normalized = normalized.setScale(1, RoundingMode.UNNECESSARY);
            }
            if (normalized.compareTo(MIN_DECIMAL) < 0 || normalized.compareTo(MAX_DECIMAL) > 0) {
                throw new StructuredFieldException("RFC 8941 decimal integer component exceeds 12 digits: " + value);
            }
            value = normalized;
        }
    }

    /**
     * RFC 8941 Section 3.3.3: String (printable ASCII VCHAR or SP: 0x20..0x7E).
     */
    record SfString(String value) implements BareItem {
        public SfString {
            Objects.requireNonNull(value, "String value must not be null");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c > 0x7E) {
                    throw new StructuredFieldException(
                            "RFC 8941 string contains non-printable ASCII char 0x" + Integer.toHexString(c), i);
                }
            }
        }
    }

    /**
     * RFC 8941 Section 3.3.4: Token (starts with ALPHA or '*', followed by tchar, ':', or '/').
     */
    record SfToken(String value) implements BareItem {
        public SfToken {
            Objects.requireNonNull(value, "Token value must not be null");
            if (value.isEmpty()) {
                throw new StructuredFieldException("RFC 8941 token must not be empty");
            }
            char first = value.charAt(0);
            if (!isAlpha(first) && first != '*') {
                throw new StructuredFieldException("RFC 8941 token must start with ALPHA or '*': " + value);
            }
            for (int i = 1; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!isTokenChar(c)) {
                    throw new StructuredFieldException("Invalid char '" + c + "' in RFC 8941 token: " + value, i);
                }
            }
        }

        public static boolean isAlpha(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        }

        public static boolean isTokenChar(char c) {
            if (isAlpha(c) || (c >= '0' && c <= '9')) {
                return true;
            }
            return switch (c) {
                case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~', ':', '/' -> true;
                default -> false;
            };
        }
    }

    /**
     * RFC 8941 Section 3.3.5: Byte Sequence (:base64:).
     */
    record SfByteSequence(byte[] value) implements BareItem {
        public SfByteSequence {
            Objects.requireNonNull(value, "Byte sequence must not be null");
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }

        public String toBase64() {
            return Base64.getEncoder().encodeToString(value);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof SfByteSequence other) && Arrays.equals(this.value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "SfByteSequence[:" + toBase64() + ":]";
        }
    }

    /**
     * RFC 8941 Section 3.3.6: Boolean (?1 or ?0).
     */
    record SfBoolean(boolean value) implements BareItem {
        public static final SfBoolean TRUE = new SfBoolean(true);
        public static final SfBoolean FALSE = new SfBoolean(false);
    }
}
