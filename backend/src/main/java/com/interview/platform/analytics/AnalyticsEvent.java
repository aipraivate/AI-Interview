package com.interview.platform.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "analytics_events")
class AnalyticsEvent {
    @Id @Column(length = 36) private String id;
    @Column(length = 36) private String userId;
    @Column(nullable = false, length = 60) private String eventName;
    @Column(nullable = false, length = 2000) private String propertiesJson;
    @Column(nullable = false) private Instant occurredAt;
    protected AnalyticsEvent() {}
    AnalyticsEvent(String userId, String eventName, String propertiesJson) {
        id = UUID.randomUUID().toString(); this.userId = userId; this.eventName = eventName;
        this.propertiesJson = propertiesJson; occurredAt = Instant.now();
    }
}
