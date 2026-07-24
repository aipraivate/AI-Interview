package com.interview.platform.analytics;

import com.interview.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;

@Service
class AnalyticsService {
    private static final Set<String> EVENTS = Set.of("signup_start", "signup_success", "resume_upload",
            "parse_success", "jd_confirm", "interview_create", "answer_submit", "interview_complete",
            "report_ready", "report_view", "evidence_expand", "report_feedback", "paywall_view",
            "order_create", "payment_success", "practice_again");
    private static final Set<String> PROPERTIES = Set.of("channel", "campaign", "device", "experiment_id",
            "file_type", "latency_bucket", "role_family", "error_code", "mode", "question_count",
            "duration_bucket", "model_route", "rubric_version", "score_band", "feedback_reason",
            "product_id", "price_bucket", "benefit", "days_since_first", "same_role", "score_delta_bucket");
    private final AnalyticsEventRepository events;
    private final ObjectMapper mapper;
    AnalyticsService(AnalyticsEventRepository events, ObjectMapper mapper) { this.events = events; this.mapper = mapper; }

    void record(String userId, Command command) {
        if (!EVENTS.contains(command.eventName())) throw new BusinessException("INVALID_EVENT", "不支持的埋点事件", HttpStatus.BAD_REQUEST);
        Map<String, String> properties = command.properties() == null ? Map.of() : command.properties();
        if (properties.size() > 20 || properties.keySet().stream().anyMatch(key -> !PROPERTIES.contains(key))
                || properties.values().stream().anyMatch(value -> value != null && value.length() > 100)) {
            throw new BusinessException("INVALID_EVENT_PROPERTIES", "埋点属性不符合最小化规则", HttpStatus.BAD_REQUEST);
        }
        try { events.save(new AnalyticsEvent(userId, command.eventName(), mapper.writeValueAsString(properties))); }
        catch (Exception exception) { throw new IllegalStateException("analytics serialization failed", exception); }
    }
    record Command(String eventName, Map<String, String> properties) {}
}
