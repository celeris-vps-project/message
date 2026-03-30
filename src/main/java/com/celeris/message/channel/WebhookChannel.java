package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class WebhookChannel extends AbstractMessageChannel {

    private final RestTemplate restTemplate;

    public WebhookChannel(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WEBHOOK;
    }

    @Override
    protected SendResult doSend(MessageRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.getContent(), headers);
            restTemplate.postForEntity(request.getRecipient(), entity, String.class);
            log.info("Webhook sent successfully: url={}", request.getRecipient());
            return SendResult.ok(request.getBizId());
        } catch (Exception e) {
            log.error("Failed to send webhook: url={}, error={}", request.getRecipient(), e.getMessage(), e);
            return SendResult.fail(e.getMessage());
        }
    }
}
