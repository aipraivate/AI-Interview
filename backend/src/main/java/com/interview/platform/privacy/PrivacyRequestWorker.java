package com.interview.platform.privacy;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PrivacyRequestWorker {
    private final PrivacyService privacy;
    PrivacyRequestWorker(PrivacyService privacy) { this.privacy = privacy; }
    @Scheduled(fixedDelayString = "${app.privacy.poll-delay-ms:5000}")
    void process() { privacy.processPending(); }
}
