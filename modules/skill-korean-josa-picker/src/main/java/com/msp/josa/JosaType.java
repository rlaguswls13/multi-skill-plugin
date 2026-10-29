package com.msp.josa;

import java.util.HashMap;
import java.util.Map;

/**
 * Types of Korean postpositions (조사) with corresponding phonological variants.
 */
public enum JosaType {
    EUN_NEUN("은", "는"),
    I_GA("이", "가"),
    EUL_REUL("을", "를"),
    GWA_WA("과", "와"),
    EURO_RO("으로", "로"),
    INA_NA("이나", "나"),
    IRANG_RANG("이랑", "랑"),
    IE_YE("이에요", "예요"),
    A_YA("아", "야");

    private final String withJongseong;
    private final String withoutJongseong;

    private static final Map<String, JosaType> TOKEN_MAP = new HashMap<>();

    static {
        registerToken("은/는", EUN_NEUN);
        registerToken("는/은", EUN_NEUN);
        registerToken("이/가", I_GA);
        registerToken("가/이", I_GA);
        registerToken("을/를", EUL_REUL);
        registerToken("를/을", EUL_REUL);
        registerToken("과/와", GWA_WA);
        registerToken("와/과", GWA_WA);
        registerToken("으로/로", EURO_RO);
        registerToken("로/으로", EURO_RO);
        registerToken("이나/나", INA_NA);
        registerToken("나/이나", INA_NA);
        registerToken("이랑/랑", IRANG_RANG);
        registerToken("랑/이랑", IRANG_RANG);
        registerToken("이에요/예요", IE_YE);
        registerToken("예요/이에요", IE_YE);
        registerToken("아/야", A_YA);
        registerToken("야/아", A_YA);
    }

    private static void registerToken(String token, JosaType type) {
        TOKEN_MAP.put(token, type);
    }

    JosaType(String withJongseong, String withoutJongseong) {
        this.withJongseong = withJongseong;
        this.withoutJongseong = withoutJongseong;
    }

    public String getWithJongseong() {
        return withJongseong;
    }

    public String getWithoutJongseong() {
        return withoutJongseong;
    }

    /**
     * Resolves the proper postposition string based on consonant traits.
     * Special case for EURO_RO: words ending in 'ㄹ' 받침 take '로' instead of '으로'.
     */
    public String resolve(boolean hasJongseong, boolean isRieul) {
        if (this == EURO_RO) {
            // No 받침 or 'ㄹ' 받침 -> "로" (e.g. 서울로, 차로)
            // Other 받침 -> "으로" (e.g. 집으로)
            return (!hasJongseong || isRieul) ? withoutJongseong : withJongseong;
        }
        return hasJongseong ? withJongseong : withoutJongseong;
    }

    /**
     * Finds JosaType from token pattern like "은/는", "이/가", "으로/로".
     */
    public static JosaType fromToken(String token) {
        if (token == null) {
            return null;
        }
        return TOKEN_MAP.get(token.trim());
    }
}
