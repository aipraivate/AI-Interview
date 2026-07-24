package com.interview.platform.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Primary
class RoutingAiGateway implements AiGateway {
    private static final Logger log = LoggerFactory.getLogger(RoutingAiGateway.class);
    private final LocalAiGateway fallback;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String apiKey;
    private final String model;

    RoutingAiGateway(LocalAiGateway fallback, ObjectMapper objectMapper,
                     @Value("${app.ai.provider:local}") String provider,
                     @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                     @Value("${app.ai.api-key:}") String apiKey,
                     @Value("${app.ai.model:gpt-4.1-mini}") String model) {
        this.fallback = fallback;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public List<String> generateQuestionPlan(String targetRole, String jdText,
                                             String resumeText, int questionCount) {
        if (!remoteEnabled()) return fallback.generateQuestionPlan(targetRole, jdText, resumeText, questionCount);
        String prompt = """
                scene_code=interview.question_plan; prompt_version=v1; schema_version=v1.
                你是资深面试官。基于目标岗位、JD和简历事实生成结构化题目，不得虚构候选人经历，
                不得询问年龄、婚育、健康、宗教等不必要敏感信息。只返回JSON：
                {"questions":["题目1","题目2"]}，题目数量必须为%d。
                目标岗位：%s
                JD：%s
                简历：%s
                """.formatted(questionCount, targetRole, jdText, resumeText);
        try {
            JsonNode json = call(prompt);
            List<String> questions = new ArrayList<>();
            json.path("questions").forEach(value -> {
                String question = value.asText().trim();
                if (!question.isBlank() && question.length() <= 300) questions.add(question);
            });
            if (questions.size() != questionCount || questions.stream().distinct().count() != questionCount) {
                throw new IllegalStateException("invalid or duplicated question set");
            }
            return questions;
        } catch (RuntimeException exception) {
            log.warn("AI question generation degraded to local provider: {}", exception.getClass().getSimpleName());
            return fallback.generateQuestionPlan(targetRole, jdText, resumeText, questionCount);
        }
    }

    @Override
    public Evaluation evaluate(String targetRole, List<QuestionAnswer> answers) {
        if (!remoteEnabled()) return fallback.evaluate(targetRole, answers);
        String prompt = """
                scene_code=interview.report; prompt_version=v1; schema_version=v1.
                依据候选人的原始回答生成训练复盘。结论必须有回答证据，不得输出录用概率、人格、心理或健康判断。
                只返回JSON：{"totalScore":0,"summary":"...","strengths":"...","improvements":"..."}。
                totalScore范围40到92，summary必须明确分数仅表示训练量表匹配度、不代表录用概率。
                目标岗位：%s
                问题与回答：%s
                """.formatted(targetRole, answers);
        try {
            Evaluation evidenceBaseline = fallback.evaluate(targetRole, answers);
            JsonNode json = call(prompt);
            int score = json.path("totalScore").asInt(-1);
            String summary = requiredText(json, "summary");
            String strengths = requiredText(json, "strengths");
            String improvements = requiredText(json, "improvements");
            if (score < 40 || score > 92 || !summary.contains("不代表录用概率")) {
                throw new IllegalStateException("invalid evaluation schema");
            }
            return new Evaluation(score, summary, strengths, improvements,
                    evidenceBaseline.dimensions(), evidenceBaseline.questionFeedback(),
                    evidenceBaseline.actionItems(), evidenceBaseline.confidence(),
                    model, "report-evidence-v2", "report-schema-v2");
        } catch (RuntimeException exception) {
            log.warn("AI report generation degraded to local provider: {}", exception.getClass().getSimpleName());
            return fallback.evaluate(targetRole, answers);
        }
    }

    private JsonNode call(String prompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", "Return valid JSON only."),
                        Map.of("role", "user", "content", prompt)));
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) throw new IllegalStateException("empty provider response");
        String content = response.at("/choices/0/message/content").asText();
        try {
            return objectMapper.readTree(stripCodeFence(content));
        } catch (Exception exception) {
            throw new IllegalStateException("provider response is not valid JSON", exception);
        }
    }

    private String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        return firstLine > 0 && lastFence > firstLine
                ? trimmed.substring(firstLine + 1, lastFence).trim() : trimmed;
    }

    private String requiredText(JsonNode value, String field) {
        String text = value.path(field).asText().trim();
        if (text.isBlank() || text.length() > 4000) throw new IllegalStateException("invalid " + field);
        return text;
    }

    private boolean remoteEnabled() {
        return "openai-compatible".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }
}
