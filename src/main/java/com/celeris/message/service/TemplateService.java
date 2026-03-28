package com.celeris.message.service;

import com.celeris.message.domain.model.MessageTemplate;
import com.celeris.message.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final MessageTemplateRepository templateRepository;

    public Optional<MessageTemplate> findByCode(String code) {
        return templateRepository.findByCode(code);
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
}
