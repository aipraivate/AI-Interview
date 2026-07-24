package com.interview.platform.privacy;

import com.interview.platform.audit.AuditService;
import com.interview.platform.common.BusinessException;
import com.interview.platform.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class PrivacyService {
    private static final Set<String> TYPES = Set.of("EXPORT", "DELETE");
    private final DataRequestRepository requests;
    private final AuditService audit;
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PrivacyService(DataRequestRepository requests, AuditService audit, AuthService auth,
                          JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.requests = requests;
        this.audit = audit;
        this.auth = auth;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RequestView create(String userId, String type, String password) {
        String normalized = type == null ? "" : type.toUpperCase(java.util.Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new BusinessException("INVALID_DATA_REQUEST_TYPE", "仅支持数据导出或删除申请",
                    HttpStatus.BAD_REQUEST);
        }
        if ("DELETE".equals(normalized)) auth.verifyHighRiskAction(userId, password);
        DataRequest request = requests.save(new DataRequest(userId, normalized));
        audit.record(userId, "DATA_REQUEST_CREATED", "DATA_REQUEST", request.getId());
        return view(request);
    }

    @Transactional
    public int processPending() {
        List<DataRequest> pending = requests.findTop10ByStatusOrderByCreatedAtAsc("PENDING");
        for (DataRequest request : pending) {
            request.processing();
            try {
                if ("EXPORT".equals(request.getRequestType())) export(request);
                else delete(request);
                audit.record(request.getUserId(), "DATA_REQUEST_COMPLETED", "DATA_REQUEST", request.getId());
            } catch (RuntimeException exception) {
                request.fail("处理失败，已进入人工复核队列");
                audit.record(request.getUserId(), "DATA_REQUEST_FAILED", "DATA_REQUEST", request.getId());
            }
        }
        return pending.size();
    }

    @Transactional(readOnly = true)
    public String download(String requestId, String userId) {
        DataRequest request = requests.findByIdAndUserId(requestId, userId)
                .filter(value -> "EXPORT".equals(value.getRequestType()) && "COMPLETED".equals(value.getStatus()))
                .orElseThrow(() -> BusinessException.notFound("可下载的数据副本不存在"));
        if (request.getAvailableUntil() != null && request.getAvailableUntil().isBefore(Instant.now())) {
            throw new BusinessException("EXPORT_EXPIRED", "数据副本已过期，请重新申请", HttpStatus.GONE);
        }
        return request.getResultPayload();
    }

    private void export(DataRequest request) {
        String userId = request.getUserId();
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("exportedAt", Instant.now().toString());
        data.put("account", jdbc.queryForList("SELECT id,nickname,email,status,created_at FROM user_accounts WHERE id=?", userId));
        data.put("resumes", jdbc.queryForList("SELECT id,title,target_role,content,status,created_at,updated_at FROM resumes WHERE user_id=?", userId));
        data.put("interviews", jdbc.queryForList("SELECT id,status,target_role,jd_snapshot,resume_snapshot,created_at,completed_at FROM interview_sessions WHERE user_id=?", userId));
        data.put("messages", jdbc.queryForList("SELECT m.session_id,m.sequence_no,m.role,m.content,m.created_at FROM interview_messages m JOIN interview_sessions s ON s.id=m.session_id WHERE s.user_id=? ORDER BY m.session_id,m.sequence_no", userId));
        data.put("reports", jdbc.queryForList("SELECT session_id,status,total_score,summary,strengths,improvements,details_json,generated_at FROM interview_reports WHERE user_id=?", userId));
        data.put("orders", jdbc.queryForList("SELECT id,product_name,credits,amount_cents,currency,status,created_at,paid_at FROM purchase_orders WHERE user_id=?", userId));
        data.put("entitlementLedger", jdbc.queryForList("SELECT operation,amount,reference_id,created_at FROM entitlement_ledger WHERE user_id=? ORDER BY created_at", userId));
        try {
            request.complete("数据副本已生成，7天内可下载", objectMapper.writeValueAsString(data),
                    Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS));
        } catch (Exception exception) {
            throw new IllegalStateException("export serialization failed", exception);
        }
    }

    private void delete(DataRequest request) {
        String userId = request.getUserId();
        jdbc.update("UPDATE interview_messages SET content='[用户已删除]' WHERE session_id IN (SELECT id FROM interview_sessions WHERE user_id=?)", userId);
        jdbc.update("UPDATE interview_reports SET summary='[用户已删除]',strengths=NULL,improvements=NULL,details_json=NULL WHERE user_id=?", userId);
        jdbc.update("UPDATE interview_sessions SET jd_snapshot='[用户已删除]',resume_snapshot='[用户已删除]',question_plan='[]' WHERE user_id=?", userId);
        jdbc.update("UPDATE resume_versions SET content='[用户已删除]',title='已删除',target_role='已删除' WHERE user_id=?", userId);
        jdbc.update("UPDATE resumes SET content='[用户已删除]',title='已删除',target_role='已删除',original_filename=NULL WHERE user_id=?", userId);
        auth.anonymize(userId);
        request.complete("账号已注销，业务内容已去标识化；备份将在到期周期内清除", null, null);
    }

    @Transactional(readOnly = true)
    public List<RequestView> list(String userId) {
        return requests.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    private RequestView view(DataRequest value) {
        return new RequestView(value.getId(), value.getRequestType(), value.getStatus(),
                value.getResultMessage(), value.getAvailableUntil(), value.getCreatedAt(), value.getCompletedAt());
    }

    public record RequestView(String id, String type, String status, String resultMessage,
                              Instant availableUntil, Instant createdAt, Instant completedAt) {}
}
