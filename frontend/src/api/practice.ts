import { apiRequest } from './client'

export interface PracticeCategory {
  code: string
  name: string
  description: string
  icon: string
  color: string
  totalCount: number
  completedCount: number
}

export interface SessionSummary {
  id: string
  mode: string
  categoryCode?: string
  status: string
  answeredCount: number
  totalCount: number
  score: number
  createdAt: string
}

export interface PracticeDashboard {
  totalQuestions: number
  attemptedQuestions: number
  masteredQuestions: number
  wrongQuestions: number
  favoriteQuestions: number
  totalAttempts: number
  accuracy: number
  studyDays: number
  categories: PracticeCategory[]
  recentSessions: SessionSummary[]
}

export interface PracticeQuestion {
  id: string
  categoryCode: string
  categoryName: string
  type: 'SINGLE' | 'MULTIPLE' | 'TRUE_FALSE' | 'SCENARIO' | 'SHORT_ANSWER'
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  stem: string
  options: string[]
  tags: string[]
  source: string
  version: string
  favorite: boolean
  answered: boolean
  lastCorrect?: boolean
}

export interface PracticeSession {
  id: string
  mode: string
  categoryCode?: string
  status: 'IN_PROGRESS' | 'COMPLETED'
  totalCount: number
  currentIndex: number
  answeredCount: number
  correctCount: number
  score: number
  currentQuestion?: PracticeQuestion
  createdAt: string
  completedAt?: string
}

export interface PracticeAnswerResult {
  correct?: boolean
  correctAnswer: string[]
  explanation?: string
  answeredCount: number
  totalCount: number
  completed: boolean
  score: number
  nextQuestion?: PracticeQuestion
}

export interface PracticeReviewItem {
  questionId: string
  type: PracticeQuestion['type']
  stem: string
  options: string[]
  selectedAnswer: string[]
  correctAnswer: string[]
  correct: boolean
  explanation: string
  durationSeconds: number
}

export interface PracticeShare {
  title: string
  mode: string
  categoryCode?: string
  totalCount: number
  correctCount: number
  score: number
  viewCount: number
  createdAt: string
}

export const practiceApi = {
  dashboard: (token: string) =>
    apiRequest<PracticeDashboard>('/api/v1/practice/dashboard', {}, token),
  questions: (
    token: string,
    filters: { category?: string; type?: string; collection?: string } = {},
  ) => {
    const params = new URLSearchParams()
    if (filters.category) params.set('category', filters.category)
    if (filters.type) params.set('type', filters.type)
    if (filters.collection) params.set('collection', filters.collection)
    return apiRequest<PracticeQuestion[]>(`/api/v1/practice/questions?${params}`, {}, token)
  },
  createSession: (
    token: string,
    payload: { mode: string; categoryCode?: string; questionCount?: number },
  ) =>
    apiRequest<PracticeSession>(
      '/api/v1/practice/sessions',
      { method: 'POST', body: JSON.stringify(payload) },
      token,
    ),
  session: (token: string, id: string) =>
    apiRequest<PracticeSession>(`/api/v1/practice/sessions/${id}`, {}, token),
  review: (token: string, id: string) =>
    apiRequest<PracticeReviewItem[]>(`/api/v1/practice/sessions/${id}/review`, {}, token),
  answer: (
    token: string,
    sessionId: string,
    payload: { questionId: string; answers: string[]; durationSeconds: number },
  ) =>
    apiRequest<PracticeAnswerResult>(
      `/api/v1/practice/sessions/${sessionId}/answers`,
      { method: 'POST', body: JSON.stringify(payload) },
      token,
    ),
  favorite: (token: string, questionId: string) =>
    apiRequest<{ questionId: string; favorite: boolean }>(
      `/api/v1/practice/questions/${questionId}/favorite`,
      { method: 'POST' },
      token,
    ),
  createShare: (token: string, sessionId: string) =>
    apiRequest<{ token: string; path: string; expiresAt: string }>(
      `/api/v1/practice/sessions/${sessionId}/share`,
      { method: 'POST' },
      token,
    ),
  share: (token: string) => apiRequest<PracticeShare>(`/api/v1/shares/${token}`),
}
