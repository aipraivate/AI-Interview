package com.interview.platform;

import com.interview.platform.auth.AuthService;
import com.interview.platform.interview.InterviewService;
import com.interview.platform.report.ReportService;
import com.interview.platform.resume.ResumeService;
import com.interview.platform.order.OrderService;
import com.interview.platform.order.PaymentWebhookService;
import com.interview.platform.privacy.PrivacyService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
class MvpFlowIntegrationTests {
    @Autowired
    private AuthService authService;
    @Autowired
    private ResumeService resumeService;
    @Autowired
    private InterviewService interviewService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PrivacyService privacyService;
    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Test
    void guestCanCompleteInterviewAndReceiveReport() throws InterruptedException {
        AuthService.GuestLogin login = authService.loginAsGuest("集成测试用户");
        String userId = login.user().id();
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.user().availableCredits()).isEqualTo(3);

        ResumeService.ResumeView resume = resumeService.create(userId,
                new ResumeService.CreateResume("后端工程师简历", "高级后端工程师",
                        "八年Java服务端经验，主导订单系统架构升级，将接口延迟降低百分之四十，并建设了完整的可观测性体系。"));
        String createKey = UUID.randomUUID().toString();
        InterviewService.InterviewView created = interviewService.create(userId, createKey,
                new InterviewService.CreateInterview(resume.id(),
                        "负责高并发交易系统架构设计、领域建模、性能治理以及跨团队技术项目交付。", 3));
        assertThat(created.status()).isEqualTo("READY");
        InterviewService.InterviewView createReplay = interviewService.create(userId, createKey,
                new InterviewService.CreateInterview(resume.id(),
                        "负责高并发交易系统架构设计、领域建模、性能治理以及跨团队技术项目交付。", 3));
        assertThat(createReplay.id()).isEqualTo(created.id());
        assertThat(authService.me(userId).availableCredits()).isEqualTo(2);

        InterviewService.InterviewView started = interviewService.start(created.id(), userId);
        assertThat(started.status()).isEqualTo("IN_PROGRESS");
        assertThat(started.currentQuestion()).isNotBlank();
        assertThat(interviewService.pause(created.id(), userId).status()).isEqualTo("PAUSED");
        assertThat(interviewService.resume(created.id(), userId).status()).isEqualTo("IN_PROGRESS");

        String firstMessageId = UUID.randomUUID().toString();
        InterviewService.AnswerReceipt first = interviewService.answer(created.id(), userId,
                new InterviewService.AnswerCommand(firstMessageId,
                        "首先我确认业务目标，其次拆解性能瓶颈，最后通过压测验证，结果接口延迟降低40%。"));
        InterviewService.AnswerReceipt replay = interviewService.answer(created.id(), userId,
                new InterviewService.AnswerCommand(firstMessageId, "这次重复请求不应再次推进面试。"));
        assertThat(replay).isEqualTo(first);

        interviewService.answer(created.id(), userId,
                new InterviewService.AnswerCommand(UUID.randomUUID().toString(),
                        "我负责领域拆分和迁移计划，使用灰度方案降低风险，并持续观察核心业务指标。"));
        InterviewService.AnswerReceipt completed = interviewService.answer(created.id(), userId,
                new InterviewService.AnswerCommand(UUID.randomUUID().toString(),
                        "最终项目按期上线，我复盘后补充了容量模型、故障演练和架构决策记录。"));
        assertThat(completed.completed()).isTrue();

        ReportService.ReportView report = waitForReport(created.id(), userId);
        assertThat(report.status()).isEqualTo("READY");
        assertThat(report.totalScore()).isBetween(40, 92);
        assertThat(report.summary()).contains("不代表录用概率");
        assertThat(report.questionFeedback()).hasSize(3);
        assertThat(report.questionFeedback().get(0).evidence()).isNotBlank();
        assertThat(report.dimensions()).hasSize(4);
        assertThat(report.actionItems()).hasSize(3);
        assertThat(report.scoreSchemaVersion()).isEqualTo("report-schema-v2");
        assertThat(authService.me(userId).availableCredits()).isEqualTo(2);

        OrderService.OrderView order = orderService.create(userId, "credits-5", "test-order-1");
        OrderService.OrderView paid = orderService.sandboxPay(userId, order.id(), "sandbox-trade-1");
        orderService.sandboxPay(userId, order.id(), "sandbox-trade-1");
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(authService.me(userId).availableCredits()).isEqualTo(7);
        OrderService.OrderView refunded = orderService.refund(userId, order.id(), "集成测试退款", "refund-1");
        assertThat(refunded.status()).isEqualTo("REFUNDED");
        assertThat(authService.me(userId).availableCredits()).isEqualTo(2);
    }

    @Test
    void uploadedResumeRequiresConfirmationAndEmptyInterviewDoesNotCharge() throws Exception {
        AuthService.GuestLogin login = authService.loginAsGuest("文件解析用户");
        String userId = login.user().id();
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(
                    "五年产品经验，负责用户研究、需求分析、项目推进和数据复盘，主导核心流程改版并提升转化率20%。");
            document.write(output);
            docx = output.toByteArray();
        }
        ResumeService.ResumeView parsed = resumeService.upload(userId,
                new MockMultipartFile("file", "resume.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx));
        assertThat(parsed.status()).isEqualTo("PARSED");
        ResumeService.ResumeView confirmed = resumeService.confirm(parsed.id(), userId,
                new ResumeService.ConfirmResume(parsed.version(), "产品简历", "高级产品经理", parsed.content()));
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");

        InterviewService.InterviewView interview = interviewService.create(userId, UUID.randomUUID().toString(),
                new InterviewService.CreateInterview(confirmed.id(),
                        "负责AI产品规划、用户研究、需求分析、项目落地与商业化数据复盘。", 3));
        interviewService.start(interview.id(), userId);
        interviewService.skip(interview.id(), userId,
                new InterviewService.SkipCommand(UUID.randomUUID().toString()));
        InterviewService.AnswerReceipt finished = interviewService.finish(interview.id(), userId,
                UUID.randomUUID().toString());
        assertThat(finished.status()).isEqualTo("ABORTED");
        assertThat(authService.me(userId).availableCredits()).isEqualTo(3);

        PrivacyService.RequestView export = privacyService.create(userId, "EXPORT", null);
        privacyService.processPending();
        assertThat(privacyService.download(export.id(), userId)).contains("产品简历");
    }

    @Test
    void formalAccountCanRegisterLoginAndRotateRefreshToken() {
        AuthService.GuestLogin guest = authService.loginAsGuest("注册前游客");
        resumeService.create(guest.user().id(), new ResumeService.CreateResume(
                "游客简历", "Java工程师", "五年Java开发经验，负责服务治理、性能优化和稳定性建设。"));
        String email = "member-" + UUID.randomUUID() + "@example.com";
        AuthService.GuestLogin registered = authService.register(new AuthService.RegisterCommand(
                email, "StrongPassword2026", "正式用户", true, true), guest.user().id());
        assertThat(registered.user().email()).isEqualTo(email);
        assertThat(registered.user().id()).isEqualTo(guest.user().id());
        assertThat(resumeService.list(registered.user().id())).hasSize(1);
        assertThat(authService.authenticate(registered.accessToken())).isEqualTo(registered.user().id());

        AuthService.GuestLogin refreshed = authService.refresh(registered.refreshToken());
        assertThat(refreshed.accessToken()).isNotEqualTo(registered.accessToken());
        assertThat(authService.login(new AuthService.LoginCommand(email, "StrongPassword2026"))
                .user().id()).isEqualTo(registered.user().id());
    }

    @Test
    void signedPaymentCallbackIsReplaySafeAndDeleteRevokesAccount() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@example.com";
        AuthService.GuestLogin login = authService.register(new AuthService.RegisterCommand(
                email, "StrongPassword2026", "待注销用户", true, true));
        String userId = login.user().id();

        OrderService.OrderView order = orderService.create(userId, "credits-5", "callback-order-1");
        String rawBody = "{\"eventId\":\"evt-1\",\"orderId\":\"" + order.id()
                + "\",\"providerTradeNo\":\"provider-1\"}";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = paymentSignature(timestamp + "." + rawBody);
        assertThat(paymentWebhookService.handle(timestamp, signature, rawBody).status()).isEqualTo("PAID");
        paymentWebhookService.handle(timestamp, signature, rawBody);
        assertThat(authService.me(userId).availableCredits()).isEqualTo(8);

        resumeService.create(userId, new ResumeService.CreateResume(
                "需要删除的简历", "前端工程师", "五年前端开发经验，负责工程化、性能优化和可访问性建设。"));
        PrivacyService.RequestView deletion = privacyService.create(userId, "DELETE", "StrongPassword2026");
        privacyService.processPending();
        assertThat(privacyService.list(userId).stream()
                .filter(value -> value.id().equals(deletion.id())).findFirst().orElseThrow().status())
                .isEqualTo("COMPLETED");
        assertThat(authService.authenticate(login.accessToken())).isNull();
    }

    private String paymentSignature(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-payment-secret-2026".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private ReportService.ReportView waitForReport(String sessionId, String userId)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            ReportService.ReportView report = reportService.get(sessionId, userId);
            if ("READY".equals(report.status())) return report;
            Thread.sleep(50);
        }
        return fail("报告未在预期时间内生成");
    }
}
