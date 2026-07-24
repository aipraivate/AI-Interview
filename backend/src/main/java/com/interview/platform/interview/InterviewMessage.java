package com.interview.platform.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_messages")
public class InterviewMessage {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String sessionId;
    @Column(nullable = false)
    private int sequenceNo;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(length = 80)
    private String clientMessageId;
    @Column(nullable = false)
    private Instant createdAt;

    protected InterviewMessage() {}

    InterviewMessage(String sessionId, int sequenceNo, String role, String content, String clientMessageId) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.sequenceNo = sequenceNo;
        this.role = role;
        this.content = content;
        this.clientMessageId = clientMessageId;
        this.createdAt = Instant.now();
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public int getSequenceNo() { return sequenceNo; }
    public Instant getCreatedAt() { return createdAt; }
}
