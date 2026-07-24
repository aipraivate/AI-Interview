package com.interview.platform.interview;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ReservationExpiryWorker {
    private final InterviewService interviews;
    ReservationExpiryWorker(InterviewService interviews) { this.interviews = interviews; }

    @Scheduled(fixedDelayString = "${app.interview.expiry-poll-ms:60000}")
    void expireReservations() { interviews.expireReservations(); }
}
