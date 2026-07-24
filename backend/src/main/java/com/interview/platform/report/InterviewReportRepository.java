package com.interview.platform.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

interface InterviewReportRepository extends JpaRepository<InterviewReport, String> {
    Optional<InterviewReport> findBySessionId(String sessionId);
    Optional<InterviewReport> findBySessionIdAndUserId(String sessionId, String userId);
    List<InterviewReport> findByUserIdAndTargetRoleAndStatusOrderByGeneratedAtDesc(
            String userId, String targetRole, ReportStatus status);
}
