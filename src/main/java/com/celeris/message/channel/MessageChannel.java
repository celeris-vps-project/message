package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;

public interface MessageChannel {

    ChannelType type();

    SendResult send(MessageRequest request);

    default boolean supports(ChannelType type) {
        return this.type() == type;
    }
}
