<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { practiceApi, type PracticeDashboard, type PracticeQuestion } from '@/api/practice'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const questions = ref<PracticeQuestion[]>([])
const dashboard = ref<PracticeDashboard | null>(null)
const category = ref((route.query.category as string) || '')
const type = ref('')
const loading = ref(false)
const error = ref('')
const collection = computed(() => (route.query.collection as string) || 'ALL')

const collectionTitle = computed(() => ({
  WRONG: '错题本', FAVORITE: '我的收藏', DONE: '已做题目', ALL: '全部题库',
}[collection.value] ?? '全部题库'))
const typeLabels: Record<string, string> = {
  SINGLE: '单选题', MULTIPLE: '多选题', TRUE_FALSE: '判断题', SCENARIO: '情景题', SHORT_ANSWER: '简答题',
}

watch(
  [() => auth.token, collection, category, type],
  async ([token]) => {
    if (!token) return
    loading.value = true
    error.value = ''
    try {
      const [items, data] = await Promise.all([
        practiceApi.questions(token, { category: category.value, type: type.value, collection: collection.value }),
        dashboard.value ? Promise.resolve(dashboard.value) : practiceApi.dashboard(token),
      ])
      questions.value = items
      dashboard.value = data
    } catch (exception) {
      error.value = exception instanceof ApiError ? exception.message : '题库加载失败'
    } finally { loading.value = false }
  },
  { immediate: true },
)

async function start() {
  if (!auth.token || !questions.value.length) return
  const mode = collection.value === 'WRONG' ? 'WRONG' : collection.value === 'FAVORITE' ? 'FAVORITE'
    : category.value ? 'CATEGORY' : 'SEQUENTIAL'
  const session = await practiceApi.createSession(auth.token, {
    mode, categoryCode: category.value || undefined, questionCount: Math.min(20, questions.value.length),
  })
  await router.push(`/practice/${session.id}`)
}

async function toggle(question: PracticeQuestion) {
  if (!auth.token) return
  const result = await practiceApi.favorite(auth.token, question.id)
  question.favorite = result.favorite
  if (collection.value === 'FAVORITE' && !result.favorite) {
    questions.value = questions.value.filter((value) => value.id !== question.id)
  }
}
</script>

<template>
  <main class="app-main library-page">
    <header class="library-header">
      <div><span class="app-kicker">QUESTION BANK</span><h1>{{ collectionTitle }}</h1><p>按岗位和题型筛选；答案只在作答后展示，避免“看懂了但不会做”。</p></div>
      <div class="library-count"><strong>{{ questions.length }}</strong><span>道题目</span></div>
    </header>
    <p v-if="error" class="alert">{{ error }}</p>
    <section class="library-toolbar">
      <select v-model="category"><option value="">全部岗位</option><option v-for="item in dashboard?.categories" :key="item.code" :value="item.code">{{ item.name }}</option></select>
      <select v-model="type"><option value="">全部题型</option><option v-for="(label, value) in typeLabels" :key="value" :value="value">{{ label }}</option></select>
      <button class="start-button" :disabled="!questions.length || loading" @click="start">练习筛选结果</button>
    </section>
    <section class="question-bank-list">
      <article v-for="(question, index) in questions" :key="question.id">
        <div class="question-number">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="bank-question-body">
          <div class="question-meta"><span>{{ question.categoryName }}</span><span>{{ typeLabels[question.type] }}</span><span :class="`difficulty-${question.difficulty.toLowerCase()}`">{{ question.difficulty }}</span><em v-if="question.answered">{{ question.lastCorrect ? '上次答对' : '上次答错' }}</em></div>
          <h2>{{ question.stem }}</h2>
          <div class="tag-row"><span v-for="tag in question.tags" :key="tag"># {{ tag }}</span></div>
        </div>
        <button class="favorite-button" :class="{ active: question.favorite }" :aria-label="question.favorite ? '取消收藏' : '收藏题目'" @click="toggle(question)">{{ question.favorite ? '★' : '☆' }}</button>
      </article>
      <div v-if="!questions.length && !loading" class="empty-state"><i>📭</i><h2>这里暂时没有题目</h2><p>先去完成一些练习，或者换一个筛选条件。</p></div>
    </section>
  </main>
</template>
