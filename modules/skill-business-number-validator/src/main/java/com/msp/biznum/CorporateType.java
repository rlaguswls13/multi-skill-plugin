package com.msp.biznum;

/**
 * Korean Corporate Registration Number (법인등록번호 13자리) corporate type classification
 * determined by digits 5 and 6 (index 4 and 5).
 */
public enum CorporateType {
    STOCK_COMPANY("11", "주식회사", "Stock Corporation / Joint-Stock Company"),
    LIMITED_COMPANY("12", "유한회사", "Limited Liability Company"),
    GENERAL_PARTNERSHIP("13", "합명회사", "General Partnership"),
    LIMITED_PARTNERSHIP("14", "합자회사", "Limited Partnership"),
    LIMITED_LIABILITY_COMPANY("15", "유한책임회사", "Limited Liability Corporation (LLC)"),
    INCORPORATED_ASSOCIATION("21", "사단법인", "Incorporated Association"),
    INCORPORATED_FOUNDATION("22", "재단법인", "Incorporated Foundation"),
    SPECIAL_CORPORATION("23", "특별법인", "Special Legal Corporation"),
    SCHOOL_CORPORATION("31", "학교법인", "School / Educational Corporation"),
    MEDICAL_CORPORATION("32", "의료법인", "Medical Corporation"),
    SOCIAL_WELFARE_CORPORATION("33", "사회복지법인", "Social Welfare Corporation"),
    RELIGIOUS_CORPORATION("34", "종교법인", "Religious Corporation"),
    AGRICULTURAL_COOPERATIVE("41", "영농조합법인", "Agricultural Cooperative"),
    AGRICULTURAL_COMPANY("42", "농업회사법인", "Agricultural Company Corporation"),
    FISHERY_COOPERATIVE("43", "영어조합법인", "Fishery Cooperative Corporation"),
    FOREIGN_CORPORATION("51", "외국법인", "Foreign Corporation"),
    OTHER("99", "기타법인", "Other Corporation");

    private final String code;
    private final String koreanDescription;
    private final String englishDescription;

    CorporateType(String code, String koreanDescription, String englishDescription) {
        this.code = code;
        this.koreanDescription = koreanDescription;
        this.englishDescription = englishDescription;
    }

    public String getCode() {
        return code;
    }

    public String getKoreanDescription() {
        return koreanDescription;
    }

    public String getEnglishDescription() {
        return englishDescription;
    }

    /**
     * Resolves the CorporateType from the 2-digit code (digits 5-6 of the 13-digit number).
     */
    public static CorporateType fromCode(String code) {
        if (code == null || code.length() != 2) {
            return OTHER;
        }
        for (CorporateType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return OTHER;
    }
}
