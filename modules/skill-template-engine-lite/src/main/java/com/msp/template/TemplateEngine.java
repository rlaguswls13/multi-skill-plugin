package com.msp.template;

import com.msp.template.parser.TemplateParser;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight string/text template engine.
 * Supports variable interpolation, nested property lookup, default values, number/date formatting,
 * conditionals (if-else), and loops (each).
 */
public final class TemplateEngine {

    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    private final Map<String, CompiledTemplate> cache;
    private final boolean cachingEnabled;

    private TemplateEngine(boolean cachingEnabled) {
        this.cachingEnabled = cachingEnabled;
        this.cache = cachingEnabled ? new ConcurrentHashMap<>() : null;
    }

    /**
     * Creates a new TemplateEngine with compilation caching enabled.
     */
    public static TemplateEngine create() {
        return new TemplateEngine(true);
    }

    /**
     * Creates a new TemplateEngine with the specified caching option.
     */
    public static TemplateEngine create(boolean enableCache) {
        return new TemplateEngine(enableCache);
    }

    /**
     * Compiles the template string into a {@link CompiledTemplate}.
     */
    public CompiledTemplate compile(String template) {
        Objects.requireNonNull(template, "template must not be null");
        if (!cachingEnabled) {
            return new CompiledTemplate(template, TemplateParser.parse(template));
        }

        if (cache.size() > DEFAULT_MAX_CACHE_SIZE) {
            cache.clear();
        }
        return cache.computeIfAbsent(template, t -> new CompiledTemplate(t, TemplateParser.parse(t)));
    }

    /**
     * Renders a template with the given map context.
     */
    public String render(String template, Map<String, Object> context) {
        return compile(template).render(context);
    }

    /**
     * Renders a template with the given root object (Record, POJO, or Map).
     */
    public String render(String template, Object rootObject) {
        return compile(template).render(rootObject);
    }

    /**
     * Clears cached compiled templates.
     */
    public void clearCache() {
        if (cache != null) {
            cache.clear();
        }
    }
}
