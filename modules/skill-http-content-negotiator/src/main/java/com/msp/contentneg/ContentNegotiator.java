package com.msp.contentneg;

import java.util.*;

/**
 * RFC 7231 HTTP Content Negotiation engine for Accept, Accept-Language,
 * and Accept-Encoding headers with quality-factor (q-value) and specificity weighting.
 */
public final class ContentNegotiator {

    private ContentNegotiator() {}

    /**
     * Parses an HTTP header containing quality-weighted items (e.g. Accept, Accept-Language, Accept-Encoding).
     *
     * @param header The raw header value
     * @return List of parsed QualityValue entries
     */
    public static List<QualityValue<String>> parseQualityHeader(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }

        List<QualityValue<String>> list = new ArrayList<>();
        String[] parts = header.split(",");

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] tokens = trimmed.split(";");
            String primaryValue = tokens[0].trim();
            if (primaryValue.isEmpty()) {
                continue;
            }

            double quality = 1.0;
            Map<String, String> parameters = new LinkedHashMap<>();

            for (int i = 1; i < tokens.length; i++) {
                String param = tokens[i].trim();
                int eq = param.indexOf('=');
                if (eq > 0) {
                    String key = param.substring(0, eq).trim();
                    String val = param.substring(eq + 1).trim();
                    // Strip quotes if any
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }

                    if (key.equalsIgnoreCase("q")) {
                        try {
                            quality = Double.parseDouble(val);
                            // Clamp between 0.0 and 1.0
                            quality = Math.max(0.0, Math.min(1.0, quality));
                        } catch (NumberFormatException ignored) {
                            quality = 1.0;
                        }
                    } else {
                        parameters.put(key.toLowerCase(Locale.ROOT), val);
                    }
                }
            }

            list.add(new QualityValue<>(primaryValue, quality, parameters));
        }

        return list;
    }

    /**
     * Negotiates the best matching MIME Content-Type based on the HTTP Accept header.
     *
     * @param acceptHeader   Raw Accept header value (e.g. "text/html, application/json;q=0.9, *\/*;q=0.8")
     * @param supportedTypes Ordered list of supported MIME types
     * @return Best matching supported type, or {@code null} if no supported type is acceptable
     */
    public static String negotiateContentType(String acceptHeader, List<String> supportedTypes) {
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            return null;
        }
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return supportedTypes.getFirst();
        }

        List<QualityValue<String>> parsed = parseQualityHeader(acceptHeader);
        if (parsed.isEmpty()) {
            return supportedTypes.getFirst();
        }

        // Sort by quality descending, then specificity descending, then parameters count descending
        List<QualityValue<String>> sorted = new ArrayList<>(parsed);
        sorted.sort((a, b) -> {
            int qCmp = Double.compare(b.quality(), a.quality());
            if (qCmp != 0) return qCmp;

            int specA = mimeSpecificity(a.value());
            int specB = mimeSpecificity(b.value());
            int specCmp = Integer.compare(specB, specA);
            if (specCmp != 0) return specCmp;

            return Integer.compare(b.parameters().size(), a.parameters().size());
        });

        for (QualityValue<String> qv : sorted) {
            if (qv.quality() <= 0.0) {
                continue;
            }

            String acceptedMime = qv.value().toLowerCase(Locale.ROOT);

            if (acceptedMime.equals("*/*")) {
                return supportedTypes.getFirst();
            } else if (acceptedMime.endsWith("/*")) {
                String prefix = acceptedMime.substring(0, acceptedMime.indexOf('/') + 1);
                for (String supported : supportedTypes) {
                    if (supported.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        return supported;
                    }
                }
            } else {
                for (String supported : supportedTypes) {
                    if (supported.equalsIgnoreCase(acceptedMime)) {
                        return supported;
                    }
                }
            }
        }

        return null;
    }

    private static int mimeSpecificity(String mime) {
        if (mime == null || mime.isBlank() || mime.equals("*/*")) {
            return 0;
        }
        if (mime.endsWith("/*")) {
            return 1;
        }
        return 2;
    }

    /**
     * Negotiates the best matching language based on the HTTP Accept-Language header.
     *
     * @param acceptLanguageHeader Raw Accept-Language header value (e.g. "ko-KR, ko;q=0.9, en-US;q=0.8, en;q=0.7")
     * @param supportedLanguages   Ordered list of supported language tags
     * @return Best matching supported language, or {@code null} if no supported language is acceptable
     */
    public static String negotiateLanguage(String acceptLanguageHeader, List<String> supportedLanguages) {
        if (supportedLanguages == null || supportedLanguages.isEmpty()) {
            return null;
        }
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return supportedLanguages.getFirst();
        }

        List<QualityValue<String>> parsed = parseQualityHeader(acceptLanguageHeader);
        if (parsed.isEmpty()) {
            return supportedLanguages.getFirst();
        }

        List<QualityValue<String>> sorted = new ArrayList<>(parsed);
        sorted.sort(QualityValue::compareTo);

        for (QualityValue<String> qv : sorted) {
            if (qv.quality() <= 0.0) {
                continue;
            }

            String lang = qv.value().toLowerCase(Locale.ROOT);

            if (lang.equals("*")) {
                return supportedLanguages.getFirst();
            }

            // 1. Exact match
            for (String supported : supportedLanguages) {
                if (supported.equalsIgnoreCase(lang)) {
                    return supported;
                }
            }

            // 2. Subtag fallback (e.g., requested "en-US", server supports "en")
            if (lang.contains("-")) {
                String primaryTag = lang.substring(0, lang.indexOf('-'));
                for (String supported : supportedLanguages) {
                    if (supported.equalsIgnoreCase(primaryTag)) {
                        return supported;
                    }
                }
            }

            // 3. Prefix match (e.g., requested "en", server supports "en-US")
            for (String supported : supportedLanguages) {
                String supportedLower = supported.toLowerCase(Locale.ROOT);
                if (supportedLower.startsWith(lang + "-")) {
                    return supported;
                }
            }
        }

        return null;
    }

    /**
     * Negotiates the best matching content encoding based on the HTTP Accept-Encoding header.
     *
     * @param acceptEncodingHeader Raw Accept-Encoding header value (e.g. "gzip, deflate, br;q=0.8")
     * @param supportedEncodings   Ordered list of supported encoding identifiers
     * @return Best matching supported encoding, or {@code null} if no supported encoding is acceptable
     */
    public static String negotiateEncoding(String acceptEncodingHeader, List<String> supportedEncodings) {
        if (supportedEncodings == null || supportedEncodings.isEmpty()) {
            return null;
        }
        if (acceptEncodingHeader == null || acceptEncodingHeader.isBlank()) {
            return supportedEncodings.getFirst();
        }

        List<QualityValue<String>> parsed = parseQualityHeader(acceptEncodingHeader);
        if (parsed.isEmpty()) {
            return supportedEncodings.getFirst();
        }

        List<QualityValue<String>> sorted = new ArrayList<>(parsed);
        sorted.sort(QualityValue::compareTo);

        for (QualityValue<String> qv : sorted) {
            if (qv.quality() <= 0.0) {
                continue;
            }

            String enc = qv.value().toLowerCase(Locale.ROOT);

            if (enc.equals("*")) {
                return supportedEncodings.getFirst();
            }

            for (String supported : supportedEncodings) {
                if (supported.equalsIgnoreCase(enc)) {
                    return supported;
                }
            }
        }

        return null;
    }
}
