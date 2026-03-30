package com.celeris.message.service;

import com.celeris.message.dispatcher.MessageDispatcher;
import com.celeris.message.domain.enums.MessageStatus;
import com.celeris.message.domain.model.MessageRecord;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import com.celeris.message.repository.MessageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageDispatcher dispatcher;
    private final MessageRecordRepository recordRepository;
    private final MessageRequestValidator requestValidator;

    @Value("${message.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${message.retry.initial-interval-ms:1000}")
    private long initialRetryIntervalMs;

    @Value("${message.retry.multiplier:2.0}")
    private double retryMultiplier;

    public SendResult send(MessageRequest request) {
        requestValidator.validate(request);

        SendResult result = dispatcher.dispatch(request);
        scheduleNextRetryIfNeeded(result);
        return result;
    }

    public MessageRecord getStatus(String bizId) {
        return recordRepository.findByBizId(bizId).orElse(null);
    }

    /**
     * Scheduled retry for failed messages with exponential backoff.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    public void retryFailedMessages() {
        List<MessageRecord> failedRecords = recordRepository
                .findRetryableRecords(MessageStatus.FAILED, maxRetryAttempts, LocalDateTime.now());

        for (MessageRecord record : failedRecords) {
            log.info("Retrying message: id={}, bizId={}, attempt={}",
                    record.getId(), record.getBizId(), record.getRetryCount() + 1);

            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastRetryAt(LocalDateTime.now());
            record.setNextRetryAt(null);

            try {
                SendResult result = dispatcher.retry(record);
                if (!result.isSuccess()) {
                    log.warn("Retry failed for message: id={}, error={}", record.getId(), result.getErrorMsg());
                }
                finalizeRetry(record);
            } catch (Exception e) {
                record.setStatus(MessageStatus.FAILED);
                record.setErrorMsg(e.getMessage());
                finalizeRetry(record);
                log.error("Retry exception for message: id={}", record.getId(), e);
            }
        }
    }

    private void scheduleNextRetryIfNeeded(SendResult result) {
        if (result.isSuccess() || result.getMessageId() == null || result.getMessageId().isBlank()) {
            return;
        }

        try {
            Long recordId = Long.parseLong(result.getMessageId());
            recordRepository.findById(recordId)
                    .ifPresent(this::finalizeRetry);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse message id for retry scheduling: {}", result.getMessageId());
        }
    }

    private void finalizeRetry(MessageRecord record) {
        if (record.getStatus() == MessageStatus.SENT) {
            record.setNextRetryAt(null);
            recordRepository.save(record);
            return;
        }

        if (record.getRetryCount() >= maxRetryAttempts) {
            record.setStatus(MessageStatus.EXHAUSTED);
            record.setNextRetryAt(null);
        } else {
            record.setStatus(MessageStatus.FAILED);
            record.setNextRetryAt(LocalDateTime.now().plus(calculateRetryDelay(record.getRetryCount())));
        }
        recordRepository.save(record);
    }

    private Duration calculateRetryDelay(int retryCount) {
        double rawDelay = initialRetryIntervalMs * Math.pow(retryMultiplier, retryCount);
        long safeDelayMs = Math.max(0L, Math.round(Math.min(rawDelay, Long.MAX_VALUE)));
        return Duration.ofMillis(safeDelayMs);
    }
}
