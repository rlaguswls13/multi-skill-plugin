package com.msp.template;

import java.util.Map;

/**
 * Unified static facade for lightweight template processing.
 */
public final class Templates {

    private static final TemplateEngine DEFAULT_ENGINE = TemplateEngine.create();

    private Templates() {
    }

    /**
     * Returns the shared default TemplateEngine instance.
     */
    public static TemplateEngine defaultEngine() {
        return DEFAULT_ENGINE;
    }

    /**
     * Compiles a template string using the default engine.
     */
    public static CompiledTemplate compile(String template) {
        return DEFAULT_ENGINE.compile(template);
    }

    /**
     * Renders a template with the given map context using the default engine.
     */
    public static String render(String template, Map<String, Object> context) {
        return DEFAULT_ENGINE.render(template, context);
    }

    /**
     * Renders a template with the given root object (Record, POJO, or Map) using the default engine.
     */
    public static String render(String template, Object rootObject) {
        return DEFAULT_ENGINE.render(template, rootObject);
    }
}
