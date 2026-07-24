<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { interviewApi, type AnswerReceipt, type Interview, type JdAnalysis, type Report, type Resume } from '@/api/interview'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const step = ref<'setup' | 'ready' | 'interview' | 'report'>('setup')
const loading = ref(false)
const error = ref('')
const answer = ref('')
const active = ref<Interview | null>(null)
const report = ref<Report | null>(null)
const history = ref<Interview[]>([])
const restoring = ref(false)
const draftResumeId = ref('')
const parsedResume = ref<Resume | null>(null)
const uploadInput = ref<HTMLInputElement | null>(null)
const feedbackSent = ref(false)
const jdAnalysis = ref<JdAnalysis | null>(null)
let createIdempotencyKey = crypto.randomUUID()
let reportTimer: number | undefined

const form = ref({
  title: '我的产品经理简历',
  targetRole: '高级产品经理',
  content:
    '拥有5年互联网产品经验，负责过从0到1的SaaS产品。曾主导用户研究、需求规划、跨团队交付和商业化验证，并通过数据分析持续优化核心漏斗。',
  jdText:
    '负责AI产品规划与落地，能完成用户研究、需求分析、产品方案、项目推进和数据复盘；具备B端SaaS经验，理解大模型能力边界和成本治理。',
  questionCount: 5,
})

const progress = computed(() => {
  if (!active.value) return 0
  return Math.round((active.value.currentQuestionIndex / active.value.questionCount) * 100)
})

watch(
  () => auth.user,
  (user) => {
    if (user && user.availableCredits < 1) error.value = '当前可用次数不足，请在右上角账号与权益中心补充权益。'
  },
  { immediate: true },
)

watch(
  () => auth.token,
  async (token, previous) => {
    if (!token) return
    if (previous && previous !== token) {
      active.value = null
      report.value = null
      step.value = 'setup'
    }
    await loadHistory(true)
  },
  { immediate: true },
)

function showError(exception: unknown) {
  error.value = exception instanceof ApiError ? exception.message : '操作失败，请稍后重试'
}

async function createSession() {
  if (!auth.token) return
  loading.value = true
  error.value = ''
  try {
    if (!jdAnalysis.value || jdAnalysis.value.normalizedText !== form.value.jdText.replace(/\s+/g, ' ').trim()) {
      jdAnalysis.value = await interviewApi.analyzeJd(auth.token, form.value.jdText)
      return
    }
    if (!draftResumeId.value) {
      const resume = await interviewApi.createResume(auth.token, {
        title: form.value.title,
        targetRole: form.value.targetRole,
        content: form.value.content,
      })
      draftResumeId.value = resume.id
    } else if (parsedResume.value?.status === 'PARSED') {
      parsedResume.value = await interviewApi.confirmResume(auth.token, parsedResume.value.id, {
        version: parsedResume.value.version,
        title: form.value.title,
        targetRole: form.value.targetRole,
        content: form.value.content,
      })
    }
    active.value = await interviewApi.createInterview(auth.token, {
      resumeId: draftResumeId.value,
      jdText: form.value.jdText,
      questionCount: form.value.questionCount,
    }, createIdempotencyKey)
    createIdempotencyKey = crypto.randomUUID()
    draftResumeId.value = ''
    parsedResume.value = null
    step.value = 'ready'
    await loadHistory(false)
    await auth.refreshUser()
  } catch (exception) {
    showError(exception)
  } finally {
    loading.value = false
  }
}

async function uploadResumeFile(event: Event) {
  if (!auth.token) return
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  loading.value = true
  error.value = ''
  try {
    parsedResume.value = await interviewApi.uploadResume(auth.token, file)
    draftResumeId.value = parsedResume.value.id
    form.value.title = parsedResume.value.title
    form.value.content = parsedResume.value.content
    await interviewApi.analytics(auth.token, 'parse_success', {
      file_type: file.name.toLowerCase().endsWith('.pdf') ? 'pdf' : 'docx',
    }).catch(() => undefined)
  } catch (exception) { showError(exception) }
  finally { loading.value = false }
}

async function loadHistory(restoreActive: boolean) {
  if (!auth.token || restoring.value) return
  restoring.value = true
  try {
    history.value = await interviewApi.listInterviews(auth.token)
    if (!restoreActive || active.value) return
    const pending = history.value.find((item) =>
      ['READY', 'IN_PROGRESS', 'PAUSED', 'REPORTING'].includes(item.status),
    )
    if (!pending) return
    active.value = await interviewApi.getInterview(auth.token, pending.id)
    if (pending.status === 'READY') step.value = 'ready'
    else if (pending.status === 'REPORTING') {
      step.value = 'report'
      pollReport()
    } else step.value = 'interview'
  } catch (exception) {
    showError(exception)
  } finally {
    restoring.value = false
  }
}

async function openHistory(item: Interview) {
  if (!auth.token) return
  error.value = ''
  active.value = await interviewApi.getInterview(auth.token, item.id)
  if (item.status === 'READY') step.value = 'ready'
  else if (['IN_PROGRESS', 'PAUSED'].includes(item.status)) step.value = 'interview'
  else {
    step.value = 'report'
    pollReport()
  }
}

async function startSession() {
  if (!auth.token || !active.value) return
  loading.value = true
  error.value = ''
  try {
    active.value = await interviewApi.startInterview(auth.token, active.value.id)
    step.value = 'interview'
  } catch (exception) {
    showError(exception)
  } finally {
    loading.value = false
  }
}

async function submitAnswer() {
  if (!auth.token || !active.value || answer.value.trim().length < 2) return
  loading.value = true
  error.value = ''
  const submitted = answer.value
  answer.value = ''
  try {
    const receipt = await interviewApi.answer(
      auth.token,
      active.value.id,
      submitted,
      crypto.randomUUID(),
    )
    handleReceipt(receipt)
  } catch (exception) {
    answer.value = submitted
    showError(exception)
  } finally {
    loading.value = false
  }
}

function handleReceipt(receipt: AnswerReceipt) {
  if (!active.value) return
  if (receipt.completed) {
    active.value = { ...active.value, status: receipt.status, currentQuestion: undefined }
    if (receipt.status === 'ABORTED') reset()
    else { step.value = 'report'; pollReport() }
  } else {
    active.value = { ...active.value, status: receipt.status,
      currentQuestionIndex: receipt.answeredCount, currentQuestion: receipt.nextQuestion }
  }
}

async function skipQuestion() {
  if (!auth.token || !active.value || loading.value) return
  loading.value = true
  try { handleReceipt(await interviewApi.skip(auth.token, active.value.id, crypto.randomUUID())) }
  catch (exception) { showError(exception) }
  finally { loading.value = false }
}

async function finishInterview() {
  if (!auth.token || !active.value || loading.value) return
  loading.value = true
  try { handleReceipt(await interviewApi.finish(auth.token, active.value.id)) }
  catch (exception) { showError(exception) }
  finally { loading.value = false }
}

async function sendFeedback(helpful: boolean) {
  if (!auth.token || !active.value) return
  await interviewApi.reportFeedback(auth.token, active.value.id, helpful,
    helpful ? 'HELPFUL' : 'NEEDS_IMPROVEMENT')
  feedbackSent.value = true
}

async function retryReport() {
  if (!auth.token || !active.value) return
  report.value = await interviewApi.retryReport(auth.token, active.value.id)
  pollReport()
}

async function pauseSession() {
  if (!auth.token || !active.value) return
  loading.value = true
  try { active.value = await interviewApi.pauseInterview(auth.token, active.value.id) }
  catch (exception) { showError(exception) }
  finally { loading.value = false }
}

async function resumeSession() {
  if (!auth.token || !active.value) return
  loading.value = true
  try { active.value = await interviewApi.resumeInterview(auth.token, active.value.id) }
  catch (exception) { showError(exception) }
  finally { loading.value = false }
}

async function pollReport() {
  if (!auth.token || !active.value) return
  window.clearTimeout(reportTimer)
  try {
    report.value = await interviewApi.getReport(auth.token, active.value.id)
    if (report.value.status === 'PENDING') reportTimer = window.setTimeout(pollReport, 800)
  } catch (exception) {
    showError(exception)
    reportTimer = window.setTimeout(pollReport, 1500)
  }
}

function reset() {
  window.clearTimeout(reportTimer)
  active.value = null
  report.value = null
  feedbackSent.value = false
  answer.value = ''
  error.value = ''
  step.value = 'setup'
  auth.refreshUser()
  loadHistory(false)
}

onBeforeUnmount(() => window.clearTimeout(reportTimer))
</script>

<template>
  <main>
    <section class="hero">
      <div>
        <span class="eyebrow">完整体系 · 文字模拟面试</span>
        <h1>练一次，明确下一次<br /><em>具体改什么</em></h1>
        <p>基于你的简历与目标 JD 生成问题，用可解释量表给出训练反馈。评分仅用于练习，不代表录用概率。</p>
      </div>
      <div class="hero-metric">
        <b>3 → 1</b>
        <span>输入简历与 JD，完成一场练习，获得一份行动复盘</span>
      </div>
    </section>

    <p class="alert" v-if="error">{{ error }}</p>

    <section class="recent" v-if="step === 'setup' && history.length">
      <div><span class="eyebrow">最近练习</span><p>可继续未完成会话，或回看已生成的证据式复盘。</p></div>
      <button v-for="item in history.slice(0, 3)" :key="item.id" class="recent-item" @click="openHistory(item)">
        <span><b>{{ item.targetRole }}</b><small>{{ new Date(item.createdAt).toLocaleDateString() }} · {{ item.questionCount }}题</small></span>
        <em>{{ ['READY', 'IN_PROGRESS', 'PAUSED'].includes(item.status) ? '继续' : item.status === 'REPORTING' ? '生成中' : '查看复盘' }}</em>
      </button>
    </section>

    <section class="workspace" v-if="step === 'setup'">
      <div class="panel form-panel">
        <div class="panel-title"><span>01</span><div><h2>准备面试材料</h2><p>AI不会替你编造经历，所有事实以你的输入为准。</p></div></div>
        <label>简历名称<input v-model="form.title" maxlength="120" /></label>
        <label>目标岗位<input v-model="form.targetRole" maxlength="120" /></label>
        <label>简历摘要<textarea v-model="form.content" rows="7" maxlength="20000" /></label>
        <div class="upload-box">
          <input ref="uploadInput" type="file" accept=".pdf,.docx" hidden @change="uploadResumeFile" />
          <button class="secondary" :disabled="loading" @click="uploadInput?.click()">上传 PDF / DOCX</button>
          <small>最大 5MB；校验扩展名、魔数与加密状态。原文件不保存，仅提取文字。</small>
        </div>
        <div class="parse-notice" v-if="parsedResume">
          <b>解析完成 · 置信提示 {{ Math.round((parsedResume.parseConfidence ?? 0) * 100) }}%</b>
          <span>来源：{{ parsedResume.originalFilename }}。请逐字段核对并修改；点击生成方案即确认当前内容。</span>
        </div>
      </div>
      <div class="panel form-panel accent-panel">
        <div class="panel-title"><span>02</span><div><h2>定义目标场景</h2><p>建议粘贴完整职责和任职要求。</p></div></div>
        <label>目标岗位 JD<textarea v-model="form.jdText" rows="9" maxlength="12000" /></label>
        <div class="parse-notice" v-if="jdAnalysis">
          <b>{{ jdAnalysis.positionTitle }} · {{ jdAnalysis.roleFamily }} · 置信提示 {{ Math.round(jdAnalysis.confidence * 100) }}%</b>
          <span>核心技能：{{ jdAnalysis.coreSkills.join('、') || '未提取到明确技能，请核对原文' }}</span>
          <span>请确认解析方向；修改 JD 后系统会重新解析。</span>
        </div>
        <label>题目数量
          <select v-model="form.questionCount"><option :value="5">5题 · 快速</option><option :value="10">10题 · 标准</option><option :value="15">15题 · 深入</option></select>
        </label>
        <button class="primary" :disabled="loading || !auth.user || auth.user.availableCredits < 1" @click="createSession">
          {{ loading ? '正在准备…' : jdAnalysis ? '确认解析并生成面试方案' : '解析 JD' }}
        </button>
      </div>
    </section>

    <section class="focus-card" v-else-if="step === 'ready' && active">
      <span class="eyebrow">面试方案已就绪</span>
      <h2>{{ active.targetRole }} · {{ active.questionCount }} 道题</h2>
      <p>创建时预占 1 次权益，首题回答确认后才正式扣减。仅创建、暂停或系统故障不会重复计费。</p>
      <div class="question-preview"><article v-for="question in active.questionPlan.slice(0, 5)" :key="question.sequence"><span>{{ question.type }} · {{ question.difficulty }} · {{ question.sourceType }}</span><b>{{ question.content }}</b><small>考察点：{{ question.keyPoints.join('、') }}</small></article></div>
      <button class="primary" :disabled="loading" @click="startSession">{{ loading ? '启动中…' : '开始面试' }}</button>
    </section>

    <section class="interview-layout" v-else-if="step === 'interview' && active">
      <aside class="progress-card">
        <span>当前进度</span><b>{{ active.currentQuestionIndex + 1 }} / {{ active.questionCount }}</b>
        <div class="progress"><i :style="{ width: `${progress}%` }"></i></div>
        <small>回答将先安全保存，再进入下一题。</small>
      </aside>
      <div class="question-card">
        <div class="session-toolbar">
          <span>{{ active.status === 'PAUSED' ? '面试已暂停，当前进度已保存' : '会话自动保存 · 支持恢复' }}</span>
          <button v-if="active.status === 'IN_PROGRESS'" class="secondary compact" :disabled="loading" @click="pauseSession">暂停</button>
          <button v-else class="primary compact" :disabled="loading" @click="resumeSession">继续面试</button>
        </div>
        <span class="eyebrow">面试官提问</span>
        <h2>{{ active.currentQuestion }}</h2>
        <label>你的回答<textarea v-model="answer" rows="9" maxlength="8000" :disabled="active.status === 'PAUSED'" placeholder="建议采用：结论—背景—行动—结果—复盘" @keydown.meta.enter="submitAnswer" /></label>
        <div class="answer-actions"><small>{{ answer.length }} / 8000</small><div><button class="text-button" :disabled="loading || active.status === 'PAUSED'" @click="skipQuestion">跳过本题</button><button class="text-button danger" :disabled="loading" @click="finishInterview">结束面试</button><button class="primary" :disabled="loading || active.status === 'PAUSED' || answer.trim().length < 2" @click="submitAnswer">{{ loading ? '保存中…' : '提交并继续' }}</button></div></div>
      </div>
    </section>

    <section class="focus-card report-card" v-else-if="step === 'report'">
      <template v-if="!report || report.status === 'PENDING'">
        <div class="loader"></div><h2>正在生成证据式复盘</h2><p>系统正在按量表整理回答，不会给出人格或录用判断。</p>
      </template>
      <template v-else-if="report.status === 'READY'">
        <span class="eyebrow">复盘已完成 · {{ report.rubricVersion }} · 置信度 {{ Math.round((report.confidence ?? 0) * 100) }}%</span>
        <div class="score">{{ report.totalScore }}<small>/100</small></div>
        <p v-if="report.previousScore !== undefined" class="score-compare">
          同岗位上次 {{ report.previousScore }} 分，本次
          <b>{{ (report.scoreDelta ?? 0) >= 0 ? '+' : '' }}{{ report.scoreDelta }}</b>
        </p>
        <p>{{ report.summary }}</p>
        <div class="report-grid"><article><h3>本次亮点</h3><p>{{ report.strengths }}</p></article><article><h3>下一步行动</h3><p>{{ report.improvements }}</p></article></div>
        <div class="dimension-grid">
          <article v-for="dimension in report.dimensions" :key="dimension.code">
            <span><b>{{ dimension.label }}</b><strong>{{ dimension.score }}/10</strong></span>
            <div class="dimension-bar"><i :style="{ width: `${dimension.score * 10}%` }"></i></div>
            <small>{{ dimension.rationale }}</small>
          </article>
        </div>
        <div class="evidence-report">
          <h2>逐题证据</h2>
          <article v-for="item in report.questionFeedback" :key="item.sequence" class="evidence-card">
            <header><span>第 {{ item.sequence }} 题</span><b>{{ item.score }}/100</b></header>
            <h3>{{ item.question }}</h3>
            <blockquote>“{{ item.evidence }}”</blockquote>
            <div><p><b>亮点：</b>{{ item.strength }}</p><p><b>问题：</b>{{ item.issue }}</p><p><b>重练建议：</b>{{ item.suggestion }}</p></div>
          </article>
        </div>
        <div class="action-plan"><h2>下次练习只做这 3 件事</h2><ol><li v-for="action in report.actionItems" :key="action">{{ action }}</li></ol></div>
        <div class="report-feedback" v-if="!feedbackSent"><span>这份复盘对你有帮助吗？</span><button class="secondary" @click="sendFeedback(true)">有帮助</button><button class="text-button" @click="sendFeedback(false)">需要改进</button></div>
        <p v-else>感谢反馈，质量团队会按量表版本持续校准。</p>
        <button class="secondary" @click="reset">再练一次</button>
      </template>
      <template v-else><h2>报告生成失败</h2><p>平台已自动返还本次权益，记录仍然保留。</p><button class="primary" @click="retryReport">重新生成</button><button class="secondary" @click="reset">返回</button></template>
    </section>
  </main>
</template>
