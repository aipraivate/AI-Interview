<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { practiceApi, type PracticeDashboard } from '@/api/practice'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const dashboard = ref<PracticeDashboard | null>(null)
const loading = ref(false)
const error = ref('')

const modes = [
  { mode: 'SEQUENTIAL', icon: '📚', title: '顺序练习', note: '从基础到进阶，系统刷完题库', count: 10, tone: 'blue' },
  { mode: 'RANDOM', icon: '🔀', title: '随机练习', note: '跨岗位抽题，检查知识盲区', count: 10, tone: 'purple' },
  { mode: 'MOCK', icon: '⏱️', title: '模拟考试', note: '10 题限时挑战，交卷后看答案', count: 10, tone: 'orange' },
  { mode: 'WRONG', icon: '🩹', title: '错题重练', note: '只练最近没有答对的题', count: 20, tone: 'red' },
]

watch(
  () => auth.token,
  async (token) => {
    if (!token) return
    loading.value = true
    try {
      dashboard.value = await practiceApi.dashboard(token)
    } catch (exception) {
      error.value = exception instanceof ApiError ? exception.message : '训练数据加载失败'
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

async function start(mode: string, questionCount: number, categoryCode?: string) {
  if (!auth.token || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const session = await practiceApi.createSession(auth.token, { mode, categoryCode, questionCount })
    await router.push(`/practice/${session.id}`)
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '暂时无法开始练习'
  } finally {
    loading.value = false
  }
}

function categoryProgress(done: number, total: number) {
  return total ? Math.round((done / total) * 100) : 0
}

const modeName = (mode: string) => ({
  SEQUENTIAL: '顺序练习', RANDOM: '随机练习', MOCK: '模拟考试',
  WRONG: '错题重练', FAVORITE: '收藏练习', CATEGORY: '专项练习',
}[mode] ?? mode)
</script>

<template>
  <main class="app-main training-home">
    <p v-if="error" class="alert">{{ error }}</p>

    <section class="training-hero">
      <div class="hero-copy">
        <span class="app-kicker">INTERVIEW TRAINING CAMP</span>
        <h1>像刷驾考题一样，<br /><em>系统练会面试</em></h1>
        <p>分类题库、专项练习、模拟考试、错题复习，再进入 AI 实战。每天十分钟，进步看得见。</p>
        <div class="hero-actions">
          <button class="start-button" :disabled="loading" @click="start('RANDOM', 10)">开始今日练习 <span>→</span></button>
          <RouterLink class="ghost-link" to="/library">浏览全部题库</RouterLink>
        </div>
      </div>
      <div class="daily-card">
        <div class="daily-ring" :style="{ '--score': `${dashboard?.accuracy ?? 0}%` }">
          <strong>{{ dashboard?.accuracy ?? 0 }}<small>%</small></strong>
          <span>综合正确率</span>
        </div>
        <div class="daily-stats">
          <span><b>{{ dashboard?.studyDays ?? 0 }}</b>练习天数</span>
          <span><b>{{ dashboard?.totalAttempts ?? 0 }}</b>累计答题</span>
          <span><b>{{ dashboard?.masteredQuestions ?? 0 }}</b>已掌握</span>
        </div>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading">
        <div><span>快速开始</span><h2>选择你的训练方式</h2></div>
        <RouterLink to="/library">查看题库 →</RouterLink>
      </div>
      <div class="mode-grid">
        <button v-for="item in modes" :key="item.mode" class="mode-card" :class="item.tone" :disabled="loading" @click="start(item.mode, item.count)">
          <i>{{ item.icon }}</i><span><b>{{ item.title }}</b><small>{{ item.note }}</small></span><em>开始</em>
        </button>
      </div>
    </section>

    <section class="collection-strip">
      <RouterLink to="/library?collection=WRONG" class="collection-card wrong">
        <span>错题本</span><strong>{{ dashboard?.wrongQuestions ?? 0 }}</strong><small>薄弱点集中重练</small>
      </RouterLink>
      <RouterLink to="/library?collection=FAVORITE" class="collection-card favorite">
        <span>我的收藏</span><strong>{{ dashboard?.favoriteQuestions ?? 0 }}</strong><small>重要题目随时回顾</small>
      </RouterLink>
      <RouterLink to="/library?collection=DONE" class="collection-card done">
        <span>已做题目</span><strong>{{ dashboard?.attemptedQuestions ?? 0 }}</strong><small>共 {{ dashboard?.totalQuestions ?? 0 }} 道审核题</small>
      </RouterLink>
      <RouterLink to="/ai-interview" class="collection-card ai">
        <span>AI 实战模拟</span><strong>∞</strong><small>简历 + JD 个性化追问</small>
      </RouterLink>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><span>专项题库</span><h2>按岗位能力逐个突破</h2></div></div>
      <div class="category-grid">
        <button v-for="category in dashboard?.categories" :key="category.code" class="category-card" :disabled="loading" @click="start('CATEGORY', Math.min(10, category.totalCount), category.code)">
          <i :style="{ background: `${category.color}18`, color: category.color }">{{ category.icon }}</i>
          <div><b>{{ category.name }}</b><p>{{ category.description }}</p></div>
          <div class="category-progress"><span><em :style="{ width: `${categoryProgress(category.completedCount, category.totalCount)}%`, background: category.color }"></em></span><small>{{ category.completedCount }}/{{ category.totalCount }}</small></div>
        </button>
      </div>
    </section>

    <section v-if="dashboard?.recentSessions.length" class="section-block recent-training">
      <div class="section-heading"><div><span>训练记录</span><h2>最近练习</h2></div></div>
      <div class="session-list">
        <RouterLink v-for="session in dashboard.recentSessions" :key="session.id" :to="`/practice/${session.id}`">
          <i>{{ session.status === 'COMPLETED' ? '✓' : '…' }}</i>
          <span><b>{{ modeName(session.mode) }}</b><small>{{ new Date(session.createdAt).toLocaleString() }} · {{ session.answeredCount }}/{{ session.totalCount }} 题</small></span>
          <strong>{{ session.status === 'COMPLETED' ? `${session.score} 分` : '继续练习' }}</strong>
        </RouterLink>
      </div>
    </section>
  </main>
</template>
