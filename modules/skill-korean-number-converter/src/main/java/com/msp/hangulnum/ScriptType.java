package com.msp.hangulnum;

/**
 * Character script type for rendering Korean numeric text.
 */
public enum ScriptType {
    /**
     * Standard modern Korean alphabet (일, 이, 삼, 사, 오...).
     */
    HANGUL,

    /**
     * Standard basic Hanja characters (一, 二, 三, 四, 五...).
     */
    HANJA_STANDARD,

    /**
     * Formal fraud-resistant Hanja typography used in legal/financial documents (壹, 貳, 參, 肆, 伍...).
     */
    HANJA_FORMAL
}
