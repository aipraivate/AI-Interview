<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import { interviewApi, type DataRequest, type LedgerEntry, type Product } from '@/api/interview'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
auth.initialize()
const showAccount = ref(false)
const accountMode = ref<'login' | 'register'>('register')
const loading = ref(false)
const message = ref('')
const products = ref<Product[]>([])
const ledger = ref<LedgerEntry[]>([])
const dataRequests = ref<DataRequest[]>([])
const form = ref({ email: '', password: '', nickname: '', accept: false })

async function openAccount() {
  showAccount.value = true
  message.value = ''
  if (auth.token) {
    if (!products.value.length) products.value = await interviewApi.products(auth.token)
    ledger.value = await interviewApi.entitlementLedger(auth.token)
    dataRequests.value = await interviewApi.dataRequests(auth.token)
  }
}

async function submitAccount() {
  loading.value = true
  message.value = ''
  try {
    if (accountMode.value === 'register') {
      await auth.register({ email: form.value.email, password: form.value.password,
        nickname: form.value.nickname, acceptTerms: form.value.accept, acceptPrivacy: form.value.accept })
    } else await auth.login(form.value.email, form.value.password)
    message.value = '账号已连接，练习记录与权益将跟随当前账号。'
  } catch (exception) {
    message.value = exception instanceof ApiError ? exception.message : '操作失败，请稍后再试'
  } finally { loading.value = false }
}

async function buy(product: Product) {
  if (!auth.token) return
  loading.value = true
  try {
    const order = await interviewApi.createOrder(auth.token, product.id)
    await interviewApi.sandboxPay(auth.token, order.id)
    await auth.refreshUser()
    message.value = `沙箱支付完成，已到账 ${product.credits} 次权益。`
  } catch (exception) {
    message.value = exception instanceof ApiError ? exception.message : '订单处理失败'
  } finally { loading.value = false }
}

async function requestExport() {
  if (!auth.token) return
  const request = await interviewApi.createDataRequest(auth.token, 'EXPORT')
  message.value = `数据导出申请已受理，状态：${request.status}`
  dataRequests.value = await interviewApi.dataRequests(auth.token)
}

async function requestDelete() {
  if (!auth.token || !window.confirm('注销后将停止登录并去标识化业务内容，确定继续吗？')) return
  try {
    const request = await interviewApi.createDataRequest(auth.token, 'DELETE', form.value.password)
    message.value = `注销申请已受理，状态：${request.status}`
    dataRequests.value = await interviewApi.dataRequests(auth.token)
  } catch (exception) {
    message.value = exception instanceof ApiError ? exception.message : '注销申请失败'
  }
}

async function downloadExport(request: DataRequest) {
  if (!auth.token) return
  try {
    const blob = await interviewApi.downloadDataExport(auth.token, request.id)
    const href = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = href
    anchor.download = `ai-interview-data-${request.id}.json`
    anchor.click()
    URL.revokeObjectURL(href)
  } catch (exception) {
    message.value = exception instanceof ApiError ? exception.message : '数据副本下载失败'
  }
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand"><span class="brand-mark">AI</span><div><strong>面试练习室</strong><small>证据式反馈 · 可重复训练</small></div></div>
      <button class="account account-trigger" v-if="auth.user" @click="openAccount">
        <span>{{ auth.user.email || auth.user.nickname }}</span><b>{{ auth.user.availableCredits }} 次可用</b>
      </button>
    </header>
    <RouterView />

    <div class="drawer-mask" v-if="showAccount" @click.self="showAccount = false">
      <aside class="account-drawer">
        <button class="drawer-close" @click="showAccount = false">×</button>
        <span class="eyebrow">账号与权益中心</span>
        <h2>{{ auth.user?.email ? '我的账户' : '保存你的练习进度' }}</h2>
        <template v-if="!auth.user?.email">
          <div class="mode-switch"><button :class="{ active: accountMode === 'register' }" @click="accountMode = 'register'">注册</button><button :class="{ active: accountMode === 'login' }" @click="accountMode = 'login'">登录</button></div>
          <label v-if="accountMode === 'register'">昵称<input v-model="form.nickname" autocomplete="name" /></label>
          <label>邮箱<input v-model="form.email" type="email" autocomplete="email" /></label>
          <label>密码<input v-model="form.password" type="password" autocomplete="current-password" placeholder="至少10位，包含字母和数字" /></label>
          <label class="consent" v-if="accountMode === 'register'"><input v-model="form.accept" type="checkbox" />我已阅读并同意服务协议与隐私政策（2026-07-21版）</label>
          <button class="primary wide" :disabled="loading || !form.email || !form.password || (accountMode === 'register' && (!form.nickname || !form.accept))" @click="submitAccount">{{ loading ? '处理中…' : accountMode === 'register' ? '注册并登录' : '登录' }}</button>
        </template>
        <template v-else>
          <div class="account-summary"><strong>{{ auth.user.nickname }}</strong><span>{{ auth.user.email }}</span><small>{{ auth.user.memberLevel }} · {{ auth.user.availableCredits }} 次可用权益</small></div>
          <h3>权益补充（支付沙箱）</h3>
          <button class="product" v-for="product in products" :key="product.id" :disabled="loading" @click="buy(product)"><span><b>{{ product.name }}</b><small>{{ product.credits }} 次到账</small></span><strong>¥{{ (product.amountCents / 100).toFixed(2) }}</strong></button>
          <h3>最近权益流水</h3>
          <div class="mini-list"><span v-for="entry in ledger.slice(0, 5)" :key="entry.id"><b>{{ entry.operation }}</b><small>{{ entry.amount }} · {{ new Date(entry.createdAt).toLocaleDateString() }}</small></span></div>
          <h3>数据权利</h3>
          <div class="privacy-actions"><button class="secondary" @click="requestExport">申请数据导出</button><button class="text-button" @click="auth.logout">退出登录</button></div>
          <div class="mini-list"><span v-for="request in dataRequests.slice(0, 3)" :key="request.id"><b>{{ request.type }} · {{ request.status }}</b><small>{{ request.resultMessage || new Date(request.createdAt).toLocaleString() }}<button v-if="request.type === 'EXPORT' && request.status === 'COMPLETED'" class="text-button" @click="downloadExport(request)">下载</button></small></span></div>
          <label>注销验证密码<input v-model="form.password" type="password" placeholder="正式账号注销前需再次验证密码" /></label>
          <button class="text-button danger wide" @click="requestDelete">申请注销并删除数据</button>
        </template>
        <p class="drawer-message" v-if="message">{{ message }}</p>
        <small class="sandbox-note">当前支付为验收沙箱，不发生真实扣款；正式渠道接入后将启用签名回调与对账。</small>
      </aside>
    </div>
  </div>
</template>
