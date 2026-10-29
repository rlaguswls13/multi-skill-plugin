package com.msp.biznum;

/**
 * Unified facade for Korean registration numbers (Business & Corporate).
 */
public final class KoreanRegistrationValidators {

    private KoreanRegistrationValidators() {
    }

    /**
     * Validates a 10-digit Korean business registration number.
     */
    public static boolean isValidBizNumber(String number) {
        return BizNumberValidator.isValid(number);
    }

    /**
     * Validates a 13-digit Korean corporate registration number.
     */
    public static boolean isValidCorpNumber(String number) {
        return CorpNumberValidator.isValid(number);
    }

    /**
     * Detailed validation for business registration number.
     */
    public static BizNumberValidator.Result validateBizNumber(String number) {
        return BizNumberValidator.validate(number);
    }

    /**
     * Detailed validation for corporate registration number.
     */
    public static CorpNumberValidator.Result validateCorpNumber(String number) {
        return CorpNumberValidator.validate(number);
    }
}
