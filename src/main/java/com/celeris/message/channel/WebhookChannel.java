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

    public WebhookChannel() {
        this.restTemplate = new RestTemplate();
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
            return SendResult.ok(request.getBizId());
        } catch (Exception e) {
            return SendResult.fail(e.getMessage());
        }
    }
}
