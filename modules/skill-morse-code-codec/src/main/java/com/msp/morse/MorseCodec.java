package com.msp.morse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-safe bidirectional Morse code encoder and decoder supporting
 * International Morse Code (Latin alphabet, digits, punctuation) and
 * Korean Telegraphic Morse Code (Hangul Jamo with syllable assembly).
 */
public final class MorseCodec {

    public static final String WORD_SEPARATOR = " / ";
    public static final String CHAR_SEPARATOR = " ";

    // International Morse Code tables
    private static final Map<Character, String> CHAR_TO_INTL = new HashMap<>();
    private static final Map<String, Character> INTL_TO_CHAR = new HashMap<>();

    // Korean Morse Code tables
    private static final Map<String, String> JAMO_TO_KOR = new HashMap<>();
    private static final Map<String, String> KOR_TO_JAMO = new HashMap<>();

    static {
        // Latin alphabet
        registerIntl('A', ".-");
        registerIntl('B', "-...");
        registerIntl('C', "-.-.");
        registerIntl('D', "-..");
        registerIntl('E', ".");
        registerIntl('F', "..-.");
        registerIntl('G', "--.");
        registerIntl('H', "....");
        registerIntl('I', "..");
        registerIntl('J', ".---");
        registerIntl('K', "-.-");
        registerIntl('L', ".-..");
        registerIntl('M', "--");
        registerIntl('N', "-.");
        registerIntl('O', "---");
        registerIntl('P', ".--.");
        registerIntl('Q', "--.-");
        registerIntl('R', ".-.");
        registerIntl('S', "...");
        registerIntl('T', "-");
        registerIntl('U', "..-");
        registerIntl('V', "...-");
        registerIntl('W', ".--");
        registerIntl('X', "-..-");
        registerIntl('Y', "-.--");
        registerIntl('Z', "--..");

        // Numbers 0-9
        registerIntl('0', "-----");
        registerIntl('1', ".----");
        registerIntl('2', "..---");
        registerIntl('3', "...--");
        registerIntl('4', "....-");
        registerIntl('5', ".....");
        registerIntl('6', "-....");
        registerIntl('7', "--...");
        registerIntl('8', "---..");
        registerIntl('9', "----.");

        // Punctuation
        registerIntl('.', ".-.-.-");
        registerIntl(',', "--..--");
        registerIntl('?', "..--..");
        registerIntl('\'', ".----.");
        registerIntl('!', "-.-.--");
        registerIntl('/', "-..-.");
        registerIntl('(', "-.--.");
        registerIntl(')', "-.--.-");
        registerIntl('&', ".-...");
        registerIntl(':', "---...");
        registerIntl(';', "-.-.-.");
        registerIntl('=', "-...-");
        registerIntl('+', ".-.-.");
        registerIntl('-', "-....-");
        registerIntl('_', "..--.-");
        registerIntl('"', ".-..-.");
        registerIntl('$', "...-..-");
        registerIntl('@', ".--.-.");

        // Korean Consonants (14)
        registerKor("ㄱ", ".-..");
        registerKor("ㄴ", "..-.");
        registerKor("ㄷ", "-...");
        registerKor("ㄹ", "...-");
        registerKor("ㅁ", "--");
        registerKor("ㅂ", ".--");
        registerKor("ㅅ", "--.");
        registerKor("ㅇ", "-.-");
        registerKor("ㅈ", ".--.");
        registerKor("ㅊ", "-.-.");
        registerKor("ㅋ", ".-.-");
        registerKor("ㅌ", "--..");
        registerKor("ㅍ", "---");
        registerKor("ㅎ", ".---");

        // Korean Vowels (12)
        registerKor("ㅏ", ".");
        registerKor("ㅑ", "..");
        registerKor("ㅓ", "-");
        registerKor("ㅕ", "...");
        registerKor("ㅗ", ".-");
        registerKor("ㅛ", "-.");
        registerKor("ㅜ", "....");
        registerKor("ㅠ", ".-.");
        registerKor("ㅡ", "-..");
        registerKor("ㅣ", "..-");
        registerKor("ㅐ", "--.-");
        registerKor("ㅔ", "-.--");
    }

    private static void registerIntl(char c, String morse) {
        CHAR_TO_INTL.put(c, morse);
        INTL_TO_CHAR.put(morse, c);
    }

    private static void registerKor(String jamo, String morse) {
        JAMO_TO_KOR.put(jamo, morse);
        KOR_TO_JAMO.put(morse, jamo);
    }

    private MorseCodec() {
        // Utility
    }

    /**
     * Encodes arbitrary text (English, Hangul, digits, punctuation) into Morse code.
     * Hangul syllables are automatically decomposed to Jamo and converted to Korean telegraph Morse.
     *
     * @param text Raw source text.
     * @return Morse code string with spaces between letters and " / " between words.
     */
    public static String encode(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                result.append(WORD_SEPARATOR);
            }
            String word = words[w];
            List<String> morseTokens = new ArrayList<>();

            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);

                if (HangulJamoHelper.isHangulSyllable(c)) {
                    List<String> jamos = HangulJamoHelper.decompose(c);
                    for (String jamo : jamos) {
                        String morse = JAMO_TO_KOR.get(jamo);
                        if (morse != null) {
                            morseTokens.add(morse);
                        }
                    }
                } else {
                    String jamoStr = String.valueOf(c);
                    if (JAMO_TO_KOR.containsKey(jamoStr)) {
                        morseTokens.add(JAMO_TO_KOR.get(jamoStr));
                    } else {
                        char upper = Character.toUpperCase(c);
                        String morse = CHAR_TO_INTL.get(upper);
                        if (morse != null) {
                            morseTokens.add(morse);
                        }
                    }
                }
            }

            result.append(String.join(CHAR_SEPARATOR, morseTokens));
        }

        return result.toString();
    }

    /**
     * Decodes Morse code into International Latin alphabet and digits.
     *
     * @param morse Morse code string.
     * @return Decoded plain text in upper case.
     */
    public static String decode(String morse) {
        return decode(morse, MorseMode.INTERNATIONAL);
    }

    /**
     * Decodes Morse code into Korean Hangul syllables.
     *
     * @param morse Morse code string.
     * @return Decoded Korean text with assembled syllables.
     */
    public static String decodeHangul(String morse) {
        return decode(morse, MorseMode.KOREAN);
    }

    /**
     * Decodes Morse code using the specified decoding mode.
     *
     * @param morse Morse code string.
     * @param mode  INTERNATIONAL or KOREAN.
     * @return Decoded plain text.
     */
    public static String decode(String morse, MorseMode mode) {
        if (morse == null || morse.isBlank()) {
            return "";
        }

        // Support both " / " and "   " (3 spaces) as word separators
        String normalized = morse.trim().replace("   ", " / ");
        String[] words = normalized.split("\\s*/\\s*");
        StringBuilder result = new StringBuilder();

        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                result.append(" ");
            }
            String wordMorse = words[w].trim();
            if (wordMorse.isEmpty()) {
                continue;
            }

            String[] tokens = wordMorse.split("\\s+");

            if (mode == MorseMode.KOREAN) {
                List<String> jamoList = new ArrayList<>();
                for (String token : tokens) {
                    if (KOR_TO_JAMO.containsKey(token)) {
                        jamoList.add(KOR_TO_JAMO.get(token));
                    } else if (INTL_TO_CHAR.containsKey(token)) {
                        jamoList.add(String.valueOf(INTL_TO_CHAR.get(token)));
                    }
                }
                result.append(HangulJamoHelper.assemble(jamoList));
            } else {
                for (String token : tokens) {
                    if (INTL_TO_CHAR.containsKey(token)) {
                        result.append(INTL_TO_CHAR.get(token));
                    }
                }
            }
        }

        return result.toString();
    }
}
