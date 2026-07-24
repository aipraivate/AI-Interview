<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { practiceApi, type PracticeShare } from '@/api/practice'

const route = useRoute()
const share = ref<PracticeShare | null>(null)
const missing = ref(false)

watch(
  () => route.params.token,
  async (token) => {
    if (!token) return
    try { share.value = await practiceApi.share(String(token)) }
    catch { missing.value = true }
  },
  { immediate: true },
)

const modeName = (mode: string) => ({
  SEQUENTIAL: '顺序练习', RANDOM: '随机练习', MOCK: '模拟考试', WRONG: '错题重练',
  FAVORITE: '收藏练习', CATEGORY: '专项练习',
}[mode] ?? mode)
</script>

<template>
  <main class="app-main share-page">
    <section v-if="share" class="share-card">
      <div class="share-brand"><i>AI</i><span>面试训练营</span></div>
      <span class="app-kicker">MY TRAINING REPORT</span>
      <h1>{{ share.title }}</h1>
      <div class="share-score"><strong>{{ share.score }}</strong><span>分</span></div>
      <p>{{ modeName(share.mode) }} · 答对 {{ share.correctCount }}/{{ share.totalCount }} 题</p>
      <div class="share-slogan">把不会的题练会，<br />比盲目刷更多题更重要。</div>
      <small>{{ share.viewCount }} 人查看 · 成绩仅代表本次训练</small>
      <RouterLink class="start-button" to="/">我也要开始练习 →</RouterLink>
    </section>
    <section v-else-if="missing" class="empty-state"><i>🔗</i><h2>分享已失效</h2><p>链接可能已过期或不存在。</p><RouterLink class="start-button" to="/">进入训练中心</RouterLink></section>
    <section v-else class="loading-panel"><div class="loader"></div></section>
  </main>
</template>
