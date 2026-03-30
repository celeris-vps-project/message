package com.celeris.message.domain.model;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "message_record")
@Getter
@Setter
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
    private LocalDateTime createdAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = MessageStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageRecord that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
