package com.interview.platform.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class LocalAiGateway implements AiGateway {
    @Override
    public List<String> generateQuestionPlan(String targetRole, String jdText,
                                             String resumeText, int questionCount) {
        String role = targetRole == null || targetRole.isBlank() ? "目标岗位" : targetRole.trim();
        String keyword = extractKeyword(jdText);
        List<String> templates = List.of(
                "请用不超过2分钟做自我介绍，并说明你为什么适合“%s”。".formatted(role),
                "请选择简历中最能体现你能力的项目，用STAR结构说明目标、行动和可量化结果。",
                "目标岗位强调“%s”。请结合真实经历说明你如何使用或理解这项能力。".formatted(keyword),
                "讲一个项目遇到重大阻碍的例子。你如何定位问题、权衡方案并推动解决？",
                "如果重新做一次刚才提到的项目，你会改变什么？为什么？",
                "当业务目标、时间和质量发生冲突时，你如何排序并与相关方达成一致？",
                "请举例说明一次你主动发现问题并推动改进的经历。",
                "面对不熟悉的问题，你会如何快速建立判断并验证方案？",
                "你希望在下一份工作中解决什么类型的问题？这与“%s”有什么关联？".formatted(role),
                "请描述一次你收到负面反馈的经历。你如何判断并转化为后续行动？",
                "讲一个需要跨团队协作但职责边界不清晰的项目，你具体推动了什么？",
                "请举例说明你如何定义成功指标，并根据数据调整执行方案。",
                "遇到方案存在重大风险但团队意见不一致时，你如何表达并推进决策？",
                "请分享一次交付结果未达预期的经历，区分事实、责任和后续改进。",
                "最后请总结你的三项核心优势，并提出一个你想向面试官了解的问题。"
        );
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) questions.add(templates.get(i % templates.size()));
        return questions;
    }

    @Override
    public Evaluation evaluate(String targetRole, List<QuestionAnswer> answers) {
        int answerCount = answers.size();
        int totalLength = answers.stream().mapToInt(value -> value.answer().length()).sum();
        long structured = answers.stream().filter(value -> looksStructured(value.answer())).count();
        int coverage = Math.min(35, answerCount * 7);
        int depth = Math.min(35, totalLength / Math.max(10, answerCount * 8));
        int structure = answerCount == 0 ? 0 : (int) Math.round(structured * 20.0 / answerCount);
        int score = Math.max(40, Math.min(92, 10 + coverage + depth + structure));
        String strengths = structured > 0
                ? "部分回答能够呈现情境、行动与结果，信息结构较清晰。"
                : "能够完成全部作答，并围绕问题给出直接回应。";
        String improvements = totalLength < answerCount * 120
                ? "补充关键背景、个人行动和量化结果；每题优先形成“结论—证据—复盘”的完整闭环。"
                : "进一步压缩背景描述，突出个人决策、权衡依据和结果证据，并主动回应岗位要求。";
        String summary = "本次练习完成%d题。得分仅表示回答与MVP量表的匹配程度，不代表录用概率。针对%s，下一轮应重点强化证据与岗位关联。"
                .formatted(answerCount, targetRole);
        List<QuestionFeedback> questionFeedback = answers.stream()
                .map(this::evaluateQuestion)
                .toList();
        List<DimensionScore> dimensions = aggregateDimensions(questionFeedback);
        List<String> actions = List.of(
                "每题先用一句话给出结论，再按情境、行动、结果补齐证据。",
                "至少补充一个可核验的数字、范围或前后对比；没有数字时明确说明事实边界。",
                "回答结尾主动说明这段经历与目标岗位要求的关联，并给出一次复盘改进。"
        );
        return new Evaluation(score, summary, strengths, improvements, dimensions,
                questionFeedback, actions, answerCount == 0 ? 0.35 : 0.72,
                "local-deterministic-v2", "report-evidence-v2", "report-schema-v2");
    }

    private QuestionFeedback evaluateQuestion(QuestionAnswer value) {
        String answer = value.answer().trim();
        int completeness = Math.min(10, 4 + answer.length() / 45);
        int logic = looksStructured(answer) ? 8 : 5;
        int accuracy = answer.length() >= 40 ? 7 : 5;
        int roleFit = containsEvidence(answer) ? 8 : 6;
        List<DimensionScore> dimensions = List.of(
                new DimensionScore("COMPLETENESS", "内容完整性", completeness,
                        completeness >= 7 ? "回答包含较充分的背景与行动信息" : "背景、个人行动或结果仍不完整"),
                new DimensionScore("LOGIC", "逻辑清晰度", logic,
                        logic >= 7 ? "回答呈现了可识别的结构" : "回答缺少清晰的结论与分层"),
                new DimensionScore("ACCURACY", "专业准确度", accuracy,
                        "当前仅依据回答自洽性评估，专业事实仍需人工复核"),
                new DimensionScore("ROLE_FIT", "岗位匹配度", roleFit,
                        roleFit >= 7 ? "回答给出了结果或验证证据" : "与目标岗位要求的关联不够明确")
        );
        int questionScore = (int) Math.round(dimensions.stream()
                .mapToInt(DimensionScore::score).average().orElse(0) * 10);
        boolean hasStructure = looksStructured(answer);
        boolean hasEvidence = containsEvidence(answer);
        String strength = hasStructure ? "表达有清晰的推进顺序。" : "能够围绕题目给出直接回应。";
        String issue = hasEvidence ? "已有结果证据，但岗位关联和个人决策仍可更具体。"
                : "缺少可核验的结果、范围或前后对比，当前结论证据不足。";
        String suggestion = hasStructure
                ? "保留现有结构，补充你的关键决策、权衡依据和结果指标。"
                : "改用“结论—情境—个人行动—结果—复盘”五句式重新组织。";
        return new QuestionFeedback(value.sequence(), value.question(), answer, questionScore,
                dimensions, excerpt(answer), strength, issue, suggestion);
    }

    private List<DimensionScore> aggregateDimensions(List<QuestionFeedback> feedback) {
        List<String> codes = List.of("COMPLETENESS", "LOGIC", "ACCURACY", "ROLE_FIT");
        List<String> labels = List.of("内容完整性", "逻辑清晰度", "专业准确度", "岗位匹配度");
        List<DimensionScore> result = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            int score = (int) Math.round(feedback.stream()
                    .flatMap(value -> value.dimensions().stream())
                    .filter(value -> code.equals(value.code()))
                    .mapToInt(DimensionScore::score).average().orElse(0));
            result.add(new DimensionScore(code, labels.get(i), score,
                    "基于本次全部回答的量表汇总，满分10分"));
        }
        return result;
    }

    private boolean containsEvidence(String answer) {
        return answer.matches("(?s).*\\d+([.%％]|次|个|人|天|周|月|年)?.*")
                || answer.contains("提升") || answer.contains("降低") || answer.contains("结果");
    }

    private String excerpt(String answer) {
        String compact = answer.replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 120) + "…";
    }

    private boolean looksStructured(String answer) {
        return answer.contains("首先") || answer.contains("其次") || answer.contains("结果")
                || answer.matches("(?s).*\\d+[%％].*") || answer.contains("最后");
    }

    private String extractKeyword(String jdText) {
        if (jdText == null || jdText.isBlank()) return "核心职责";
        String compact = jdText.replaceAll("\\s+", " ").trim();
        return compact.length() <= 24 ? compact : compact.substring(0, 24) + "…";
    }
}
