package com.msp.ssn;

import java.time.LocalDate;
import java.time.Period;

/**
 * Parsed metadata and validation result for a Korean Resident / Foreigner Registration Number.
 */
public record SsnInfo(
        boolean valid,
        String raw,
        String formatted,
        LocalDate birthDate,
        Gender gender,
        boolean foreigner,
        int century,
        int internationalAge,
        int koreanAge,
        String errorMessage
) {
    public static SsnInfo success(
            String raw,
            String formatted,
            LocalDate birthDate,
            Gender gender,
            boolean foreigner,
            int century
    ) {
        LocalDate today = LocalDate.now();
        int internationalAge = Period.between(birthDate, today).getYears();
        int koreanAge = today.getYear() - birthDate.getYear() + 1;

        return new SsnInfo(
                true,
                raw,
                formatted,
                birthDate,
                gender,
                foreigner,
                century,
                internationalAge,
                koreanAge,
                null
        );
    }

    public static SsnInfo failure(String raw, String errorMessage) {
        return new SsnInfo(
                false,
                raw,
                null,
                null,
                null,
                false,
                0,
                0,
                0,
                errorMessage
        );
    }
}
