import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import PracticeHomeView from '../views/PracticeHomeView.vue'
import PracticeSessionView from '../views/PracticeSessionView.vue'
import PracticeShareView from '../views/PracticeShareView.vue'
import QuestionLibraryView from '../views/QuestionLibraryView.vue'

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'training-home', component: PracticeHomeView },
    { path: '/library', name: 'library', component: QuestionLibraryView },
    { path: '/practice/:sessionId', name: 'practice', component: PracticeSessionView },
    { path: '/share/:token', name: 'practice-share', component: PracticeShareView },
    { path: '/ai-interview', name: 'ai-interview', component: HomeView },
  ],
})
