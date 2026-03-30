package com.celeris.message.repository;

import com.celeris.message.domain.model.InAppMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InAppMessageRepository extends JpaRepository<InAppMessage, Long> {

    List<InAppMessage> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);

    List<InAppMessage> findByUserIdOrderByCreatedAtDesc(String userId);
}
