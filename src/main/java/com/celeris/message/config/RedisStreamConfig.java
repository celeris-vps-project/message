package com.celeris.message.config;

import com.celeris.message.consumer.RedisStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisStreamConfig {

    @Value("${message.redis-stream.stream-key:celeris:message:stream}")
    private String streamKey;

    @Value("${message.redis-stream.consumer-group:message-service-group}")
    private String consumerGroup;

    @Value("${message.redis-stream.consumer-name:consumer-1}")
    private String consumerName;

    @Bean
    public Subscription redisStreamSubscription(RedisConnectionFactory connectionFactory,
                                                 RedisStreamConsumer consumer,
                                                 StringRedisTemplate redisTemplate) {
        ensureConsumerGroup(redisTemplate);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .targetType(String.class)
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        Subscription subscription = container.receive(
                Consumer.from(consumerGroup, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                consumer
        );

        container.start();
        log.info("Redis Stream consumer started: group={}, consumer={}, stream={}", consumerGroup, consumerName, streamKey);
        return subscription;
    }

    private void ensureConsumerGroup(StringRedisTemplate redisTemplate) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            log.info("Created consumer group: {}", consumerGroup);
        } catch (Exception e) {
            // Group already exists or stream doesn't exist yet - both are OK
            log.debug("Consumer group setup: {}", e.getMessage());
        }
    }
}
