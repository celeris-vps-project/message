package com.celeris.message.template;

import java.util.Map;

public interface TemplateRenderer {

    /**
     * Render a template string with the given variables.
     *
     * @param template the template content (Thymeleaf HTML or simple placeholder)
     * @param vars     template variables
     * @return rendered content
     */
    String render(String template, Map<String, String> vars);

    /**
     * Render simple placeholder template: ${varName} style.
     */
    String renderSimple(String template, Map<String, String> vars);
}
