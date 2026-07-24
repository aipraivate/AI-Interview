package com.interview.platform.ai;

import java.util.List;

public interface AiGateway {
    List<String> generateQuestionPlan(String targetRole, String jdText, String resumeText, int questionCount);
    default List<QuestionSpec> generateQuestionSet(String targetRole, String jdText,
                                                   String resumeText, int questionCount) {
        List<String> plan = generateQuestionPlan(targetRole, jdText, resumeText, questionCount);
        return java.util.stream.IntStream.range(0, plan.size()).mapToObj(index -> {
            String type = index == 0 ? "INTRODUCTION" : index % 4 == 1 ? "PROJECT"
                    : index % 4 == 2 ? "PROFESSIONAL" : "BEHAVIORAL";
            String difficulty = index < 3 ? "JUNIOR" : index < 7 ? "INTERMEDIATE" : "SENIOR";
            return new QuestionSpec(index + 1, plan.get(index), type, difficulty,
                    List.of("事实证据", "个人行动", "结果复盘"),
                    index == 0 ? "CURATED" : "JD_CUSTOM", Integer.toHexString(plan.get(index).hashCode()));
        }).toList();
    }
    Evaluation evaluate(String targetRole, List<QuestionAnswer> answers);

    record QuestionAnswer(int sequence, String question, String answer) {}
    record QuestionSpec(int sequence, String content, String type, String difficulty,
                        List<String> keyPoints, String sourceType, String fingerprint) {}
    record DimensionScore(String code, String label, int score, String rationale) {}
    record QuestionFeedback(int sequence, String question, String answer, int score,
                            List<DimensionScore> dimensions, String evidence,
                            String strength, String issue, String suggestion) {}
    record Evaluation(int totalScore, String summary, String strengths, String improvements,
                      List<DimensionScore> dimensions, List<QuestionFeedback> questionFeedback,
                      List<String> actionItems, double confidence, String modelVersion,
                      String promptVersion, String schemaVersion) {}
}
