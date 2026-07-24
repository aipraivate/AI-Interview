import { defineStore } from 'pinia'
import { interviewApi, type GuestLogin, type User } from '@/api/interview'

const TOKEN_KEY = 'interview_mvp_token'
const REFRESH_KEY = 'interview_refresh_token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    refreshToken: localStorage.getItem(REFRESH_KEY) ?? '',
    user: null as User | null,
    loading: false,
  }),
  actions: {
    saveSession(session: GuestLogin) {
      this.token = session.accessToken
      this.refreshToken = session.refreshToken
      this.user = session.user
      localStorage.setItem(TOKEN_KEY, session.accessToken)
      localStorage.setItem(REFRESH_KEY, session.refreshToken)
    },
    clearSession() {
      this.token = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
    },
    async initialize() {
      if (this.loading || this.user) return
      this.loading = true
      try {
        if (this.token) {
          try {
            this.user = await interviewApi.me(this.token)
            return
          } catch {
            if (this.refreshToken) {
              try {
                this.saveSession(await interviewApi.refresh(this.refreshToken))
                return
              } catch { this.clearSession() }
            }
          }
        }
        this.saveSession(await interviewApi.guest())
      } finally { this.loading = false }
    },
    async register(payload: { email: string; password: string; nickname: string; acceptTerms: boolean; acceptPrivacy: boolean }) {
      this.saveSession(await interviewApi.register(payload, this.token))
    },
    async login(email: string, password: string) {
      this.saveSession(await interviewApi.login({ email, password }))
    },
    async logout() {
      if (this.refreshToken) await interviewApi.logout(this.refreshToken).catch(() => undefined)
      this.clearSession()
      this.saveSession(await interviewApi.guest())
    },
    async refreshUser() { if (this.token) this.user = await interviewApi.me(this.token) },
  },
})
