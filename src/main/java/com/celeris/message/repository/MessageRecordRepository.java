package com.celeris.message.repository;

import com.celeris.message.domain.enums.MessageStatus;
import com.celeris.message.domain.model.MessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRecordRepository extends JpaRepository<MessageRecord, Long> {

    Optional<MessageRecord> findByBizId(String bizId);

    List<MessageRecord> findByStatusAndRetryCountLessThan(MessageStatus status, int maxRetry);

    @Query("""
            select record
            from MessageRecord record
            where record.status = :status
              and record.retryCount < :maxRetry
              and (record.nextRetryAt is null or record.nextRetryAt <= :now)
            order by record.createdAt asc
            """)
    List<MessageRecord> findRetryableRecords(
            @Param("status") MessageStatus status,
            @Param("maxRetry") int maxRetry,
            @Param("now") LocalDateTime now
    );
}
