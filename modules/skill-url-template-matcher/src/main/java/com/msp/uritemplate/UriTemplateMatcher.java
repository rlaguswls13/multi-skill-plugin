package com.msp.uritemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring / RFC 6570 style URI template parser, matcher, and expander.
 * <p>
 * Supports:
 * <ul>
 *     <li>Single-segment path variables: {@code /api/users/{userId}}</li>
 *     <li>Multi-segment wildcard variables: {@code /files/{path:*}}, {@code /docs/{*path}}, {@code /static/{path*}}</li>
 *     <li>Literal wildcards: {@code /api/v1/*\/info}, {@code /public/**}</li>
 *     <li>Variable expansion: {@code expand("/api/users/{id}", Map.of("id", 123))}</li>
 * </ul>
 * Thread-safe and zero external dependencies.
 */
public final class UriTemplateMatcher {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^{}]+)\\}");
    private static final Map<String, UriTemplateMatcher> COMPILED_CACHE = new ConcurrentHashMap<>();

    private final String template;
    private final Pattern regexPattern;
    private final List<String> variableNames;

    /**
     * Constructs a compiled UriTemplateMatcher for the given template string.
     *
     * @param template URI template, e.g. {@code /api/users/{userId}/orders/{orderId}}
     */
    public UriTemplateMatcher(String template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
        List<String> names = new ArrayList<>();
        this.regexPattern = compileTemplate(template, names);
        this.variableNames = Collections.unmodifiableList(names);
    }

    /**
     * Factory method creating or reusing a compiled UriTemplateMatcher.
     */
    public static UriTemplateMatcher of(String template) {
        Objects.requireNonNull(template, "template must not be null");
        return COMPILED_CACHE.computeIfAbsent(template, UriTemplateMatcher::new);
    }

    /**
     * Matches the given URI against this template and extracts variable values.
     *
     * @param uri URI string to match (e.g. {@code /api/users/42/orders/100})
     * @return Optional containing map of extracted variables, or empty if no match
     */
    public Optional<Map<String, String>> match(String uri) {
        if (uri == null) {
            return Optional.empty();
        }
        String path = extractPath(uri);
        Matcher matcher = regexPattern.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < variableNames.size(); i++) {
            String name = variableNames.get(i);
            String value = matcher.group(i + 1);
            variables.put(name, value != null ? value : "");
        }
        return Optional.of(Collections.unmodifiableMap(variables));
    }

    /**
     * Checks whether the given URI matches this template.
     */
    public boolean matches(String uri) {
        return match(uri).isPresent();
    }

    /**
     * Expands this template with the provided variable values.
     *
     * @param variables Map containing variable values
     * @return Expanded URI string
     * @throws IllegalArgumentException if a required variable is missing
     */
    public String expand(Map<String, ?> variables) {
        Map<String, ?> vars = variables != null ? variables : Collections.emptyMap();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String varName = normalizeVarName(placeholder);

            if (!vars.containsKey(varName)) {
                throw new IllegalArgumentException("Missing required URI template variable: '" + varName + "'");
            }

            Object val = vars.get(varName);
            String replacement = val != null ? val.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Static utility: matches URI against template and extracts variables.
     */
    public static Optional<Map<String, String>> match(String template, String uri) {
        return of(template).match(uri);
    }

    /**
     * Static utility: checks if URI matches template.
     */
    public static boolean matches(String template, String uri) {
        return of(template).matches(uri);
    }

    /**
     * Static utility: expands template using provided variables.
     */
    public static String expand(String template, Map<String, ?> variables) {
        return of(template).expand(variables);
    }

    public String getTemplate() {
        return template;
    }

    public List<String> getVariableNames() {
        return variableNames;
    }

    /**
     * Compiles a URI template into a Regex Pattern while collecting variable names.
     */
    private static Pattern compileTemplate(String template, List<String> variableNames) {
        StringBuilder regex = new StringBuilder("^");
        int lastEnd = 0;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String literal = template.substring(lastEnd, matcher.start());
            appendLiteral(regex, literal);

            String placeholder = matcher.group(1);
            String varName = normalizeVarName(placeholder);
            variableNames.add(varName);

            if (isWildcard(placeholder)) {
                // Wildcard matches multiple segments including slashes
                regex.append("(.*)");
            } else {
                // Standard variable matches single segment (excluding slash)
                regex.append("([^/]+)");
            }
            lastEnd = matcher.end();
        }

        String remainingLiteral = template.substring(lastEnd);
        appendLiteral(regex, remainingLiteral);
        regex.append("$");

        return Pattern.compile(regex.toString());
    }

    private static void appendLiteral(StringBuilder regex, String literal) {
        if (literal.isEmpty()) {
            return;
        }

        // Handle ** and * wildcards in literal parts
        int pos = 0;
        while (pos < literal.length()) {
            int doubleStar = literal.indexOf("**", pos);
            int singleStar = literal.indexOf("*", pos);

            if (doubleStar != -1 && (singleStar == -1 || doubleStar <= singleStar)) {
                if (doubleStar > pos) {
                    regex.append(Pattern.quote(literal.substring(pos, doubleStar)));
                }
                regex.append(".*");
                pos = doubleStar + 2;
            } else if (singleStar != -1) {
                if (singleStar > pos) {
                    regex.append(Pattern.quote(literal.substring(pos, singleStar)));
                }
                regex.append("[^/]*");
                pos = singleStar + 1;
            } else {
                regex.append(Pattern.quote(literal.substring(pos)));
                break;
            }
        }
    }

    private static String normalizeVarName(String placeholder) {
        if (placeholder.startsWith("*")) {
            return placeholder.substring(1).trim();
        }
        if (placeholder.endsWith(":*")) {
            return placeholder.substring(0, placeholder.length() - 2).trim();
        }
        if (placeholder.endsWith("*")) {
            return placeholder.substring(0, placeholder.length() - 1).trim();
        }
        return placeholder.trim();
    }

    private static boolean isWildcard(String placeholder) {
        return placeholder.startsWith("*") || placeholder.endsWith(":*") || placeholder.endsWith("*");
    }

    private static String extractPath(String uri) {
        String clean = uri.trim();
        if (clean.contains("://")) {
            try {
                URI u = URI.create(clean);
                String path = u.getPath();
                return (path != null && !path.isEmpty()) ? path : "/";
            } catch (Exception ignored) {
                // fallback to string extraction
                int schemeEnd = clean.indexOf("://");
                int slash = clean.indexOf('/', schemeEnd + 3);
                clean = (slash != -1) ? clean.substring(slash) : "/";
            }
        }
        int queryIndex = clean.indexOf('?');
        if (queryIndex != -1) {
            clean = clean.substring(0, queryIndex);
        }
        int fragmentIndex = clean.indexOf('#');
        if (fragmentIndex != -1) {
            clean = clean.substring(0, fragmentIndex);
        }
        return clean;
    }
}
