package com.celeris.message.dispatcher;

import com.celeris.message.channel.MessageChannel;
import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.enums.MessageStatus;
import com.celeris.message.domain.model.*;
import com.celeris.message.exception.InvalidMessageRequestException;
import com.celeris.message.exception.MessageConflictException;
import com.celeris.message.exception.TemplateNotFoundException;
import com.celeris.message.repository.MessageRecordRepository;
import com.celeris.message.service.TemplateService;
import com.celeris.message.template.TemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
        if (hasText(request.getBizId())) {
            Optional<MessageRecord> existing = recordRepository.findByBizId(request.getBizId());
            if (existing.isPresent()) {
                return resolveExistingRecord(existing.get());
            }
        }

        if (hasText(request.getTemplateCode())) {
            applyTemplate(request);
        }

        MessageChannel channel = resolveChannel(request.getChannel());
        MessageRecord record = MessageRecord.builder()
                .bizId(request.getBizId())
                .channel(request.getChannel())
                .recipient(request.getRecipient())
                .subject(request.getSubject())
                .content(request.getContent())
                .templateCode(request.getTemplateCode())
                .status(MessageStatus.SENDING)
                .build();

        try {
            record = recordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            if (hasText(request.getBizId())) {
                Optional<MessageRecord> existing = recordRepository.findByBizId(request.getBizId());
                if (existing.isPresent()) {
                    return resolveExistingRecord(existing.get());
                }
            }
            throw e;
        }

        SendResult result = channel.send(request);

        if (result.isSuccess()) {
            record.setStatus(MessageStatus.SENT);
            record.setErrorMsg(null);
            record.setNextRetryAt(null);
            record.setSentAt(LocalDateTime.now());
        } else {
            record.setStatus(MessageStatus.FAILED);
            record.setErrorMsg(result.getErrorMsg());
        }
        recordRepository.save(record);

        result.setMessageId(record.getId().toString());
        return result;
    }

    /**
     * Retry sending for an existing message record.
     * This bypasses record creation and idempotency checks.
     */
    public SendResult retry(MessageRecord record) {
        log.info("Retrying message: id={}, bizId={}", record.getId(), record.getBizId());

        MessageRequest request = MessageRequest.builder()
                .bizId(record.getBizId())
                .channel(record.getChannel())
                .recipient(record.getRecipient())
                .subject(record.getSubject())
                .content(record.getContent())
                .templateCode(record.getTemplateCode())
                .build();

        MessageChannel channel = channelMap.get(record.getChannel());
        if (channel == null) {
            record.setStatus(MessageStatus.FAILED);
            record.setErrorMsg("Unsupported channel: " + record.getChannel());
            recordRepository.save(record);
            return SendResult.fail("Unsupported channel: " + record.getChannel());
        }

        record.setStatus(MessageStatus.SENDING);
        record.setErrorMsg(null);
        recordRepository.save(record);

        SendResult result = channel.send(request);

        if (result.isSuccess()) {
            record.setStatus(MessageStatus.SENT);
            record.setErrorMsg(null);
            record.setNextRetryAt(null);
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

    private void applyTemplate(MessageRequest request) {
        MessageTemplate template = templateService.resolveByCode(request.getTemplateCode())
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + request.getTemplateCode()));

        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new InvalidMessageRequestException("Template is disabled: " + request.getTemplateCode());
        }

        String renderedContent = isHtmlTemplate(template.getContentTemplate())
                ? templateRenderer.render(template.getContentTemplate(), request.getVars())
                : templateRenderer.renderSimple(template.getContentTemplate(), request.getVars());
        request.setContent(renderedContent);

        if (template.getSubjectTemplate() != null && request.getSubject() == null) {
            request.setSubject(templateRenderer.renderSimple(template.getSubjectTemplate(), request.getVars()));
        }

        if (request.getChannel() == null) {
            request.setChannel(template.getChannel());
        }
    }

    private MessageChannel resolveChannel(ChannelType channelType) {
        if (channelType == null) {
            throw new InvalidMessageRequestException("channel is required");
        }

        MessageChannel channel = channelMap.get(channelType);
        if (channel == null) {
            throw new InvalidMessageRequestException("Unsupported channel: " + channelType);
        }
        return channel;
    }

    private SendResult resolveExistingRecord(MessageRecord record) {
        if (record.getStatus() == MessageStatus.SENT
                || record.getStatus() == MessageStatus.SENDING
                || record.getStatus() == MessageStatus.PENDING) {
            log.info("Duplicate message reused existing record: bizId={}, status={}", record.getBizId(), record.getStatus());
            return SendResult.ok(record.getId().toString());
        }

        throw new MessageConflictException(
                "Message with bizId " + record.getBizId() + " already exists in status " + record.getStatus()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
