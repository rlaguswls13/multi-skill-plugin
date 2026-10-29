package com.msp.ssn;

/**
 * Gender classification based on the 7th digit of the Korean Resident Registration Number.
 */
public enum Gender {
    MALE("남성", "Male"),
    FEMALE("여성", "Female");

    private final String koreanDescription;
    private final String englishDescription;

    Gender(String koreanDescription, String englishDescription) {
        this.koreanDescription = koreanDescription;
        this.englishDescription = englishDescription;
    }

    public String getKoreanDescription() {
        return koreanDescription;
    }

    public String getEnglishDescription() {
        return englishDescription;
    }
}
