package com.celeris.message.dispatcher;

import com.celeris.message.channel.MessageChannel;
import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.enums.MessageStatus;
import com.celeris.message.domain.model.*;
import com.celeris.message.repository.MessageRecordRepository;
import com.celeris.message.service.TemplateService;
import com.celeris.message.template.TemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageDispatcher {

    private final Map<ChannelType, MessageChannel> channelMap;
    private final TemplateRenderer templateRenderer;
    private final TemplateService templateService;
    private final MessageRecordRepository recordRepository;

    public MessageDispatcher(List<MessageChannel> channels,
                             TemplateRenderer templateRenderer,
                             TemplateService templateService,
                             MessageRecordRepository recordRepository) {
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(MessageChannel::type, Function.identity()));
        this.templateRenderer = templateRenderer;
        this.templateService = templateService;
        this.recordRepository = recordRepository;
    }

    public SendResult dispatch(MessageRequest request) {
        // Idempotency check
        if (request.getBizId() != null) {
            Optional<MessageRecord> existing = recordRepository.findByBizId(request.getBizId());
            if (existing.isPresent()) {
                MessageRecord record = existing.get();
                if (record.getStatus() == MessageStatus.SENT) {
                    log.info("Duplicate message ignored: bizId={}", request.getBizId());
                    return SendResult.ok(record.getId().toString());
                }
            }
        }

        // Resolve template if templateCode is provided
        if (request.getTemplateCode() != null && !request.getTemplateCode().isEmpty()) {
            MessageTemplate template = templateService.findByCode(request.getTemplateCode())
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + request.getTemplateCode()));

            if (!template.getEnabled()) {
                return SendResult.fail("Template is disabled: " + request.getTemplateCode());
            }

            // Render content from template
            String renderedContent = isHtmlTemplate(template.getContentTemplate())
                    ? templateRenderer.render(template.getContentTemplate(), request.getVars())
                    : templateRenderer.renderSimple(template.getContentTemplate(), request.getVars());
            request.setContent(renderedContent);

            // Render subject if template has one
            if (template.getSubjectTemplate() != null && request.getSubject() == null) {
                request.setSubject(templateRenderer.renderSimple(template.getSubjectTemplate(), request.getVars()));
            }

            // Use template channel if not specified
            if (request.getChannel() == null) {
                request.setChannel(template.getChannel());
            }
        }

        // Create record
        MessageRecord record = MessageRecord.builder()
                .bizId(request.getBizId())
                .channel(request.getChannel())
                .recipient(request.getRecipient())
                .subject(request.getSubject())
                .content(request.getContent())
                .templateCode(request.getTemplateCode())
                .status(MessageStatus.SENDING)
                .build();
        record = recordRepository.save(record);

        // Route to channel
        MessageChannel channel = channelMap.get(request.getChannel());
        if (channel == null) {
            record.setStatus(MessageStatus.FAILED);
            record.setErrorMsg("Unsupported channel: " + request.getChannel());
            recordRepository.save(record);
            return SendResult.fail("Unsupported channel: " + request.getChannel());
        }

        SendResult result = channel.send(request);

        // Update record
        if (result.isSuccess()) {
            record.setStatus(MessageStatus.SENT);
            record.setSentAt(LocalDateTime.now());
        } else {
            record.setStatus(MessageStatus.FAILED);
            record.setErrorMsg(result.getErrorMsg());
        }
        recordRepository.save(record);

        result.setMessageId(record.getId().toString());
        return result;
    }

    private boolean isHtmlTemplate(String template) {
        return template != null && (template.contains("<") && template.contains("th:"));
    }
}
