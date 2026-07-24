package com.interview.platform.outbox;

import com.interview.platform.report.ReportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
class OutboxWorker {
    private final OutboxEventRepository events;
    private final ReportService reports;

    OutboxWorker(OutboxEventRepository events, ReportService reports) {
        this.events = events;
        this.reports = reports;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:250}")
    @Transactional
    public void process() {
        for (OutboxEvent event : events.findTop10ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                "PENDING", Instant.now())) {
            try {
                if ("INTERVIEW_REPORT_REQUESTED".equals(event.getEventType())) {
                    reports.generate(event.getAggregateId());
                }
                event.complete();
            } catch (RuntimeException exception) {
                if (!event.retry(exception)) reports.finalFailure(event.getAggregateId());
            }
        }
    }
}
