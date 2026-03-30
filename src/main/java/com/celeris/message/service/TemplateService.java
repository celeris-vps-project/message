package com.celeris.message.service;

import com.celeris.message.domain.model.MessageTemplate;
import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final MessageTemplateRepository templateRepository;
    private final ResourceLoader resourceLoader;

    public Optional<MessageTemplate> findByCode(String code) {
        return templateRepository.findByCode(code);
    }

    public Optional<MessageTemplate> resolveByCode(String code) {
        return templateRepository.findByCode(code)
                .or(() -> loadClasspathTemplate(code));
    }

    public List<MessageTemplate> findAll() {
        return templateRepository.findAll();
    }

    @Transactional
    public MessageTemplate create(MessageTemplate template) {
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(template);
    }

    @Transactional
    public MessageTemplate update(Long id, MessageTemplate updated) {
        MessageTemplate existing = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        existing.setName(updated.getName());
        existing.setSubjectTemplate(updated.getSubjectTemplate());
        existing.setContentTemplate(updated.getContentTemplate());
        existing.setVarsSchema(updated.getVarsSchema());
        existing.setEnabled(updated.getEnabled());
        existing.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        templateRepository.deleteById(id);
    }

    private Optional<MessageTemplate> loadClasspathTemplate(String code) {
        Resource resource = resourceLoader.getResource("classpath:templates/email/" + code + ".html");
        if (!resource.exists()) {
            return Optional.empty();
        }

        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return Optional.of(MessageTemplate.builder()
                    .code(code)
                    .name(code)
                    .channel(ChannelType.EMAIL)
                    .contentTemplate(content)
                    .enabled(true)
                    .build());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template file: " + code, e);
        }
    }
}
