package com.celeris.message.repository;

import com.celeris.message.domain.enums.MessageStatus;
import com.celeris.message.domain.model.MessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRecordRepository extends JpaRepository<MessageRecord, Long> {

    Optional<MessageRecord> findByBizId(String bizId);

    List<MessageRecord> findByStatusAndRetryCountLessThan(MessageStatus status, int maxRetry);
}
