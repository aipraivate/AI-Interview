package com.interview.platform.outbox;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private final OutboxEventRepository events;

    public OutboxService(OutboxEventRepository events) {
        this.events = events;
    }

    public void enqueueReport(String sessionId) {
        try {
            events.save(new OutboxEvent("INTERVIEW_REPORT_REQUESTED", sessionId));
        } catch (DataIntegrityViolationException ignored) {
            // The unique event key makes completion idempotent.
        }
    }

    public void requeueReport(String sessionId) {
        events.findByEventTypeAndAggregateId("INTERVIEW_REPORT_REQUESTED", sessionId)
                .ifPresentOrElse(OutboxEvent::requeue, () -> enqueueReport(sessionId));
    }
}
