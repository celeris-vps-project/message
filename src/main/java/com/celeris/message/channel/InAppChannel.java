package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.InAppMessage;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import com.celeris.message.repository.InAppMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppChannel extends AbstractMessageChannel {

    private final InAppMessageRepository inAppMessageRepository;

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    protected SendResult doSend(MessageRequest request) {
        try {
            Long userId = Long.parseLong(request.getRecipient());
            InAppMessage msg = InAppMessage.builder()
                    .userId(userId)
                    .title(request.getSubject())
                    .content(request.getContent())
                    .bizId(request.getBizId())
                    .build();
            inAppMessageRepository.save(msg);
            return SendResult.ok(request.getBizId());
        } catch (NumberFormatException e) {
            return SendResult.fail("Invalid user ID: " + request.getRecipient());
        }
    }
}
