package com.celeris.message.dispatcher;

import com.celeris.message.channel.MessageChannel;
import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.enums.MessageStatus;
import com.celeris.message.domain.model.MessageRecord;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.MessageTemplate;
import com.celeris.message.domain.model.SendResult;
import com.celeris.message.repository.MessageRecordRepository;
import com.celeris.message.service.TemplateService;
import com.celeris.message.template.TemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDispatcherTest {

    @Mock
    private MessageChannel emailChannel;

    @Mock
    private TemplateRenderer templateRenderer;

    @Mock
    private TemplateService templateService;

    @Mock
    private MessageRecordRepository recordRepository;

    private MessageDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(emailChannel.type()).thenReturn(ChannelType.EMAIL);
        dispatcher = new MessageDispatcher(
                List.of(emailChannel),
                templateRenderer,
                templateService,
                recordRepository
        );
    }

    @Test
    void dispatch_directContent_success() {
        MessageRequest request = MessageRequest.builder()
                .channel(ChannelType.EMAIL)
                .recipient("test@example.com")
                .subject("Hello")
                .content("World")
                .bizId("biz-100")
                .build();

        when(recordRepository.findByBizId("biz-100")).thenReturn(Optional.empty());
        when(recordRepository.save(any(MessageRecord.class))).thenAnswer(inv -> {
            MessageRecord r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(emailChannel.send(any())).thenReturn(SendResult.ok("1"));

        SendResult result = dispatcher.dispatch(request);

        assertTrue(result.isSuccess());
        verify(emailChannel).send(any());
        verify(recordRepository, times(2)).save(any(MessageRecord.class));
    }

    @Test
    void dispatch_withTemplate_rendersContent() {
        MessageTemplate template = MessageTemplate.builder()
                .code("order-confirm")
                .channel(ChannelType.EMAIL)
                .subjectTemplate("Order ${orderId}")
                .contentTemplate("Hello ${username}")
                .enabled(true)
                .build();

        MessageRequest request = MessageRequest.builder()
                .templateCode("order-confirm")
                .channel(ChannelType.EMAIL)
                .recipient("test@example.com")
                .vars(Map.of("orderId", "ORD-001", "username", "John"))
                .bizId("biz-101")
                .build();

        when(recordRepository.findByBizId("biz-101")).thenReturn(Optional.empty());
        when(templateService.findByCode("order-confirm")).thenReturn(Optional.of(template));
        when(templateRenderer.renderSimple(eq("Hello ${username}"), any())).thenReturn("Hello John");
        when(templateRenderer.renderSimple(eq("Order ${orderId}"), any())).thenReturn("Order ORD-001");
        when(recordRepository.save(any(MessageRecord.class))).thenAnswer(inv -> {
            MessageRecord r = inv.getArgument(0);
            r.setId(2L);
            return r;
        });
        when(emailChannel.send(any())).thenReturn(SendResult.ok("2"));

        SendResult result = dispatcher.dispatch(request);

        assertTrue(result.isSuccess());
        assertEquals("Hello John", request.getContent());
        assertEquals("Order ORD-001", request.getSubject());
    }

    @Test
    void dispatch_idempotent_duplicateIgnored() {
        MessageRecord existing = MessageRecord.builder()
                .id(99L)
                .bizId("dup-biz")
                .status(MessageStatus.SENT)
                .build();

        when(recordRepository.findByBizId("dup-biz")).thenReturn(Optional.of(existing));

        MessageRequest request = MessageRequest.builder()
                .channel(ChannelType.EMAIL)
                .recipient("test@example.com")
                .content("Hello")
                .bizId("dup-biz")
                .build();

        SendResult result = dispatcher.dispatch(request);

        assertTrue(result.isSuccess());
        verify(emailChannel, never()).send(any());
    }

    @Test
    void dispatch_unsupportedChannel_fails() {
        MessageRequest request = MessageRequest.builder()
                .channel(ChannelType.SMS)
                .recipient("1234567890")
                .content("Hello")
                .build();

        when(recordRepository.save(any(MessageRecord.class))).thenAnswer(inv -> {
            MessageRecord r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });

        SendResult result = dispatcher.dispatch(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMsg().contains("Unsupported channel"));
    }
}
