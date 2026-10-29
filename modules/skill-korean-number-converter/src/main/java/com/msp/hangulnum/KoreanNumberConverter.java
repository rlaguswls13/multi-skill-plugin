package com.msp.hangulnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe Korean spoken words and formal receipt typography converter for numbers.
 * Supports Sino-Korean numbers, classical and formal financial Hanja, receipt fraud prevention typography,
 * and pure Korean count modifiers (하나, 둘, 셋 / 한 개, 두 개...).
 */
public final class KoreanNumberConverter {

    // --- Sino-Korean Hangul ---
    private static final String[] HANGUL_DIGITS = {"영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] HANGUL_SUB_UNITS = {"", "십", "백", "천"};
    private static final String[] HANGUL_BLOCK_UNITS = {"", "만", "억", "조", "경", "해"};

    // --- Hanja Standard ---
    private static final String[] HANJA_STD_DIGITS = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] HANJA_STD_SUB_UNITS = {"", "十", "百", "千"};
    private static final String[] HANJA_STD_BLOCK_UNITS = {"", "萬", "億", "兆", "京", "垓"};

    // --- Hanja Formal (금융/영수증 대문자) ---
    private static final String[] HANJA_FORMAL_DIGITS = {"零", "壹", "貳", "參", "肆", "伍", "陸", "漆", "捌", "玖"};
    private static final String[] HANJA_FORMAL_SUB_UNITS = {"", "拾", "佰", "阡"};
    private static final String[] HANJA_FORMAL_BLOCK_UNITS = {"", "萬", "億", "兆", "京", "垓"};

    // --- Pure Korean (고유어 수사) ---
    private static final String[] PURE_TENS = {"", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"};
    private static final String[] PURE_ONES = {"", "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉"};

    private KoreanNumberConverter() {
        // Utility
    }

    /**
     * Converts a number to standard spoken Hangul using default options.
     * E.g., 12345 -> "일만 이천삼백사십오"
     *
     * @param number Long value to format.
     * @return Korean words string.
     */
    public static String toHangul(long number) {
        return toHangul(number, NumberFormatOptions.DEFAULT);
    }

    /**
     * Converts a monetary amount to standard Korean formal receipt string.
     * E.g., 12345000 -> "금 일천이백삼십사만 오천 원정"
     *
     * @param amount Monetary amount.
     * @return Formal receipt typography string.
     */
    public static String toCurrency(long amount) {
        return toHangul(amount, NumberFormatOptions.CURRENCY);
    }

    /**
     * Converts a number to standard basic Hanja (一, 二, 三...).
     *
     * @param number Long value to format.
     * @return Standard Hanja numeric string.
     */
    public static String toHanja(long number) {
        return toHangul(number, NumberFormatOptions.builder().scriptType(ScriptType.HANJA_STANDARD).build());
    }

    /**
     * Converts a number to formal fraud-resistant financial Hanja (壹, 貳, 參, 萬...).
     *
     * @param number Long value to format.
     * @return Formal Hanja numeric string.
     */
    public static String toHanjaFormal(long number) {
        return toHangul(number, NumberFormatOptions.HANJA_RECEIPT);
    }

    /**
     * Converts a number with customized typography and script options.
     *
     * @param number  Long value to format.
     * @param options Formatting configuration.
     * @return Formatted Korean or Hanja numeric string.
     */
    public static String toHangul(long number, NumberFormatOptions options) {
        NumberFormatOptions opts = (options != null) ? options : NumberFormatOptions.DEFAULT;

        if (number == 0) {
            String zeroChar = switch (opts.scriptType()) {
                case HANGUL -> HANGUL_DIGITS[0];
                case HANJA_STANDARD -> HANJA_STD_DIGITS[0];
                case HANJA_FORMAL -> HANJA_FORMAL_DIGITS[0];
            };
            return opts.prefix() + zeroChar + opts.suffix();
        }

        String minusPrefix = "";
        if (number < 0) {
            minusPrefix = (opts.scriptType() == ScriptType.HANGUL) ? "마이너스 " : "負 ";
            number = -number;
        }

        String[] digits = switch (opts.scriptType()) {
            case HANGUL -> HANGUL_DIGITS;
            case HANJA_STANDARD -> HANJA_STD_DIGITS;
            case HANJA_FORMAL -> HANJA_FORMAL_DIGITS;
        };
        String[] subUnits = switch (opts.scriptType()) {
            case HANGUL -> HANGUL_SUB_UNITS;
            case HANJA_STANDARD -> HANJA_STD_SUB_UNITS;
            case HANJA_FORMAL -> HANJA_FORMAL_SUB_UNITS;
        };
        String[] blockUnits = switch (opts.scriptType()) {
            case HANGUL -> HANGUL_BLOCK_UNITS;
            case HANJA_STANDARD -> HANJA_STD_BLOCK_UNITS;
            case HANJA_FORMAL -> HANJA_FORMAL_BLOCK_UNITS;
        };

        // Split into 4-digit blocks
        List<Long> blocks = new ArrayList<>();
        long temp = number;
        while (temp > 0) {
            blocks.add(temp % 10000L);
            temp /= 10000L;
        }

        List<String> formattedBlocks = new ArrayList<>();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            long blockVal = blocks.get(i);
            if (blockVal == 0) {
                continue;
            }

            String blockText = formatFourDigits((int) blockVal, digits, subUnits, opts.receiptMode());
            String blockUnit = (i < blockUnits.length) ? blockUnits[i] : "";

            formattedBlocks.add(blockText + blockUnit);
        }

        String delimiter = opts.spaceEveryTenThousand() ? " " : "";
        String body = String.join(delimiter, formattedBlocks);

        return opts.prefix() + minusPrefix + body + opts.suffix();
    }

    private static String formatFourDigits(int n, String[] digits, String[] subUnits, boolean receiptMode) {
        StringBuilder sb = new StringBuilder();
        int[] posVal = {n / 1000, (n % 1000) / 100, (n % 100) / 10, n % 10};

        for (int i = 0; i < 4; i++) {
            int val = posVal[i];
            int unitIdx = 3 - i; // 3: 천, 2: 백, 1: 십, 0: 일

            if (val == 0) {
                continue;
            }

            if (val == 1) {
                if (unitIdx == 0) {
                    sb.append(digits[1]);
                } else {
                    if (receiptMode) {
                        sb.append(digits[1]).append(subUnits[unitIdx]);
                    } else {
                        sb.append(subUnits[unitIdx]);
                    }
                }
            } else {
                sb.append(digits[val]).append(subUnits[unitIdx]);
            }
        }
        return sb.toString();
    }

    /**
     * Converts an integer (1 to 99) to native pure Korean counting words.
     * E.g., 1 -> "하나", 20 -> "스물", 23 -> "스물셋".
     *
     * @param number Number between 1 and 99.
     * @return Pure Korean counting word.
     */
    public static String toPureKoreanCount(int number) {
        return toPureKoreanCount(number, null);
    }

    /**
     * Converts an integer (1 to 99) to native pure Korean counter with a unit noun.
     * Applies modifier inflection (수관형사: 한 개, 두 개, 세 개, 네 개, 스무 개).
     * E.g., toPureKoreanCount(1, "개") -> "한 개", toPureKoreanCount(20, "명") -> "스무 명".
     *
     * @param number Number between 1 and 99.
     * @param unit   Unit noun (e.g. "개", "명", "살", "병"). If null, returns pure cardinal noun.
     * @return Inflected counting phrase.
     */
    public static String toPureKoreanCount(int number, String unit) {
        if (number < 1 || number > 99) {
            throw new IllegalArgumentException("Pure Korean counting supports numbers 1 through 99, given: " + number);
        }

        int tens = number / 10;
        int ones = number % 10;

        StringBuilder sb = new StringBuilder();

        if (unit != null && !unit.isEmpty()) {
            if (number == 20) {
                sb.append("스무");
            } else {
                if (tens > 0) {
                    sb.append(PURE_TENS[tens]);
                }
                if (ones > 0) {
                    String modOne = switch (ones) {
                        case 1 -> "한";
                        case 2 -> "두";
                        case 3 -> "세";
                        case 4 -> "네";
                        default -> PURE_ONES[ones];
                    };
                    sb.append(modOne);
                }
            }
            sb.append(" ").append(unit);
        } else {
            if (tens > 0) {
                sb.append(PURE_TENS[tens]);
            }
            if (ones > 0) {
                sb.append(PURE_ONES[ones]);
            }
        }

        return sb.toString();
    }
}
