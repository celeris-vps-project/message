package com.celeris.message.channel;

import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractMessageChannel implements MessageChannel {

    @Override
    public SendResult send(MessageRequest request) {
        log.info("Sending message via {}: recipient={}, bizId={}", type(), request.getRecipient(), request.getBizId());
        try {
            SendResult result = doSend(request);
            if (result.isSuccess()) {
                log.info("Message sent successfully via {}: bizId={}", type(), request.getBizId());
            } else {
                log.warn("Message send failed via {}: bizId={}, error={}", type(), request.getBizId(), result.getErrorMsg());
            }
            return result;
        } catch (Exception e) {
            log.error("Message send error via {}: bizId={}", type(), request.getBizId(), e);
            return SendResult.fail(e.getMessage());
        }
    }

    protected abstract SendResult doSend(MessageRequest request);
}
