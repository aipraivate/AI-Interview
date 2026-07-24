package com.interview.platform.interview;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, String> {
    Optional<InterviewSession> findByIdAndUserId(String id, String userId);
    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(String userId);
    List<InterviewSession> findTop100ByStatusAndReservationExpiresAtBefore(
            InterviewStatus status, Instant deadline);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id and s.userId = :userId")
    Optional<InterviewSession> findOwnedForUpdate(@Param("id") String id, @Param("userId") String userId);
}
