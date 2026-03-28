package com.celeris.message.consumer;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.service.MessageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        String payload = record.getValue();
        log.info("Received message from Redis Stream: id={}, payload={}", record.getId(), payload);

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

            messageService.send(request);

            // Acknowledge the message
            redisTemplate.opsForStream().acknowledge(
                    record.getStream(),
                    record.getId()
            );
        } catch (Exception e) {
            log.error("Failed to process Redis Stream message: id={}", record.getId(), e);
        }
    }

    private String getStr(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private ChannelType parseChannel(String channel) {
        if (channel == null) return null;
        try {
            return ChannelType.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown channel type: {}", channel);
            return null;
        }
    }
}
