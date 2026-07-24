package com.interview.platform.report;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, String> {
    Optional<ReportFeedback> findByReportIdAndUserId(String reportId, String userId);
}
