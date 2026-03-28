package com.celeris.message.domain.model;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_id", unique = true, length = 64)
    private String bizId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private ChannelType channel;

    @Column(name = "recipient", nullable = false, length = 256)
    private String recipient;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
