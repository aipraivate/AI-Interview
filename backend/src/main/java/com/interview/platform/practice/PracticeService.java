package com.interview.platform.practice;

import com.interview.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PracticeService {
    private static final Set<String> MODES = Set.of(
            "SEQUENTIAL", "RANDOM", "CATEGORY", "MOCK", "WRONG", "FAVORITE");
    private final QuestionCategoryRepository categories;
    private final PracticeQuestionRepository questions;
    private final PracticeSessionRepository sessions;
    private final PracticeAnswerRepository answers;
    private final UserQuestionProgressRepository progress;
    private final QuestionFavoriteRepository favorites;
    private final PracticeShareRepository shares;
    private final ObjectMapper objectMapper;

    public PracticeService(QuestionCategoryRepository categories,
                           PracticeQuestionRepository questions,
                           PracticeSessionRepository sessions,
                           PracticeAnswerRepository answers,
                           UserQuestionProgressRepository progress,
                           QuestionFavoriteRepository favorites,
                           PracticeShareRepository shares,
                           ObjectMapper objectMapper) {
        this.categories = categories;
        this.questions = questions;
        this.sessions = sessions;
        this.answers = answers;
        this.progress = progress;
        this.favorites = favorites;
        this.shares = shares;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(String userId) {
        List<PracticeQuestion> bank = questions.findByEnabledTrueOrderByIdAsc();
        List<UserQuestionProgress> userProgress = progress.findByUserId(userId);
        Map<String, UserQuestionProgress> progressByQuestion = new HashMap<>();
        userProgress.forEach(value -> progressByQuestion.put(value.getQuestionId(), value));
        Set<String> favoriteIds = favoriteIds(userId);
        int attempts = userProgress.stream().mapToInt(UserQuestionProgress::getAttempts).sum();
        int correct = userProgress.stream().mapToInt(UserQuestionProgress::getCorrectCount).sum();
        int wrong = (int) userProgress.stream().filter(value -> !value.isLastCorrect()).count();
        int mastered = (int) userProgress.stream()
                .filter(value -> value.isLastCorrect() && value.getCorrectCount() >= 2).count();
        int accuracy = attempts == 0 ? 0 : (int) Math.round(correct * 100.0 / attempts);
        Map<String, Long> totalByCategory = bank.stream().collect(java.util.stream.Collectors.groupingBy(
                PracticeQuestion::getCategoryId, java.util.stream.Collectors.counting()));
        Map<String, Long> doneByCategory = bank.stream()
                .filter(value -> progressByQuestion.containsKey(value.getId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        PracticeQuestion::getCategoryId, java.util.stream.Collectors.counting()));
        List<CategoryView> categoryViews = categories.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(value -> new CategoryView(value.getCode(), value.getName(), value.getDescription(),
                        value.getIcon(), value.getColor(), totalByCategory.getOrDefault(value.getId(), 0L).intValue(),
                        doneByCategory.getOrDefault(value.getId(), 0L).intValue()))
                .toList();
        List<PracticeSession> history = sessions.findByUserIdOrderByCreatedAtDesc(userId);
        long studyDays = history.stream().map(value -> LocalDate.ofInstant(value.getCreatedAt(), ZoneOffset.UTC))
                .distinct().count();
        return new DashboardView(bank.size(), userProgress.size(), mastered, wrong, favoriteIds.size(),
                attempts, accuracy, (int) studyDays, categoryViews,
                history.stream().limit(5).map(this::summary).toList());
    }

    @Transactional(readOnly = true)
    public List<QuestionView> library(String userId, String categoryCode, String type, String collection) {
        Map<String, QuestionCategory> categoryMap = categoryMap();
        Map<String, UserQuestionProgress> userProgress = new HashMap<>();
        progress.findByUserId(userId).forEach(value -> userProgress.put(value.getQuestionId(), value));
        Set<String> favoriteIds = favoriteIds(userId);
        return questions.findByEnabledTrueOrderByIdAsc().stream()
                .filter(value -> categoryCode == null || categoryCode.isBlank()
                        || categoryCode.equals(categoryMap.get(value.getCategoryId()).getCode()))
                .filter(value -> type == null || type.isBlank()
                        || type.equalsIgnoreCase(value.getQuestionType()))
                .filter(value -> matchesCollection(value.getId(), collection, userProgress, favoriteIds))
                .map(value -> questionView(value, categoryMap.get(value.getCategoryId()),
                        favoriteIds.contains(value.getId()), userProgress.get(value.getId())))
                .toList();
    }

    @Transactional
    public SessionView createSession(String userId, CreateSessionCommand command) {
        String mode = normalizeMode(command.mode());
        Map<String, QuestionCategory> categoryMap = categoryMap();
        List<PracticeQuestion> bank = new ArrayList<>(questions.findByEnabledTrueOrderByIdAsc());
        String categoryCode = command.categoryCode() == null ? null : command.categoryCode().trim();
        if (categoryCode != null && !categoryCode.isBlank()) {
            bank.removeIf(value -> !categoryCode.equals(categoryMap.get(value.getCategoryId()).getCode()));
        }
        if ("WRONG".equals(mode)) {
            Set<String> wrongIds = progress.findByUserId(userId).stream()
                    .filter(value -> !value.isLastCorrect()).map(UserQuestionProgress::getQuestionId)
                    .collect(java.util.stream.Collectors.toSet());
            bank.removeIf(value -> !wrongIds.contains(value.getId()));
        }
        if ("FAVORITE".equals(mode)) {
            Set<String> ids = favoriteIds(userId);
            bank.removeIf(value -> !ids.contains(value.getId()));
        }
        if (bank.isEmpty()) {
            throw new BusinessException("NO_PRACTICE_QUESTIONS", "当前条件下还没有可练习的题目",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!"SEQUENTIAL".equals(mode)) Collections.shuffle(bank);
        int requested = command.questionCount() == null ? ("MOCK".equals(mode) ? 10 : 5)
                : Math.max(1, Math.min(50, command.questionCount()));
        List<String> selected = bank.stream().limit(Math.min(requested, bank.size()))
                .map(PracticeQuestion::getId).toList();
        PracticeSession session = sessions.save(new PracticeSession(userId, mode, categoryCode,
                writeJson(selected), selected.size()));
        return sessionView(session, userId);
    }

    @Transactional(readOnly = true)
    public SessionView getSession(String sessionId, String userId) {
        return sessionView(ownedSession(sessionId, userId), userId);
    }

    @Transactional(readOnly = true)
    public List<ReviewItem> review(String sessionId, String userId) {
        PracticeSession session = ownedSession(sessionId, userId);
        if (!"COMPLETED".equals(session.getStatus())) {
            throw BusinessException.conflict("PRACTICE_NOT_COMPLETED", "完成练习后才能查看完整解析");
        }
        Map<String, PracticeAnswer> answerByQuestion = new HashMap<>();
        answers.findBySessionIdOrderByAnsweredAtAsc(sessionId)
                .forEach(value -> answerByQuestion.put(value.getQuestionId(), value));
        return readList(session.getQuestionIdsJson()).stream().map(questionId -> {
            PracticeQuestion question = questions.findById(questionId).orElseThrow();
            PracticeAnswer answer = answerByQuestion.get(questionId);
            if (answer == null) throw new IllegalStateException("Completed practice is missing an answer");
            return new ReviewItem(question.getId(), question.getQuestionType(), question.getStem(),
                    readList(question.getOptionsJson()), readList(answer.getSelectedAnswerJson()),
                    readList(question.getCorrectAnswerJson()), answer.isCorrect(), question.getExplanation(),
                    answer.getDurationSeconds());
        }).toList();
    }

    @Transactional
    public AnswerResult answer(String sessionId, String userId, AnswerCommand command) {
        PracticeSession session = ownedSession(sessionId, userId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw BusinessException.conflict("PRACTICE_COMPLETED", "本次练习已经完成");
        }
        List<String> questionIds = readList(session.getQuestionIdsJson());
        String expectedQuestionId = questionIds.get(session.getCurrentIndex());
        if (!expectedQuestionId.equals(command.questionId())) {
            throw BusinessException.conflict("QUESTION_ORDER_CONFLICT", "题目进度已变化，请刷新后继续");
        }
        if (answers.existsBySessionIdAndQuestionId(sessionId, command.questionId())) {
            throw BusinessException.conflict("QUESTION_ALREADY_ANSWERED", "本题已经作答");
        }
        PracticeQuestion question = questions.findById(command.questionId())
                .orElseThrow(() -> BusinessException.notFound("题目不存在"));
        List<String> selected = normalizeAnswers(command.answers());
        boolean correct = evaluate(question, selected);
        String selectedJson = writeJson(selected);
        answers.save(new PracticeAnswer(sessionId, userId, question.getId(), selectedJson, correct,
                Math.max(0, Math.min(3600, command.durationSeconds()))));
        UserQuestionProgress value = progress.findByUserIdAndQuestionId(userId, question.getId())
                .orElseGet(() -> new UserQuestionProgress(userId, question.getId()));
        value.record(correct, selectedJson);
        progress.save(value);
        session.record(correct);
        boolean reveal = !"MOCK".equals(session.getMode());
        QuestionView next = "COMPLETED".equals(session.getStatus()) ? null
                : questionView(questions.findById(questionIds.get(session.getCurrentIndex())).orElseThrow(),
                        categoryMap().get(questions.findById(questionIds.get(session.getCurrentIndex()))
                                .orElseThrow().getCategoryId()),
                        favoriteIds(userId).contains(questionIds.get(session.getCurrentIndex())), null);
        return new AnswerResult(reveal ? correct : null,
                reveal ? readList(question.getCorrectAnswerJson()) : List.of(),
                reveal ? question.getExplanation() : null, session.getAnsweredCount(), session.getTotalCount(),
                "COMPLETED".equals(session.getStatus()), score(session), next);
    }

    @Transactional
    public FavoriteView toggleFavorite(String questionId, String userId) {
        if (!questions.existsById(questionId)) throw BusinessException.notFound("题目不存在");
        var existing = favorites.findByUserIdAndQuestionId(userId, questionId);
        if (existing.isPresent()) {
            favorites.delete(existing.get());
            return new FavoriteView(questionId, false);
        }
        favorites.save(new QuestionFavorite(userId, questionId));
        return new FavoriteView(questionId, true);
    }

    @Transactional
    public ShareCreated createShare(String sessionId, String userId) {
        PracticeSession session = ownedSession(sessionId, userId);
        if (!"COMPLETED".equals(session.getStatus())) {
            throw BusinessException.conflict("PRACTICE_NOT_COMPLETED", "完成练习后才能分享成绩");
        }
        PracticeShare share = shares.findBySessionIdAndUserId(sessionId, userId)
                .orElseGet(() -> {
                    SharePayload payload = new SharePayload(session.getMode(), session.getCategoryCode(),
                            session.getTotalCount(), session.getCorrectCount(), score(session));
                    return shares.save(new PracticeShare(userId, sessionId,
                            "我完成了一次面试能力训练", writeJson(payload)));
                });
        return new ShareCreated(share.getShareToken(), "/share/" + share.getShareToken(),
                share.getExpiresAt());
    }

    @Transactional
    public ShareView getShare(String token) {
        PracticeShare share = shares.findByShareToken(token)
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessException("SHARE_NOT_FOUND", "分享不存在或已过期",
                        HttpStatus.NOT_FOUND));
        share.viewed();
        SharePayload payload = readJson(share.getPayloadJson(), SharePayload.class);
        return new ShareView(share.getTitle(), payload.mode(), payload.categoryCode(), payload.totalCount(),
                payload.correctCount(), payload.score(), share.getViewCount(), share.getCreatedAt());
    }

    private boolean matchesCollection(String questionId, String collection,
                                      Map<String, UserQuestionProgress> progressByQuestion,
                                      Set<String> favoriteIds) {
        if (collection == null || collection.isBlank() || "ALL".equalsIgnoreCase(collection)) return true;
        return switch (collection.toUpperCase(Locale.ROOT)) {
            case "WRONG" -> progressByQuestion.containsKey(questionId)
                    && !progressByQuestion.get(questionId).isLastCorrect();
            case "FAVORITE" -> favoriteIds.contains(questionId);
            case "DONE" -> progressByQuestion.containsKey(questionId);
            default -> true;
        };
    }

    private SessionView sessionView(PracticeSession session, String userId) {
        List<String> ids = readList(session.getQuestionIdsJson());
        QuestionView current = null;
        if ("IN_PROGRESS".equals(session.getStatus())) {
            PracticeQuestion question = questions.findById(ids.get(session.getCurrentIndex())).orElseThrow();
            current = questionView(question, categoryMap().get(question.getCategoryId()),
                    favoriteIds(userId).contains(question.getId()), null);
        }
        return new SessionView(session.getId(), session.getMode(), session.getCategoryCode(), session.getStatus(),
                session.getTotalCount(), session.getCurrentIndex(), session.getAnsweredCount(),
                session.getCorrectCount(), score(session), current, session.getCreatedAt(), session.getCompletedAt());
    }

    private SessionSummary summary(PracticeSession value) {
        return new SessionSummary(value.getId(), value.getMode(), value.getCategoryCode(), value.getStatus(),
                value.getAnsweredCount(), value.getTotalCount(), score(value), value.getCreatedAt());
    }

    private QuestionView questionView(PracticeQuestion question, QuestionCategory category,
                                      boolean favorite, UserQuestionProgress value) {
        return new QuestionView(question.getId(), category.getCode(), category.getName(), question.getQuestionType(),
                question.getDifficulty(), question.getStem(), readList(question.getOptionsJson()),
                readList(question.getTagsJson()), question.getSource(), question.getVersion(), favorite,
                value != null, value == null ? null : value.isLastCorrect());
    }

    private Map<String, QuestionCategory> categoryMap() {
        Map<String, QuestionCategory> result = new HashMap<>();
        categories.findByEnabledTrueOrderBySortOrderAsc().forEach(value -> result.put(value.getId(), value));
        return result;
    }

    private Set<String> favoriteIds(String userId) {
        return favorites.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(QuestionFavorite::getQuestionId).collect(java.util.stream.Collectors.toSet());
    }

    private PracticeSession ownedSession(String sessionId, String userId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("练习记录不存在"));
    }

    private boolean evaluate(PracticeQuestion question, List<String> selected) {
        List<String> correct = normalizeAnswers(readList(question.getCorrectAnswerJson()));
        if ("SHORT_ANSWER".equals(question.getQuestionType())) {
            String text = String.join(" ", selected).toLowerCase(Locale.ROOT);
            return !text.isBlank() && correct.stream()
                    .allMatch(value -> text.contains(value.toLowerCase(Locale.ROOT)));
        }
        return new LinkedHashSet<>(selected).equals(new LinkedHashSet<>(correct));
    }

    private List<String> normalizeAnswers(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    private int score(PracticeSession value) {
        return value.getAnsweredCount() == 0 ? 0
                : (int) Math.round(value.getCorrectCount() * 100.0 / value.getAnsweredCount());
    }

    private String normalizeMode(String value) {
        String result = value == null ? "SEQUENTIAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(result)) {
            throw new BusinessException("INVALID_PRACTICE_MODE", "不支持的练习模式", HttpStatus.BAD_REQUEST);
        }
        return result;
    }

    private List<String> readList(String json) {
        return readJson(json, new TypeReference<List<String>>() {});
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to write practice data", exception); }
    }

    private <T> T readJson(String json, Class<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception exception) { throw new IllegalStateException("Unable to read practice data", exception); }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception exception) { throw new IllegalStateException("Unable to read practice data", exception); }
    }

    public record CreateSessionCommand(String mode, String categoryCode, Integer questionCount) {}
    public record AnswerCommand(String questionId, List<String> answers, int durationSeconds) {}
    public record CategoryView(String code, String name, String description, String icon, String color,
                               int totalCount, int completedCount) {}
    public record DashboardView(int totalQuestions, int attemptedQuestions, int masteredQuestions,
                                int wrongQuestions, int favoriteQuestions, int totalAttempts, int accuracy,
                                int studyDays, List<CategoryView> categories,
                                List<SessionSummary> recentSessions) {}
    public record QuestionView(String id, String categoryCode, String categoryName, String type,
                               String difficulty, String stem, List<String> options, List<String> tags,
                               String source, String version, boolean favorite, boolean answered,
                               Boolean lastCorrect) {}
    public record SessionView(String id, String mode, String categoryCode, String status, int totalCount,
                              int currentIndex, int answeredCount, int correctCount, int score,
                              QuestionView currentQuestion, Instant createdAt, Instant completedAt) {}
    public record SessionSummary(String id, String mode, String categoryCode, String status,
                                 int answeredCount, int totalCount, int score, Instant createdAt) {}
    public record AnswerResult(Boolean correct, List<String> correctAnswer, String explanation,
                               int answeredCount, int totalCount, boolean completed, int score,
                               QuestionView nextQuestion) {}
    public record ReviewItem(String questionId, String type, String stem, List<String> options,
                             List<String> selectedAnswer, List<String> correctAnswer, boolean correct,
                             String explanation, int durationSeconds) {}
    public record FavoriteView(String questionId, boolean favorite) {}
    private record SharePayload(String mode, String categoryCode, int totalCount, int correctCount, int score) {}
    public record ShareCreated(String token, String path, Instant expiresAt) {}
    public record ShareView(String title, String mode, String categoryCode, int totalCount, int correctCount,
                            int score, int viewCount, Instant createdAt) {}
}
