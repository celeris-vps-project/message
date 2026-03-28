package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SMS channel - SPI placeholder.
 * Replace with actual SMS provider integration (Twilio, Alibaba Cloud SMS, etc.)
 */
@Slf4j
@Component
public class SmsChannel extends AbstractMessageChannel {

    @Override
    public ChannelType type() {
        return ChannelType.SMS;
    }

    @Override
    protected SendResult doSend(MessageRequest request) {
        // TODO: Integrate actual SMS provider via SPI
        log.warn("SMS channel not yet implemented, skipping send to: {}", request.getRecipient());
        return SendResult.fail("SMS channel not implemented");
    }
}
