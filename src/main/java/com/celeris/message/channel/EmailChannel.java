package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailChannel extends AbstractMessageChannel {

    private final JavaMailSender mailSender;

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    protected SendResult doSend(MessageRequest request) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(request.getRecipient());
            helper.setSubject(request.getSubject() != null ? request.getSubject() : "");
            helper.setText(request.getContent(), true);
            mailSender.send(mimeMessage);
            return SendResult.ok(request.getBizId());
        } catch (Exception e) {
            return SendResult.fail(e.getMessage());
        }
    }
}
