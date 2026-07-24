package com.interview.platform.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_commands")
class ProcessedCommand {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String sessionId;
    @Column(nullable = false, length = 80)
    private String clientMessageId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseJson;
    @Column(nullable = false)
    private Instant createdAt;

    protected ProcessedCommand() {}

    ProcessedCommand(String sessionId, String clientMessageId, String responseJson) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.clientMessageId = clientMessageId;
        this.responseJson = responseJson;
        this.createdAt = Instant.now();
    }

    String getResponseJson() { return responseJson; }
}

