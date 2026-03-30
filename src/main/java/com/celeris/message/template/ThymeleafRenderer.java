package com.celeris.message.template;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThymeleafRenderer implements TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final TemplateEngine stringTemplateEngine;

    public ThymeleafRenderer() {
        // Create a dedicated TemplateEngine for inline string templates
        this.stringTemplateEngine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        stringTemplateEngine.setTemplateResolver(resolver);
    }

    @Override
    public String render(String template, Map<String, String> vars) {
        Context context = new Context();
        if (vars != null) {
            vars.forEach(context::setVariable);
        }
        return stringTemplateEngine.process(template, context);
    }

    @Override
    public String renderSimple(String template, Map<String, String> vars) {
        if (template == null) return null;
        if (vars == null || vars.isEmpty()) return template;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
