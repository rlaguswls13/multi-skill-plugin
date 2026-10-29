package com.msp.interpolate;

import java.util.Collections;
import java.util.Map;

/**
 * Thread-safe string template interpolator supporting:
 * <ul>
 *     <li>Named placeholders: {@code ${var}}</li>
 *     <li>Nested dot-notation properties: {@code ${user.address.city}} on Maps and Java records</li>
 *     <li>Positional indexed placeholders: {@code {0}}, {@code {1}}</li>
 *     <li>Backslash escaping: {@code \${var}} &rarr; {@code ${var}}, {@code \{0\}} &rarr; {@code {0}}</li>
 *     <li>Customizable missing variable strategies: {@link MissingStrategy#PRESERVE}, {@link MissingStrategy#EMPTY}, {@link MissingStrategy#THROW}</li>
 * </ul>
 */
public final class StringInterpolator {

    private StringInterpolator() {
        // Utility
    }

    /**
     * Interpolates named variables in a template with default options (${var}, nested dot support, preserve missing).
     *
     * @param template Template string.
     * @param context  Map of variable names to values or objects.
     * @return Interpolated string.
     */
    public static String interpolate(String template, Map<String, ?> context) {
        return interpolate(template, context, InterpolationOptions.DEFAULT);
    }

    /**
     * Interpolates named variables in a template with customized options.
     *
     * @param template Template string.
     * @param context  Map of variable names to values or objects.
     * @param options  Formatting and strategy options.
     * @return Interpolated string.
     */
    public static String interpolate(String template, Map<String, ?> context, InterpolationOptions options) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        InterpolationOptions opts = (options != null) ? options : InterpolationOptions.DEFAULT;
        Map<String, ?> ctx = (context != null) ? context : Collections.emptyMap();

        String prefix = opts.prefix();
        String suffix = opts.suffix();

        int n = template.length();
        StringBuilder out = new StringBuilder(n + 32);
        int i = 0;

        while (i < n) {
            if (template.charAt(i) == '\\') {
                int slashCount = 0;
                while (i < n && template.charAt(i) == '\\') {
                    slashCount++;
                    i++;
                }

                if (i < n && (template.startsWith(prefix, i) || template.startsWith(suffix, i))) {
                    out.append("\\".repeat(slashCount / 2));
                    if (slashCount % 2 != 0) {
                        String matched = template.startsWith(prefix, i) ? prefix : suffix;
                        out.append(matched);
                        i += matched.length();
                        continue;
                    }
                    // Even slashes: prefix is not escaped, proceed to variable resolution
                } else {
                    out.append("\\".repeat(slashCount));
                    continue;
                }
            }

            if (template.startsWith(prefix, i)) {
                int startPos = i + prefix.length();
                int endPos = template.indexOf(suffix, startPos);
                if (endPos != -1) {
                    String varName = template.substring(startPos, endPos).trim();
                    Object val = PropertyResolver.resolve(ctx, varName, opts.enableNestedProperties());

                    if (val != null) {
                        out.append(val);
                    } else {
                        switch (opts.missingStrategy()) {
                            case PRESERVE -> out.append(prefix).append(varName).append(suffix);
                            case EMPTY -> {}
                            case THROW -> throw new MissingVariableException(varName);
                        }
                    }
                    i = endPos + suffix.length();
                } else {
                    out.append(template.charAt(i));
                    i++;
                }
            } else {
                out.append(template.charAt(i));
                i++;
            }
        }

        return out.toString();
    }

    /**
     * Interpolates positional indexed placeholders ({0}, {1}, ...) in a template.
     *
     * @param template Template string with {0}, {1} placeholders.
     * @param args     Positional arguments.
     * @return Interpolated string.
     */
    public static String interpolateIndexed(String template, Object... args) {
        return interpolateIndexed(template, MissingStrategy.PRESERVE, args);
    }

    /**
     * Interpolates positional indexed placeholders ({0}, {1}, ...) with a custom missing strategy.
     *
     * @param template Template string.
     * @param strategy Strategy when an index is out of bounds or null.
     * @param args     Positional arguments.
     * @return Interpolated string.
     */
    public static String interpolateIndexed(String template, MissingStrategy strategy, Object... args) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        MissingStrategy strat = (strategy != null) ? strategy : MissingStrategy.PRESERVE;
        Object[] arguments = (args != null) ? args : new Object[0];

        int n = template.length();
        StringBuilder out = new StringBuilder(n + 32);
        int i = 0;

        while (i < n) {
            if (template.charAt(i) == '\\') {
                int slashCount = 0;
                while (i < n && template.charAt(i) == '\\') {
                    slashCount++;
                    i++;
                }

                if (i < n && (template.charAt(i) == '{' || template.charAt(i) == '}')) {
                    out.append("\\".repeat(slashCount / 2));
                    if (slashCount % 2 != 0) {
                        out.append(template.charAt(i));
                        i++;
                        continue;
                    }
                } else {
                    out.append("\\".repeat(slashCount));
                    continue;
                }
            }

            if (template.charAt(i) == '{') {
                int endPos = template.indexOf('}', i + 1);
                if (endPos != -1) {
                    String indexStr = template.substring(i + 1, endPos).trim();
                    try {
                        int index = Integer.parseInt(indexStr);
                        if (index >= 0 && index < arguments.length && arguments[index] != null) {
                            out.append(arguments[index]);
                        } else {
                            switch (strat) {
                                case PRESERVE -> out.append('{').append(indexStr).append('}');
                                case EMPTY -> {}
                                case THROW -> throw new MissingVariableException(indexStr);
                            }
                        }
                        i = endPos + 1;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // Not an integer inside {}, treat as literal
                    }
                }
            }

            out.append(template.charAt(i));
            i++;
        }

        return out.toString();
    }
}
