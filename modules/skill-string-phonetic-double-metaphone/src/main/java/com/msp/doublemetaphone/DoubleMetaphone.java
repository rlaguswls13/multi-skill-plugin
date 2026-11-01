package com.msp.doublemetaphone;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Java 21 implementation of Lawrence Philips' Double Metaphone phonetic encoding algorithm.
 * <p>
 * Generates both primary and alternate secondary phonetic keys, accounting for English,
 * French, German, Italian, Spanish, and Slavic linguistic rules.
 */
public final class DoubleMetaphone {

    private static final String VOWELS = "AEIOUY";
    private static final Set<String> SILENT_START = Set.of("GN", "KN", "PN", "WR", "PS");

    private final int maxCodeLen;

    public DoubleMetaphone() {
        this(4);
    }

    public DoubleMetaphone(int maxCodeLen) {
        if (maxCodeLen < 1) {
            throw new IllegalArgumentException("maxCodeLen must be at least 1");
        }
        this.maxCodeLen = maxCodeLen;
    }

    public static DoubleMetaphoneResult encode(String input) {
        return new DoubleMetaphone(4).encodeWord(input);
    }

    public static DoubleMetaphoneResult encode(String input, int maxCodeLen) {
        return new DoubleMetaphone(maxCodeLen).encodeWord(input);
    }

    public static boolean isSimilar(String str1, String str2) {
        return isSimilar(str1, str2, true);
    }

    public static boolean isSimilar(String str1, String str2, boolean alternate) {
        if (str1 == null || str2 == null) {
            return false;
        }
        DoubleMetaphoneResult r1 = encode(str1);
        DoubleMetaphoneResult r2 = encode(str2);
        return r1.matches(r2, alternate);
    }

    public int getMaxCodeLen() {
        return maxCodeLen;
    }

    public DoubleMetaphoneResult encodeWord(String value) {
        String clean = cleanInput(value);
        if (clean.isEmpty()) {
            return new DoubleMetaphoneResult("", "");
        }

        boolean isSlavic = isSlavic(clean);
        int length = clean.length();
        int current = 0;

        // Skip silent starting combinations: GN, KN, PN, WR, PS
        if (length >= 2 && SILENT_START.contains(clean.substring(0, 2))) {
            current = 1;
        }

        DoubleMetaphoneResultBuilder result = new DoubleMetaphoneResultBuilder(maxCodeLen);

        // Initial 'X' is encoded as 'S'
        if (clean.startsWith("X")) {
            result.append('S');
            current = 1;
        }

        while (!result.isFull()) {
            if (current >= length) {
                break;
            }

            char ch = clean.charAt(current);

            switch (ch) {
                case 'A', 'E', 'I', 'O', 'U', 'Y' -> {
                    if (current == 0) {
                        result.append('A');
                    }
                    current++;
                }
                case 'B' -> {
                    result.append('P');
                    if (charAt(clean, current + 1) == 'B') {
                        current += 2;
                    } else {
                        current++;
                    }
                }
                case 'C' -> {
                    current = handleC(clean, result, current, isSlavic);
                }
                case 'D' -> {
                    current = handleD(clean, result, current);
                }
                case 'F' -> {
                    result.append('F');
                    current = (charAt(clean, current + 1) == 'F') ? current + 2 : current + 1;
                }
                case 'G' -> {
                    current = handleG(clean, result, current, isSlavic);
                }
                case 'H' -> {
                    current = handleH(clean, result, current);
                }
                case 'J' -> {
                    current = handleJ(clean, result, current, isSlavic);
                }
                case 'K' -> {
                    result.append('K');
                    current = (charAt(clean, current + 1) == 'K') ? current + 2 : current + 1;
                }
                case 'L' -> {
                    current = handleL(clean, result, current);
                }
                case 'M' -> {
                    result.append('M');
                    if (stringAt(clean, current - 1, 3, "UMB")
                            && (current + 1 == length - 1 || stringAt(clean, current + 2, 2, "ER"))) {
                        current += 2;
                    } else {
                        current = (charAt(clean, current + 1) == 'M') ? current + 2 : current + 1;
                    }
                }
                case 'N' -> {
                    result.append('N');
                    current = (charAt(clean, current + 1) == 'N') ? current + 2 : current + 1;
                }
                case 'P' -> {
                    current = handleP(clean, result, current);
                }
                case 'Q' -> {
                    result.append('K');
                    current = (charAt(clean, current + 1) == 'Q') ? current + 2 : current + 1;
                }
                case 'R' -> {
                    current = handleR(clean, result, current);
                }
                case 'S' -> {
                    current = handleS(clean, result, current, isSlavic);
                }
                case 'T' -> {
                    current = handleT(clean, result, current);
                }
                case 'V' -> {
                    result.append('F');
                    current = (charAt(clean, current + 1) == 'V') ? current + 2 : current + 1;
                }
                case 'W' -> {
                    current = handleW(clean, result, current);
                }
                case 'X' -> {
                    current = handleX(clean, result, current);
                }
                case 'Z' -> {
                    current = handleZ(clean, result, current, isSlavic);
                }
                default -> current++;
            }
        }

        return result.build();
    }

    private int handleC(String val, DoubleMetaphoneResultBuilder result, int current, boolean isSlavic) {
        if (current > 1 && !isVowel(charAt(val, current - 2)) && stringAt(val, current - 1, 3, "ACH")
                && (charAt(val, current + 2) != 'I' && (charAt(val, current + 2) != 'E'
                || stringAt(val, current - 2, 6, "BACHER", "MACHER")))) {
            result.append('K');
            return current + 2;
        }

        // Special case: 'CAESAR'
        if (current == 0 && stringAt(val, current, 6, "CAESAR")) {
            result.append('S');
            return current + 2;
        }

        // Italian 'CHIA'
        if (stringAt(val, current, 4, "CHIA")) {
            result.append('K');
            return current + 2;
        }

        if (stringAt(val, current, 2, "CH")) {
            // Find 'MICHAEL'
            if (current > 0 && stringAt(val, current, 4, "CHAE")) {
                result.append('K', 'X');
                return current + 2;
            }

            // Greek roots: chemistry, chorus, etc.
            if (current == 0 && (stringAt(val, current + 1, 5, "HARAC", "HARIS")
                    || stringAt(val, current + 1, 3, "HOR", "HYM", "HIA", "HEM"))
                    && !stringAt(val, 0, 5, "CHORE")) {
                result.append('K');
                return current + 2;
            }

            // Germanic, Greek, or Slavic
            if ((stringAt(val, 0, 4, "VAN ", "VON ") || stringAt(val, 0, 3, "SCH"))
                    || stringAt(val, current - 2, 6, "ORCHES", "ARCHIT", "ORCHID")
                    || stringAt(val, current + 2, 1, "T", "S")
                    || ((stringAt(val, current - 1, 1, "A", "O", "U", "E") || current == 0)
                    && stringAt(val, current + 2, 1, "L", "R", "N", "M", "B", "H", "F", "V", "W", " "))) {
                result.append('K');
            } else if (current > 0) {
                if (stringAt(val, 0, 2, "MC")) {
                    result.append('K');
                } else {
                    result.append('X', 'K');
                }
            } else {
                result.append('X');
            }
            return current + 2;
        }

        // 'CZ'
        if (stringAt(val, current, 2, "CZ") && !stringAt(val, current - 2, 4, "WICZ")) {
            result.append('S', 'X');
            return current + 2;
        }

        // 'CIA'
        if (stringAt(val, current + 1, 3, "CIA")) {
            result.append('X');
            return current + 3;
        }

        // Double 'CC'
        if (stringAt(val, current, 2, "CC") && !(current == 1 && charAt(val, 0) == 'M')) {
            if (stringAt(val, current + 2, 1, "I", "E", "H") && !stringAt(val, current + 2, 2, "HU")) {
                if ((current == 1 && charAt(val, current - 1) == 'A')
                        || stringAt(val, current - 1, 5, "UCCEE", "UCCES")) {
                    result.append("KS");
                } else {
                    result.append('X');
                }
                return current + 3;
            } else {
                result.append('K');
                return current + 2;
            }
        }

        if (stringAt(val, current, 2, "CK", "CG", "CQ")) {
            result.append('K');
            return current + 2;
        }

        if (stringAt(val, current, 2, "CI", "CE", "CY")) {
            if (stringAt(val, current, 3, "CIO", "CIE", "CIA")) {
                result.append('S', 'X');
            } else {
                result.append('S');
            }
            return current + 2;
        }

        result.append('K');

        if (stringAt(val, current + 1, 2, " C", " Q", " G")) {
            return current + 3;
        } else if (stringAt(val, current + 1, 1, "C", "K", "Q") && !stringAt(val, current + 1, 2, "CE", "CI")) {
            return current + 2;
        }
        return current + 1;
    }

    private int handleD(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (stringAt(val, current, 2, "DG")) {
            if (stringAt(val, current + 2, 1, "I", "E", "Y")) {
                result.append('J');
                return current + 3;
            } else {
                result.append("TK");
                return current + 2;
            }
        }
        if (stringAt(val, current, 2, "DT", "DD")) {
            result.append('T');
            return current + 2;
        }
        result.append('T');
        return current + 1;
    }

    private int handleG(String val, DoubleMetaphoneResultBuilder result, int current, boolean isSlavic) {
        if (charAt(val, current + 1) == 'H') {
            if (current > 0 && !isVowel(charAt(val, current - 1))) {
                result.append('K');
                return current + 2;
            }
            if (current == 0) {
                if (charAt(val, current + 2) == 'I') {
                    result.append('J');
                } else {
                    result.append('K');
                }
                return current + 2;
            }
            if ((current > 1 && stringAt(val, current - 2, 1, "B", "H", "D"))
                    || (current > 2 && stringAt(val, current - 3, 1, "B", "H", "D"))
                    || (current > 3 && stringAt(val, current - 4, 1, "B", "H"))) {
                return current + 2;
            }
            if (current > 2 && charAt(val, current - 1) == 'U' && stringAt(val, current - 3, 1, "C", "G", "L", "R", "T")) {
                result.append('F');
            } else if (current > 0 && charAt(val, current - 1) != 'I') {
                result.append('K');
            }
            return current + 2;
        }

        if (charAt(val, current + 1) == 'N') {
            if (current == 1 && isVowel(charAt(val, 0)) && !isSlavic) {
                result.append("KN", "N");
            } else if (!stringAt(val, current + 2, 2, "EY") && charAt(val, current + 1) != 'Y' && !isSlavic) {
                result.append("N", "KN");
            } else {
                result.append("KN");
            }
            return current + 2;
        }

        if (stringAt(val, current + 1, 2, "LI") && !isSlavic) {
            result.append("KL", "L");
            return current + 2;
        }

        if (current == 0 && (charAt(val, current + 1) == 'Y' || stringAt(val, current + 1, 2, "ES", "EP", "EB", "EL", "EY", "IB", "IL", "IN", "IE", "EI", "ER"))) {
            result.append('K', 'J');
            return current + 2;
        }

        if ((stringAt(val, current + 1, 2, "ER") || charAt(val, current + 1) == 'Y')
                && !stringAt(val, 0, 6, "DANGER", "RANGER", "MANGER")
                && !stringAt(val, current - 1, 1, "E", "I")
                && !stringAt(val, current - 1, 3, "RGY", "OGY")) {
            result.append('K', 'J');
            return current + 2;
        }

        if (stringAt(val, current + 1, 1, "E", "I", "Y") || stringAt(val, current - 1, 4, "AGGI", "OGGI")) {
            if (stringAt(val, 0, 4, "VAN ", "VON ") || stringAt(val, 0, 3, "SCH") || stringAt(val, current + 1, 2, "ET")) {
                result.append('K');
            } else if (stringAt(val, current + 1, 3, "IER")) {
                result.append('J');
            } else {
                result.append('J', 'K');
            }
            return current + 2;
        }

        result.append('K');
        return (charAt(val, current + 1) == 'G') ? current + 2 : current + 1;
    }

    private int handleH(String val, DoubleMetaphoneResultBuilder result, int current) {
        if ((current == 0 || isVowel(charAt(val, current - 1))) && isVowel(charAt(val, current + 1))) {
            result.append('H');
            return current + 2;
        }
        return current + 1;
    }

    private int handleJ(String val, DoubleMetaphoneResultBuilder result, int current, boolean isSlavic) {
        if (stringAt(val, current, 4, "JOSE") || stringAt(val, 0, 4, "SAN ")) {
            if ((current == 0 && charAt(val, current + 4) == ' ') || stringAt(val, 0, 4, "SAN ")) {
                result.append('H');
            } else {
                result.append('J', 'H');
            }
            return current + 1;
        }

        if (current == 0 && !stringAt(val, current, 4, "JOSE")) {
            result.append('J', 'A');
        } else if (isVowel(charAt(val, current - 1)) && !isSlavic && (charAt(val, current + 1) == 'A' || charAt(val, current + 1) == 'O')) {
            result.append('J', 'H');
        } else if (current == val.length() - 1) {
            result.append('J', ' ');
        } else if (!stringAt(val, current + 1, 1, "L", "T", "K", "S", "N", "M", "B", "Z")
                && !stringAt(val, current - 1, 1, "S", "K", "L")) {
            result.append('J');
        }

        return (charAt(val, current + 1) == 'J') ? current + 2 : current + 1;
    }

    private int handleL(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (charAt(val, current + 1) == 'L') {
            if (conditionSpanishLL(val, current)) {
                result.appendPrimary('L');
            } else {
                result.append('L');
            }
            return current + 2;
        }
        result.append('L');
        return current + 1;
    }

    private boolean conditionSpanishLL(String val, int current) {
        if (current == val.length() - 3 && stringAt(val, current - 1, 4, "ILLO", "ILLA", "ALLE")) {
            return true;
        }
        if ((stringAt(val, val.length() - 2, 2, "AS", "OS") || stringAt(val, val.length() - 1, 1, "A", "O"))
                && stringAt(val, current - 1, 4, "ALLE")) {
            return true;
        }
        return false;
    }

    private int handleP(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (charAt(val, current + 1) == 'H') {
            result.append('F');
            return current + 2;
        }
        result.append('P');
        return (stringAt(val, current + 1, 1, "P", "B")) ? current + 2 : current + 1;
    }

    private int handleR(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (current == val.length() - 1 && !isSlavic(val)
                && stringAt(val, current - 2, 2, "IE") && !stringAt(val, current - 4, 2, "ME", "MA")) {
            result.appendSecondary('R');
        } else {
            result.append('R');
        }
        return (charAt(val, current + 1) == 'R') ? current + 2 : current + 1;
    }

    private int handleS(String val, DoubleMetaphoneResultBuilder result, int current, boolean isSlavic) {
        // Special case: 'ISLAND', 'ISLE'
        if (stringAt(val, current - 1, 3, "ISL", "YSL")) {
            return current + 1;
        }

        // Special case: 'SUGAR'
        if (current == 0 && stringAt(val, current, 5, "SUGAR")) {
            result.append('X', 'S');
            return current + 1;
        }

        if (stringAt(val, current, 2, "SH")) {
            if (stringAt(val, current + 1, 4, "HEIM", "HOEK", "HOLM", "HOLZ")) {
                result.append('S');
            } else {
                result.append('X');
            }
            return current + 2;
        }

        // Italian / Slavic: 'SIO', 'SIA'
        if (stringAt(val, current, 3, "SIO", "SIA") || stringAt(val, current, 4, "SIAN")) {
            if (!isSlavic) {
                result.append('S', 'X');
            } else {
                result.append('S');
            }
            return current + 3;
        }

        // German 'SCH'
        if ((current == 0 && stringAt(val, current + 1, 1, "M", "N", "L", "W")) || stringAt(val, current + 1, 1, "Z")) {
            result.append('S', 'X');
            return (stringAt(val, current + 1, 1, "Z")) ? current + 2 : current + 1;
        }

        if (stringAt(val, current, 2, "SC")) {
            if (charAt(val, current + 2) == 'H') {
                if (stringAt(val, current + 3, 2, "OO", "ER", "EN", "UY", "ED", "EM")) {
                    if (stringAt(val, current + 3, 2, "ER", "EN")) {
                        result.append("X", "SK");
                    } else {
                        result.append("SK");
                    }
                } else {
                    if (current == 0 && !isVowel(charAt(val, 3)) && charAt(val, 3) != 'W') {
                        result.append('X', 'S');
                    } else {
                        result.append('X');
                    }
                }
                return current + 3;
            }

            if (stringAt(val, current + 2, 1, "I", "E", "Y")) {
                result.append('S');
                return current + 3;
            }

            result.append("SK");
            return current + 3;
        }

        if (current == val.length() - 1 && stringAt(val, current - 2, 2, "AI", "OI")) {
            result.appendSecondary('S');
        } else {
            result.append('S');
        }

        return (stringAt(val, current + 1, 1, "S", "Z")) ? current + 2 : current + 1;
    }

    private int handleT(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (stringAt(val, current, 4, "TION")) {
            result.append('X');
            return current + 3;
        }

        if (stringAt(val, current, 3, "TIA", "TCH")) {
            result.append('X');
            return current + 3;
        }

        if (stringAt(val, current, 2, "TH") || stringAt(val, current, 3, "TTH")) {
            if (stringAt(val, current + 2, 2, "OM", "AM") || stringAt(val, 0, 4, "VAN ", "VON ") || stringAt(val, 0, 3, "SCH")) {
                result.append('T');
            } else {
                result.append('0', 'T');
            }
            return current + 2;
        }

        if (stringAt(val, current + 1, 1, "T", "D")) {
            current += 2;
        } else {
            // Check silent 'T' in 'STLE' (e.g. 'WRESTLE', 'CASTLE')
            if (current > 0 && charAt(val, current - 1) == 'S' && stringAt(val, current + 1, 2, "LE")) {
                return current + 1;
            }
            current++;
        }
        result.append('T');
        return current;
    }

    private int handleW(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (stringAt(val, current, 2, "WR")) {
            result.append('R');
            return current + 2;
        }

        if (current == 0 && (isVowel(charAt(val, current + 1)) || stringAt(val, current, 2, "WH"))) {
            if (isVowel(charAt(val, current + 1))) {
                result.append('A', 'F');
            } else {
                result.append('A');
            }
            return current + 1;
        }

        if ((current == val.length() - 1 && isVowel(charAt(val, current - 1)))
                || stringAt(val, current - 1, 5, "EWSKI", "EWSKY", "OWSKI", "OWSKY")
                || stringAt(val, 0, 3, "SCH")) {
            result.appendSecondary('F');
            return current + 1;
        }

        if (stringAt(val, current, 4, "WICZ", "WITZ")) {
            result.append("TS", "FX");
            return current + 4;
        }

        return current + 1;
    }

    private int handleX(String val, DoubleMetaphoneResultBuilder result, int current) {
        if (current == 0) {
            result.append('S');
            return current + 1;
        }

        if (!(current == val.length() - 1 && (stringAt(val, current - 3, 3, "IAU", "EAU") || stringAt(val, current - 2, 2, "AU", "OU")))) {
            result.append("KS");
        }

        return (stringAt(val, current + 1, 1, "C", "X")) ? current + 2 : current + 1;
    }

    private int handleZ(String val, DoubleMetaphoneResultBuilder result, int current, boolean isSlavic) {
        if (charAt(val, current + 1) == 'H') {
            result.append('J');
            return current + 2;
        } else if (stringAt(val, current + 1, 2, "ZO", "ZI", "ZA") || (isSlavic && (current > 0 && charAt(val, current - 1) != 'T'))) {
            result.append('S', "TS");
        } else {
            result.append('S');
        }
        return (charAt(val, current + 1) == 'Z') ? current + 2 : current + 1;
    }

    private static String cleanInput(String input) {
        if (input == null) {
            return "";
        }
        String upper = input.trim().toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isVowel(char ch) {
        return VOWELS.indexOf(ch) != -1;
    }

    private static boolean isSlavic(String value) {
        return value.contains("W") || value.contains("K") || value.contains("CZ") || value.contains("WITZ");
    }

    private static char charAt(String val, int index) {
        if (index < 0 || index >= val.length()) {
            return '\0';
        }
        return val.charAt(index);
    }

    private static boolean stringAt(String val, int start, int length, String... targets) {
        if (start < 0 || start + length > val.length()) {
            return false;
        }
        String sub = val.substring(start, start + length);
        for (String target : targets) {
            if (sub.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static final class DoubleMetaphoneResultBuilder {
        private final StringBuilder primary = new StringBuilder();
        private final StringBuilder secondary = new StringBuilder();
        private final int maxLength;

        DoubleMetaphoneResultBuilder(int maxLength) {
            this.maxLength = maxLength;
        }

        void append(char ch) {
            appendPrimary(ch);
            appendSecondary(ch);
        }

        void append(String str) {
            appendPrimary(str);
            appendSecondary(str);
        }

        void append(char prim, char sec) {
            appendPrimary(prim);
            appendSecondary(sec);
        }

        void append(char prim, String sec) {
            appendPrimary(prim);
            appendSecondary(sec);
        }

        void append(String prim, char sec) {
            appendPrimary(prim);
            appendSecondary(sec);
        }

        void append(String prim, String sec) {
            appendPrimary(prim);
            appendSecondary(sec);
        }

        void appendPrimary(char ch) {
            if (ch != ' ' && primary.length() < maxLength) {
                primary.append(ch);
            }
        }

        void appendPrimary(String str) {
            for (int i = 0; i < str.length() && primary.length() < maxLength; i++) {
                char ch = str.charAt(i);
                if (ch != ' ') {
                    primary.append(ch);
                }
            }
        }

        void appendSecondary(char ch) {
            if (ch != ' ' && secondary.length() < maxLength) {
                secondary.append(ch);
            }
        }

        void appendSecondary(String str) {
            for (int i = 0; i < str.length() && secondary.length() < maxLength; i++) {
                char ch = str.charAt(i);
                if (ch != ' ') {
                    secondary.append(ch);
                }
            }
        }

        boolean isFull() {
            return primary.length() >= maxLength && secondary.length() >= maxLength;
        }

        DoubleMetaphoneResult build() {
            return new DoubleMetaphoneResult(primary.toString(), secondary.toString());
        }
    }
}
