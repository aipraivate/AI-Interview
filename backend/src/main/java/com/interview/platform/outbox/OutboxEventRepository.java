package com.interview.platform.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop10ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            String status, Instant availableAt);
    Optional<OutboxEvent> findByEventTypeAndAggregateId(String eventType, String aggregateId);
}
