package com.interview.platform.audit;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public void record(String userId, String action, String resourceType, String resourceId) {
        logs.save(new AuditLog(userId, action, resourceType, resourceId,
                MDC.get("traceId"), "{}"));
    }
}
