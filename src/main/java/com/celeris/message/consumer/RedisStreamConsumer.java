package com.celeris.message.consumer;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import com.celeris.message.exception.InvalidMessageRequestException;
import com.celeris.message.exception.MessageConflictException;
import com.celeris.message.exception.TemplateNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.celeris.message.service.MessageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class RedisStreamConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private static final String RAW_PAYLOAD_FIELD = "_raw";

    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;
    private final String deadLetterStreamKey;
    private final String deliveryAttemptHashKey;
    private final int maxDeliveryAttempts;
    private final long reclaimBatchSize;
    private final Duration reclaimIdleTime;

    public RedisStreamConsumer(
            MessageService messageService,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            @Value("${message.redis-stream.stream-key:celeris:message:stream}") String streamKey,
            @Value("${message.redis-stream.consumer-group:message-service-group}") String consumerGroup,
            @Value("${message.redis-stream.consumer-name:consumer-1}") String consumerName,
            @Value("${message.redis-stream.dead-letter-stream-key:celeris:message:stream:dead-letter}") String deadLetterStreamKey,
            @Value("${message.redis-stream.delivery-attempt-hash-key:celeris:message:stream:delivery-attempts}") String deliveryAttemptHashKey,
            @Value("${message.redis-stream.max-delivery-attempts:5}") int maxDeliveryAttempts,
            @Value("${message.redis-stream.reclaim-idle-ms:60000}") long reclaimIdleMs,
            @Value("${message.redis-stream.reclaim-batch-size:20}") long reclaimBatchSize) {
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
        this.deadLetterStreamKey = deadLetterStreamKey;
        this.deliveryAttemptHashKey = deliveryAttemptHashKey;
        this.maxDeliveryAttempts = Math.max(1, maxDeliveryAttempts);
        this.reclaimBatchSize = Math.max(1L, reclaimBatchSize);
        this.reclaimIdleTime = Duration.ofMillis(Math.max(1L, reclaimIdleMs));
    }

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        long deliveryAttempt = incrementDeliveryAttempt(record.getStream(), recordId(record));
        log.info("Received message from Redis Stream: stream={}, id={}, attempt={}, payload={}",
                record.getStream(), record.getId(), deliveryAttempt, record.getValue());
        handleRecord(record.getStream(), recordId(record), record.getValue(), deliveryAttempt, false);
    }

    @Scheduled(fixedDelayString = "${message.redis-stream.reclaim-fixed-delay-ms:30000}")
    public void reclaimPendingMessages() {
        long reclaimedCount = 0L;
        String pendingCursor = null;

        try {
            while (true) {
                var pendingScan = scanPendingMessages(pendingCursor);
                if (pendingScan.isEmpty()) {
                    break;
                }

                pendingCursor = pendingScan.nextCursor();
                List<RecordId> reclaimCandidates = pendingScan.reclaimCandidates();
                if (reclaimCandidates.isEmpty()) {
                    if (!pendingScan.hasMore()) {
                        break;
                    }
                    continue;
                }

                List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                        streamKey,
                        consumerGroup,
                        consumerName,
                        reclaimIdleTime,
                        reclaimCandidates.toArray(RecordId[]::new)
                );

                if (claimed == null || claimed.isEmpty()) {
                    break;
                }

                for (MapRecord<String, Object, Object> record : claimed) {
                    long deliveryAttempt = incrementDeliveryAttempt(record.getStream(), recordId(record));
                    log.warn("Reclaimed pending Redis Stream message: stream={}, id={}, attempt={}",
                            record.getStream(), record.getId(), deliveryAttempt);
                    handleRecord(record.getStream(), recordId(record), extractPayload(record), deliveryAttempt, true);
                    reclaimedCount++;
                }

                if (!pendingScan.hasMore()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to reclaim pending Redis Stream messages: stream={}, group={}, consumer={}",
                    streamKey, consumerGroup, consumerName, e);
        }

        if (reclaimedCount > 0) {
            log.info("Finished reclaiming pending Redis Stream messages: stream={}, group={}, consumer={}, reclaimed={}",
                    streamKey, consumerGroup, consumerName, reclaimedCount);
        }
    }

    private void handleRecord(String stream, String recordId, String payload, long deliveryAttempt, boolean reclaimed) {
        log.info("Processing Redis Stream message: stream={}, id={}, reclaimed={}, attempt={}",
                stream, recordId, reclaimed, deliveryAttempt);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});

            MessageRequest request = MessageRequest.builder()
                    .templateCode(getStr(data, "template_code"))
                    .channel(parseChannel(getStr(data, "channel")))
                    .recipient(getStr(data, "recipient"))
                    .subject(getStr(data, "subject"))
                    .content(getStr(data, "content"))
                    .bizId(getStr(data, "biz_id"))
                    .build();

            // Extract vars if present
            Object vars = data.get("vars");
            if (vars instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> varsMap = (Map<String, String>) vars;
                request.setVars(varsMap);
            }

            SendResult result = messageService.send(request);
            if (!result.isSuccess()) {
                log.warn("Message delivery failed after persistence; retry scheduled in DB: id={}, messageId={}, error={}",
                        recordId, result.getMessageId(), result.getErrorMsg());
            }
            acknowledge(stream, recordId);
            clearDeliveryAttempt(stream, recordId);
        } catch (InvalidMessageRequestException | TemplateNotFoundException | MessageConflictException e) {
            publishDeadLetter(stream, recordId, payload, e.getMessage(), deliveryAttempt, reclaimed, "non_retryable");
            acknowledge(stream, recordId);
            clearDeliveryAttempt(stream, recordId);
            log.warn("Message moved to dead-letter stream: stream={}, id={}, attempt={}, error={}",
                    stream, recordId, deliveryAttempt, e.getMessage());
        } catch (Exception e) {
            if (deliveryAttempt >= maxDeliveryAttempts) {
                publishDeadLetter(
                        stream,
                        recordId,
                        payload,
                        Objects.toString(e.getMessage(), e.getClass().getSimpleName()),
                        deliveryAttempt,
                        reclaimed,
                        "max_delivery_exhausted"
                );
                acknowledge(stream, recordId);
                clearDeliveryAttempt(stream, recordId);
                log.warn("Message exceeded max Redis Stream delivery attempts and was dead-lettered: stream={}, id={}, attempt={}",
                        stream, recordId, deliveryAttempt);
                return;
            }

            log.error("Failed to process Redis Stream message; leaving pending for reclaim: stream={}, id={}, attempt={}",
                    stream, recordId, deliveryAttempt, e);
        }
    }

    private String getStr(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private ChannelType parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return ChannelType.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidMessageRequestException(
                    "Unknown channel type: " + channel + ". Valid values: EMAIL, SMS, WEBHOOK, IN_APP"
            );
        }
    }

    private PendingScan scanPendingMessages(String pendingCursor) {
        Range<String> pendingRange = pendingCursor == null
                ? Range.unbounded()
                : Range.rightUnbounded(Range.Bound.exclusive(pendingCursor));
        var pendingMessages = redisTemplate.opsForStream()
                .pending(streamKey, consumerGroup, pendingRange, reclaimBatchSize);
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return PendingScan.empty();
        }

        List<RecordId> reclaimCandidates = new ArrayList<>();
        String nextCursor = pendingCursor;
        for (PendingMessage pendingMessage : pendingMessages) {
            nextCursor = pendingMessage.getIdAsString();
            if (pendingMessage.getElapsedTimeSinceLastDelivery().compareTo(reclaimIdleTime) < 0) {
                continue;
            }
            reclaimCandidates.add(pendingMessage.getId());
        }
        return new PendingScan(reclaimCandidates, nextCursor, pendingMessages.size() >= reclaimBatchSize);
    }

    private String extractPayload(MapRecord<String, Object, Object> record) {
        Map<Object, Object> payloadMap = record.getValue();
        if (payloadMap == null || payloadMap.isEmpty()) {
            throw new InvalidMessageRequestException("Redis Stream payload is empty");
        }

        Object rawPayload = payloadMap.get(RAW_PAYLOAD_FIELD);
        if (rawPayload != null && !rawPayload.toString().isBlank()) {
            return rawPayload.toString();
        }

        if (payloadMap.size() == 1) {
            return payloadMap.values().iterator().next().toString();
        }

        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new InvalidMessageRequestException("Failed to reconstruct Redis Stream payload");
        }
    }

    private String recordId(ObjectRecord<String, String> record) {
        return record.getId().getValue();
    }

    private String recordId(MapRecord<String, Object, Object> record) {
        return record.getId().getValue();
    }

    private void acknowledge(String stream, String recordId) {
        Long acked = redisTemplate.opsForStream().acknowledge(stream, consumerGroup, recordId);
        if (acked == null || acked == 0L) {
            log.warn("Redis Stream acknowledge returned no match: stream={}, id={}, group={}",
                    stream, recordId, consumerGroup);
        }
    }

    private long incrementDeliveryAttempt(String stream, String recordId) {
        Long attempts = redisTemplate.opsForHash().increment(deliveryAttemptHashKey, deliveryAttemptField(stream, recordId), 1L);
        return attempts != null ? attempts : 1L;
    }

    private void clearDeliveryAttempt(String stream, String recordId) {
        redisTemplate.opsForHash().delete(deliveryAttemptHashKey, deliveryAttemptField(stream, recordId));
    }

    private String deliveryAttemptField(String stream, String recordId) {
        return stream + "|" + recordId;
    }

    private void publishDeadLetter(
            String stream,
            String recordId,
            String payload,
            String error,
            long deliveryAttempt,
            boolean reclaimed,
            String reason) {
        Map<String, String> deadLetter = new HashMap<>();
        deadLetter.put("original_stream", stream);
        deadLetter.put("original_id", recordId);
        deadLetter.put("payload", payload);
        deadLetter.put("error", error);
        deadLetter.put("reason", reason);
        deadLetter.put("delivery_attempt", Long.toString(deliveryAttempt));
        deadLetter.put("max_delivery_attempts", Integer.toString(maxDeliveryAttempts));
        deadLetter.put("reclaimed", Boolean.toString(reclaimed));
        deadLetter.put("consumer_group", consumerGroup);
        deadLetter.put("consumer_name", consumerName);
        deadLetter.put("failed_at", LocalDateTime.now().toString());
        redisTemplate.opsForStream().add(StreamRecords.mapBacked(deadLetter).withStreamKey(deadLetterStreamKey));
    }

    private record PendingScan(List<RecordId> reclaimCandidates, String nextCursor, boolean hasMore) {

        private static PendingScan empty() {
            return new PendingScan(List.of(), null, false);
        }

        private boolean isEmpty() {
            return nextCursor == null;
        }
    }
}
