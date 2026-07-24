package com.interview.platform.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, String> {
    List<InterviewMessage> findBySessionIdOrderBySequenceNo(String sessionId);
}

