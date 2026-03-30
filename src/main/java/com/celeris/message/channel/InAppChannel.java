package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.InAppMessage;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import com.celeris.message.repository.InAppMessageRepository;
import com.celeris.message.service.InAppSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class InAppChannel extends AbstractMessageChannel {

    private final InAppMessageRepository inAppMessageRepository;
    private final InAppSseService inAppSseService;

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    protected SendResult doSend(MessageRequest request) {
        if (!StringUtils.hasText(request.getRecipient())) {
            return SendResult.fail("Invalid user ID: " + request.getRecipient());
        }

        InAppMessage msg = InAppMessage.builder()
                .userId(request.getRecipient().trim())
                .title(request.getSubject())
                .content(request.getContent())
                .bizId(request.getBizId())
                .build();
        InAppMessage saved = inAppMessageRepository.save(msg);
        inAppSseService.publish(saved);
        return SendResult.ok(saved.getId() != null ? saved.getId().toString() : request.getBizId());
    }
}
