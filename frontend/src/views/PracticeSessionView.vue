<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import {
  practiceApi,
  type PracticeAnswerResult,
  type PracticeReviewItem,
  type PracticeSession,
} from '@/api/practice'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const session = ref<PracticeSession | null>(null)
const result = ref<PracticeAnswerResult | null>(null)
const selected = ref<string[]>([])
const shortAnswer = ref('')
const loading = ref(false)
const error = ref('')
const shareMessage = ref('')
const review = ref<PracticeReviewItem[]>([])
let questionStartedAt = Date.now()

const question = computed(() => session.value?.currentQuestion)
const isMultiple = computed(() => question.value?.type === 'MULTIPLE')
const isShort = computed(() => question.value?.type === 'SHORT_ANSWER')
const isMock = computed(() => session.value?.mode === 'MOCK')
const canSubmit = computed(() => !loading.value && !result.value && (isShort.value
  ? shortAnswer.value.trim().length > 1 : selected.value.length > 0))
const progressPercent = computed(() => session.value
  ? Math.round((session.value.answeredCount / session.value.totalCount) * 100) : 0)
const typeLabels: Record<string, string> = {
  SINGLE: '单选题', MULTIPLE: '多选题', TRUE_FALSE: '判断题', SCENARIO: '情景题', SHORT_ANSWER: '简答题',
}

watch(
  [() => auth.token, () => route.params.sessionId],
  async ([token, sessionId]) => {
    if (!token || !sessionId) return
    await loadSession(String(sessionId))
  },
  { immediate: true },
)

async function loadSession(id = String(route.params.sessionId)) {
  if (!auth.token) return
  loading.value = true
  try {
    session.value = await practiceApi.session(auth.token, id)
    review.value = session.value.status === 'COMPLETED'
      ? await practiceApi.review(auth.token, id)
      : []
    selected.value = []
    shortAnswer.value = ''
    result.value = null
    questionStartedAt = Date.now()
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '练习加载失败'
  } finally { loading.value = false }
}

function selectOption(option: string) {
  if (result.value) return
  if (!isMultiple.value) {
    selected.value = [option]
    return
  }
  selected.value = selected.value.includes(option)
    ? selected.value.filter((value) => value !== option)
    : [...selected.value, option]
}

async function submit() {
  if (!auth.token || !session.value || !question.value || !canSubmit.value) return
  loading.value = true
  error.value = ''
  try {
    result.value = await practiceApi.answer(auth.token, session.value.id, {
      questionId: question.value.id,
      answers: isShort.value ? [shortAnswer.value.trim()] : selected.value,
      durationSeconds: Math.round((Date.now() - questionStartedAt) / 1000),
    })
    session.value = {
      ...session.value,
      answeredCount: result.value.answeredCount,
      currentIndex: result.value.answeredCount,
      score: result.value.score,
      status: result.value.completed ? 'COMPLETED' : 'IN_PROGRESS',
      currentQuestion: result.value.completed ? undefined : session.value.currentQuestion,
    }
    if (result.value.completed) {
      session.value = await practiceApi.session(auth.token, session.value.id)
      review.value = await practiceApi.review(auth.token, session.value.id)
    }
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '答案提交失败'
  } finally { loading.value = false }
}

async function nextQuestion() {
  if (!session.value) return
  await loadSession(session.value.id)
}

async function toggleFavorite() {
  if (!auth.token || !question.value) return
  error.value = ''
  try {
    const response = await practiceApi.favorite(auth.token, question.value.id)
    question.value.favorite = response.favorite
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '收藏更新失败，请重试'
  }
}

async function shareResult() {
  if (!auth.token || !session.value) return
  error.value = ''
  shareMessage.value = ''
  try {
    const share = await practiceApi.createShare(auth.token, session.value.id)
    const url = `${window.location.origin}${share.path}`
    if (navigator.share) {
      try {
        await navigator.share({ title: '我的面试训练成绩', text: `本次训练 ${session.value.score} 分`, url })
        shareMessage.value = '系统分享已完成'
        return
      } catch (exception) {
        if (exception instanceof DOMException && exception.name === 'AbortError') {
          shareMessage.value = '已取消系统分享，成绩链接仍已生成'
          return
        }
      }
    }
    await copyText(url)
    shareMessage.value = `分享链接已复制：${url}`
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '分享失败，请重试'
  }
}

async function copyText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  const input = document.createElement('textarea')
  input.value = value
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  const copied = document.execCommand('copy')
  input.remove()
  if (!copied) throw new Error('Clipboard is unavailable')
}
</script>

<template>
  <main class="app-main practice-page">
    <p v-if="error" class="alert">{{ error }}</p>
    <section v-if="session && session.status === 'COMPLETED'" class="exam-result-card">
      <div class="result-confetti">✦</div>
      <span class="app-kicker">TRAINING COMPLETE</span>
      <h1>本次训练完成</h1>
      <div class="result-score"><strong>{{ session.score }}</strong><span>分</span></div>
      <p>答对 {{ session.correctCount }} / {{ session.totalCount }} 题。每一次错题，都是下一次面试前最明确的训练方向。</p>
      <div class="result-breakdown">
        <span><b>{{ session.totalCount }}</b>总题数</span><span><b>{{ session.correctCount }}</b>答对</span><span><b>{{ session.totalCount - session.correctCount }}</b>待巩固</span>
      </div>
      <div class="result-actions">
        <button class="start-button" @click="router.push('/')">返回训练中心</button>
        <RouterLink class="ghost-link" to="/library?collection=WRONG">查看错题</RouterLink>
        <button class="ghost-button" @click="shareResult">分享成绩</button>
      </div>
      <small v-if="shareMessage">{{ shareMessage }}</small>
      <div v-if="review.length" class="result-review">
        <div class="result-review-heading"><span>逐题复盘</span><b>{{ review.filter((item) => !item.correct).length }} 道待巩固</b></div>
        <article v-for="(item, index) in review" :key="item.questionId" :class="item.correct ? 'correct' : 'wrong'">
          <header><i>{{ item.correct ? '✓' : '!' }}</i><span>第 {{ index + 1 }} 题 · {{ typeLabels[item.type] }}</span><em>{{ item.correct ? '正确' : '错误' }}</em></header>
          <h3>{{ item.stem }}</h3>
          <p><b>你的答案：</b>{{ item.selectedAnswer.join('、') || '未作答' }}</p>
          <p v-if="!item.correct"><b>正确答案：</b>{{ item.correctAnswer.join('、') }}</p>
          <div><b>解析</b><p>{{ item.explanation }}</p></div>
        </article>
      </div>
    </section>

    <section v-else-if="session && question" class="practice-shell">
      <aside class="exam-sidebar">
        <button class="back-button" @click="router.push('/')">← 退出练习</button>
        <div class="exam-progress-copy"><span>{{ isMock ? '模拟考试' : '当前进度' }}</span><strong>{{ session.answeredCount + 1 }}<small>/{{ session.totalCount }}</small></strong></div>
        <div class="exam-progress-bar"><i :style="{ width: `${progressPercent}%` }"></i></div>
        <div class="question-map"><i v-for="index in session.totalCount" :key="index" :class="{ done: index <= session.answeredCount, current: index === session.answeredCount + 1 }">{{ index }}</i></div>
        <div class="exam-tip"><b>{{ isMock ? '考试模式' : '练习模式' }}</b><p>{{ isMock ? '作答过程中不展示答案，完成后统一计分。' : '提交后立即查看答案和解析，错题自动进入错题本。' }}</p></div>
      </aside>

      <article class="practice-question-card">
        <header class="practice-question-header">
          <div><span>{{ question.categoryName }}</span><span>{{ typeLabels[question.type] }}</span><span>{{ question.difficulty }}</span></div>
          <button class="favorite-button large" :class="{ active: question.favorite }" @click="toggleFavorite">{{ question.favorite ? '★ 已收藏' : '☆ 收藏' }}</button>
        </header>
        <h1>{{ question.stem }}</h1>
        <p v-if="isMultiple" class="select-hint">多选题，请选择所有正确选项</p>

        <div v-if="!isShort" class="answer-options">
          <button v-for="(option, index) in question.options" :key="option" :class="{
            selected: selected.includes(option),
            correct: result?.correctAnswer.includes(option),
            wrong: result?.correct === false && selected.includes(option) && !result.correctAnswer.includes(option),
          }" @click="selectOption(option)">
            <i>{{ String.fromCharCode(65 + index) }}</i><span>{{ option }}</span><em v-if="selected.includes(option)">✓</em>
          </button>
        </div>
        <textarea v-else v-model="shortAnswer" class="short-answer" rows="6" maxlength="1000" placeholder="写出关键词或你的完整回答…" :disabled="!!result"></textarea>

        <div v-if="result && !isMock" class="answer-analysis" :class="result.correct ? 'is-correct' : 'is-wrong'">
          <header><i>{{ result.correct ? '✓' : '!' }}</i><div><b>{{ result.correct ? '回答正确' : '回答错误' }}</b><span v-if="!result.correct">正确答案：{{ result.correctAnswer.join('、') }}</span></div></header>
          <div><b>答案解析</b><p>{{ result.explanation }}</p></div>
        </div>
        <div v-else-if="result && isMock" class="answer-analysis mock-saved"><header><i>✓</i><div><b>答案已保存</b><span>考试结束后统一查看成绩</span></div></header></div>

        <footer class="practice-actions">
          <span>{{ question.source }} · {{ question.version }}</span>
          <button v-if="!result" class="start-button" :disabled="!canSubmit" @click="submit">提交答案</button>
          <button v-else-if="!result.completed" class="start-button" @click="nextQuestion">下一题 →</button>
        </footer>
      </article>
    </section>

    <section v-else class="loading-panel"><div class="loader"></div><p>正在准备题目…</p></section>
  </main>
</template>
