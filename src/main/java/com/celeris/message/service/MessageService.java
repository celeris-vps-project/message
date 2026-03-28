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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageDispatcher dispatcher;
    private final MessageRecordRepository recordRepository;

    @Value("${message.retry.max-attempts:3}")
    private int maxRetryAttempts;

    public SendResult send(MessageRequest request) {
        return dispatcher.dispatch(request);
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
                .findByStatusAndRetryCountLessThan(MessageStatus.FAILED, maxRetryAttempts);

        for (MessageRecord record : failedRecords) {
            log.info("Retrying message: id={}, bizId={}, attempt={}", record.getId(), record.getBizId(), record.getRetryCount() + 1);

            MessageRequest request = MessageRequest.builder()
                    .bizId(record.getBizId())
                    .channel(record.getChannel())
                    .recipient(record.getRecipient())
                    .subject(record.getSubject())
                    .content(record.getContent())
                    .templateCode(record.getTemplateCode())
                    .build();

            record.setRetryCount(record.getRetryCount() + 1);
            record.setStatus(MessageStatus.SENDING);
            recordRepository.save(record);

            try {
                SendResult result = dispatcher.dispatch(request);
                if (!result.isSuccess()) {
                    log.warn("Retry failed for message: id={}, error={}", record.getId(), result.getErrorMsg());
                }
            } catch (Exception e) {
                record.setStatus(MessageStatus.FAILED);
                record.setErrorMsg(e.getMessage());
                recordRepository.save(record);
                log.error("Retry exception for message: id={}", record.getId(), e);
            }
        }
    }
}
