package com.interview.platform.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, String> {
    Optional<ProcessedCommand> findBySessionIdAndClientMessageId(String sessionId, String clientMessageId);
}

