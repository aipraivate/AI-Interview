import { apiDownload, apiRequest } from './client'

export interface User {
  id: string
  nickname: string
  availableCredits: number
  email?: string
  memberLevel: string
}

export interface GuestLogin {
  accessToken: string
  expiresAt: string
  refreshToken: string
  refreshExpiresAt: string
  user: User
}

export interface Product { id: string; name: string; credits: number; amountCents: number; currency: string }
export interface Order { id: string; productId: string; productName: string; credits: number; amountCents: number; currency: string; status: string }
export interface Entitlement { availableCredits: number; reservedCredits: number }
export interface LedgerEntry { id: string; operation: string; amount: number; referenceId?: string; createdAt: string }
export interface DataRequest { id: string; type: string; status: string; resultMessage?: string; availableUntil?: string; createdAt: string }

export interface Resume {
  id: string
  title: string
  targetRole: string
  content: string
  status: 'PARSED' | 'CONFIRMED'
  sourceType: 'MANUAL' | 'UPLOAD'
  originalFilename?: string
  parseConfidence?: number
  confirmedAt?: string
  isDefault: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface Interview {
  id: string
  resumeId: string
  targetRole: string
  status: string
  questionCount: number
  currentQuestionIndex: number
  currentQuestion?: string
  questionPlan: QuestionSpec[]
  createdAt: string
  startedAt?: string
  completedAt?: string
}
export interface QuestionSpec { sequence: number; content: string; type: string; difficulty: string; keyPoints: string[]; sourceType: string; fingerprint: string }

export interface AnswerReceipt {
  sessionId: string
  status: string
  answeredCount: number
  questionCount: number
  nextQuestion?: string
  completed: boolean
}

export interface Report {
  id: string
  sessionId: string
  status: 'PENDING' | 'READY' | 'FAILED'
  totalScore?: number
  summary?: string
  strengths?: string
  improvements?: string
  rubricVersion: string
  scoreSchemaVersion: string
  confidence?: number
  modelVersion?: string
  promptVersion?: string
  dimensions: DimensionScore[]
  questionFeedback: QuestionFeedback[]
  actionItems: string[]
  previousScore?: number
  scoreDelta?: number
  generatedAt?: string
}

export interface DimensionScore {
  code: string
  label: string
  score: number
  rationale: string
}

export interface QuestionFeedback {
  sequence: number
  question: string
  answer: string
  score: number
  dimensions: DimensionScore[]
  evidence: string
  strength: string
  issue: string
  suggestion: string
}
export interface JdAnalysis { positionTitle: string; roleFamily: string; responsibilities: string[]; coreSkills: string[]; requirements: string[]; confidence: number; parserVersion: string; normalizedText: string }

export const interviewApi = {
  guest: (nickname = '体验用户') =>
    apiRequest<GuestLogin>('/api/v1/auth/guest', {
      method: 'POST',
      body: JSON.stringify({ nickname }),
    }),
  register: (payload: { email: string; password: string; nickname: string; acceptTerms: boolean; acceptPrivacy: boolean }, token?: string) =>
    apiRequest<GuestLogin>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(payload) }, token),
  login: (payload: { email: string; password: string }) =>
    apiRequest<GuestLogin>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  refresh: (refreshToken: string) =>
    apiRequest<GuestLogin>('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
  logout: (refreshToken: string) =>
    apiRequest<void>('/api/v1/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
  me: (token: string) => apiRequest<User>('/api/v1/auth/me', {}, token),
  createResume: (token: string, payload: { title: string; targetRole: string; content: string }) =>
    apiRequest<Resume>('/api/v1/resumes', { method: 'POST', body: JSON.stringify(payload) }, token),
  uploadResume: (token: string, file: File) => {
    const body = new FormData()
    body.set('file', file)
    return apiRequest<Resume>('/api/v1/resumes/upload', { method: 'POST', body }, token)
  },
  confirmResume: (token: string, resumeId: string, payload: { version: number; title: string; targetRole: string; content: string }) =>
    apiRequest<Resume>(`/api/v1/resumes/${resumeId}/confirm`, { method: 'POST', body: JSON.stringify(payload) }, token),
  listResumes: (token: string) => apiRequest<Resume[]>('/api/v1/resumes', {}, token),
  analyzeJd: (token: string, jdText: string) => apiRequest<JdAnalysis>('/api/v1/jd/analyze', {
    method: 'POST', body: JSON.stringify({ jdText }),
  }, token),
  createInterview: (
    token: string,
    payload: { resumeId: string; jdText: string; questionCount: number },
    idempotencyKey: string,
  ) =>
    apiRequest<Interview>(
      '/api/v1/interviews',
      {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(payload),
      },
      token,
    ),
  listInterviews: (token: string) => apiRequest<Interview[]>('/api/v1/interviews', {}, token),
  startInterview: (token: string, sessionId: string) =>
    apiRequest<Interview>(`/api/v1/interviews/${sessionId}/start`, { method: 'POST' }, token),
  pauseInterview: (token: string, sessionId: string) =>
    apiRequest<Interview>(`/api/v1/interviews/${sessionId}/pause`, { method: 'POST' }, token),
  resumeInterview: (token: string, sessionId: string) =>
    apiRequest<Interview>(`/api/v1/interviews/${sessionId}/resume`, { method: 'POST' }, token),
  answer: (token: string, sessionId: string, answer: string, clientMessageId: string) =>
    apiRequest<AnswerReceipt>(
      `/api/v1/interviews/${sessionId}/answers`,
      { method: 'POST', body: JSON.stringify({ answer, clientMessageId }) },
      token,
    ),
  skip: (token: string, sessionId: string, clientMessageId: string) =>
    apiRequest<AnswerReceipt>(`/api/v1/interviews/${sessionId}/skip`, {
      method: 'POST', body: JSON.stringify({ clientMessageId }),
    }, token),
  finish: (token: string, sessionId: string) =>
    apiRequest<AnswerReceipt>(`/api/v1/interviews/${sessionId}/finish`, {
      method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() },
    }, token),
  getInterview: (token: string, sessionId: string) =>
    apiRequest<Interview>(`/api/v1/interviews/${sessionId}`, {}, token),
  getReport: (token: string, sessionId: string) =>
    apiRequest<Report>(`/api/v1/interviews/${sessionId}/report`, {}, token),
  retryReport: (token: string, sessionId: string) =>
    apiRequest<Report>(`/api/v1/interviews/${sessionId}/report/retry`, { method: 'POST' }, token),
  reportFeedback: (token: string, sessionId: string, helpful: boolean, reason?: string) =>
    apiRequest<{ helpful: boolean }>(`/api/v1/interviews/${sessionId}/report/feedback`, {
      method: 'POST', body: JSON.stringify({ helpful, reason }),
    }, token),
  products: (token: string) => apiRequest<Product[]>('/api/v1/orders/products', {}, token),
  createOrder: (token: string, productId: string) =>
    apiRequest<Order>('/api/v1/orders', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ productId }) }, token),
  sandboxPay: (token: string, orderId: string) =>
    apiRequest<Order>(`/api/v1/orders/${orderId}/sandbox-pay`, { method: 'POST', body: JSON.stringify({ providerTradeNo: `web-${crypto.randomUUID()}` }) }, token),
  entitlement: (token: string) => apiRequest<Entitlement>('/api/v1/entitlements', {}, token),
  entitlementLedger: (token: string) => apiRequest<LedgerEntry[]>('/api/v1/entitlements/ledger', {}, token),
  createDataRequest: (token: string, type: 'EXPORT' | 'DELETE', password?: string) =>
    apiRequest<{ id: string; status: string }>('/api/v1/privacy/requests', { method: 'POST', body: JSON.stringify({ type, password }) }, token),
  dataRequests: (token: string) => apiRequest<DataRequest[]>('/api/v1/privacy/requests', {}, token),
  downloadDataExport: (token: string, requestId: string) =>
    apiDownload(`/api/v1/privacy/requests/${requestId}/download`, token),
  analytics: (token: string, eventName: string, properties: Record<string, string> = {}) =>
    apiRequest<void>('/api/v1/analytics/events', { method: 'POST', body: JSON.stringify({ eventName, properties }) }, token),
}
