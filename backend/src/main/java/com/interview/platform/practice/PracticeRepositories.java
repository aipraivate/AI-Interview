package com.interview.platform.practice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, String> {
    List<QuestionCategory> findByEnabledTrueOrderBySortOrderAsc();
    Optional<QuestionCategory> findByCodeAndEnabledTrue(String code);
}

interface PracticeQuestionRepository extends JpaRepository<PracticeQuestion, String> {
    List<PracticeQuestion> findByEnabledTrueOrderByIdAsc();
}

interface PracticeSessionRepository extends JpaRepository<PracticeSession, String> {
    Optional<PracticeSession> findByIdAndUserId(String id, String userId);
    List<PracticeSession> findByUserIdOrderByCreatedAtDesc(String userId);
    List<PracticeSession> findTop10ByUserIdOrderByCreatedAtDesc(String userId);
}

interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, String> {
    boolean existsBySessionIdAndQuestionId(String sessionId, String questionId);
    List<PracticeAnswer> findBySessionIdOrderByAnsweredAtAsc(String sessionId);
}

interface UserQuestionProgressRepository extends JpaRepository<UserQuestionProgress, String> {
    Optional<UserQuestionProgress> findByUserIdAndQuestionId(String userId, String questionId);
    List<UserQuestionProgress> findByUserId(String userId);
}

interface QuestionFavoriteRepository extends JpaRepository<QuestionFavorite, String> {
    Optional<QuestionFavorite> findByUserIdAndQuestionId(String userId, String questionId);
    List<QuestionFavorite> findByUserIdOrderByCreatedAtDesc(String userId);
}

interface PracticeShareRepository extends JpaRepository<PracticeShare, String> {
    Optional<PracticeShare> findByShareToken(String token);
    Optional<PracticeShare> findBySessionIdAndUserId(String sessionId, String userId);
}
