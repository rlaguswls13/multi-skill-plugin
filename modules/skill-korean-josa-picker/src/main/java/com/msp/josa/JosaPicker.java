package com.msp.josa;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Korean postposition (조사) selector and template formatter based on terminal phonological analysis.
 */
public final class JosaPicker {

    private static final Pattern TEMPLATE_HASH_PATTERN = Pattern.compile("([가-힣a-zA-Z0-9]+)#\\{([^}]+)\\}");
    private static final Pattern TEMPLATE_COLON_PATTERN = Pattern.compile("#\\{([가-힣a-zA-Z0-9]+)\\s*[:|,]\s*([^}]+)\\}");
    private static final Pattern TEMPLATE_PAREN_PATTERN = Pattern.compile("([가-힣a-zA-Z0-9]+)\\(([^)]+)\\)");

    private JosaPicker() {
    }

    /**
     * Selects only the appropriate postposition string (e.g. "은", "는", "으로", "로") for the word.
     *
     * @param word target word
     * @param type postposition type
     * @return selected postposition string
     */
    public static String pick(String word, JosaType type) {
        Objects.requireNonNull(type, "type must not be null");
        KoreanJongseongDetector.SoundTrait trait = KoreanJongseongDetector.detect(word);
        return type.resolve(trait.hasJongseong(), trait.isRieul());
    }

    /**
     * Attaches the appropriate postposition directly to the word (e.g. "철수" -> "철수는").
     *
     * @param word target word
     * @param type postposition type
     * @return word concatenated with chosen postposition
     */
    public static String attach(String word, JosaType type) {
        if (word == null) {
            return pick("", type);
        }
        return word + pick(word, type);
    }

    /**
     * Formats a template string containing postposition placeholders into a natural sentence.
     * <p>
     * Supported syntaxes:
     * <ul>
     *   <li>{@code "철수#{은/는} 사과#{을/를} 먹었다"} -> {@code "철수는 사과를 먹었다"}</li>
     *   <li>{@code "#{철수:은/는} #{사과:을/를} 먹었다"} -> {@code "철수는 사과를 먹었다"}</li>
     *   <li>{@code "철수(은/는) 사과(을/를) 먹었다"} -> {@code "철수는 사과를 먹었다"}</li>
     * </ul>
     *
     * @param template template string with placeholders
     * @return formatted string
     */
    public static String format(String template) {
        if (template == null || template.isBlank()) {
            return template;
        }

        String result = template;

        // 1. "#{word:조사}"
        Matcher colonMatcher = TEMPLATE_COLON_PATTERN.matcher(result);
        if (colonMatcher.find()) {
            StringBuilder sb = new StringBuilder();
            do {
                String word = colonMatcher.group(1);
                String token = colonMatcher.group(2);
                JosaType type = JosaType.fromToken(token);
                if (type != null) {
                    colonMatcher.appendReplacement(sb, Matcher.quoteReplacement(attach(word, type)));
                } else {
                    colonMatcher.appendReplacement(sb, Matcher.quoteReplacement(colonMatcher.group(0)));
                }
            } while (colonMatcher.find());
            colonMatcher.appendTail(sb);
            result = sb.toString();
        }

        // 2. "word#{조사}"
        Matcher hashMatcher = TEMPLATE_HASH_PATTERN.matcher(result);
        if (hashMatcher.find()) {
            StringBuilder sb = new StringBuilder();
            do {
                String word = hashMatcher.group(1);
                String token = hashMatcher.group(2);
                JosaType type = JosaType.fromToken(token);
                if (type != null) {
                    hashMatcher.appendReplacement(sb, Matcher.quoteReplacement(attach(word, type)));
                } else {
                    hashMatcher.appendReplacement(sb, Matcher.quoteReplacement(hashMatcher.group(0)));
                }
            } while (hashMatcher.find());
            hashMatcher.appendTail(sb);
            result = sb.toString();
        }

        // 3. "word(조사)"
        Matcher parenMatcher = TEMPLATE_PAREN_PATTERN.matcher(result);
        if (parenMatcher.find()) {
            StringBuilder sb = new StringBuilder();
            do {
                String word = parenMatcher.group(1);
                String token = parenMatcher.group(2);
                JosaType type = JosaType.fromToken(token);
                if (type != null) {
                    parenMatcher.appendReplacement(sb, Matcher.quoteReplacement(attach(word, type)));
                } else {
                    parenMatcher.appendReplacement(sb, Matcher.quoteReplacement(parenMatcher.group(0)));
                }
            } while (parenMatcher.find());
            parenMatcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }
}
