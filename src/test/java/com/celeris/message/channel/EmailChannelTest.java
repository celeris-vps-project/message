package com.celeris.message.channel;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailChannel emailChannel;

    @BeforeEach
    void setUp() {
        emailChannel = new EmailChannel(mailSender, "noreply@example.com");
    }

    @Test
    void type_returnsEmail() {
        assertEquals(ChannelType.EMAIL, emailChannel.type());
    }

    @Test
    void supports_email() {
        assertTrue(emailChannel.supports(ChannelType.EMAIL));
        assertFalse(emailChannel.supports(ChannelType.SMS));
    }

    @Test
    void send_success() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        MessageRequest request = MessageRequest.builder()
                .recipient("test@example.com")
                .subject("Test Subject")
                .content("<p>Hello</p>")
                .bizId("biz-001")
                .build();

        SendResult result = emailChannel.send(request);

        assertTrue(result.isSuccess());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_failure() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP error"));

        MessageRequest request = MessageRequest.builder()
                .recipient("test@example.com")
                .subject("Test")
                .content("Hello")
                .bizId("biz-002")
                .build();

        SendResult result = emailChannel.send(request);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMsg());
    }
}
