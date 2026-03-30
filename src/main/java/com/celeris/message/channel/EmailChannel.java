package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailChannel extends AbstractMessageChannel {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailChannel(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:noreply@example.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    protected SendResult doSend(MessageRequest request) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(request.getRecipient());
            helper.setSubject(request.getSubject() != null ? request.getSubject() : "");
            helper.setText(request.getContent(), true);
            mailSender.send(mimeMessage);
            log.info("Email sent successfully: to={}, subject={}", request.getRecipient(), request.getSubject());
            return SendResult.ok(request.getBizId());
        } catch (Exception e) {
            log.error("Failed to send email: to={}, error={}", request.getRecipient(), e.getMessage(), e);
            return SendResult.fail(e.getMessage());
        }
    }
}
