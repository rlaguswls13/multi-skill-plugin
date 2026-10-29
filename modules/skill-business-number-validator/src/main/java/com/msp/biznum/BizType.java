package com.msp.biznum;

/**
 * Korean Business Registration Number (사업자등록번호) classification
 * determined by digits 4 and 5 (index 3 and 4).
 */
public enum BizType {
    INDIVIDUAL_TAXABLE("개인 과세사업자", "Individual Taxable Business"),
    INDIVIDUAL_TAX_EXEMPT("개인 면세사업자", "Individual Tax-Exempt Business"),
    NON_PROFIT("비영리법인", "Non-Profit Corporation"),
    PROFIT_CORP("영리법인", "Commercial Corporation"),
    PUBLIC_INSTITUTION("국가/지자체/공공기관", "Public Institution / Government Body"),
    FOREIGN_CORP("외국법인", "Foreign Corporation"),
    UNKNOWN("알 수 없음", "Unknown Business Type");

    private final String koreanDescription;
    private final String englishDescription;

    BizType(String koreanDescription, String englishDescription) {
        this.koreanDescription = koreanDescription;
        this.englishDescription = englishDescription;
    }

    public String getKoreanDescription() {
        return koreanDescription;
    }

    public String getEnglishDescription() {
        return englishDescription;
    }

    /**
     * Resolves the BizType from the 2-digit code (digits 4-5 of the 10-digit number).
     */
    public static BizType fromCode(String code) {
        if (code == null || code.length() != 2) {
            return UNKNOWN;
        }
        int val;
        try {
            val = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }

        if (val >= 1 && val <= 79) {
            return INDIVIDUAL_TAXABLE;
        } else if (val == 80) {
            return NON_PROFIT;
        } else if (val >= 81 && val <= 87) {
            return PROFIT_CORP;
        } else if (val == 88) {
            return PUBLIC_INSTITUTION;
        } else if (val == 89) {
            return FOREIGN_CORP;
        } else if (val >= 90 && val <= 99) {
            return INDIVIDUAL_TAX_EXEMPT;
        }
        return UNKNOWN;
    }
}
