import { expect, test, type Page } from '@playwright/test'

async function expectNoRuntimeErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  return () => expect(errors, errors.join('\n')).toEqual([])
}

test('题库来自后端，空错题入口不会发出失败请求', async ({ page }) => {
  const verifyErrors = await expectNoRuntimeErrors(page)
  const failedResponses: string[] = []
  page.on('response', (response) => {
    if (response.status() >= 400) failedResponses.push(`${response.status()} ${response.url()}`)
  })

  await page.goto('/')
  await expect(page.locator('.account-trigger')).toContainText('体验用户')
  await expect(page.locator('.collection-card.done')).toContainText('60')
  const wrongPractice = page.getByRole('button', { name: /错题重练/ })
  await expect(wrongPractice).toBeDisabled()
  await expect(wrongPractice).toContainText('暂无数据')
  await expect(failedResponses).toEqual([])
  verifyErrors()
})

test('收藏、筛选、创建练习和提交答案形成真实持久化链路', async ({ page }) => {
  const verifyErrors = await expectNoRuntimeErrors(page)
  await page.goto('/library')
  await expect(page.locator('.library-count strong')).toHaveText('60')
  await expect(page.locator('.question-bank-list article')).toHaveCount(60)

  const firstFavorite = page.locator('.favorite-button').first()
  await firstFavorite.click()
  await expect(firstFavorite).toHaveClass(/active/)
  await page.getByRole('button', { name: '练习筛选结果' }).click()
  await expect(page).toHaveURL(/\/practice\//)
  await expect(page.locator('.practice-question-card h1')).toContainText('RAG')
  await page.getByRole('button', { name: /检索召回质量和引用依据/ }).click()
  await page.getByRole('button', { name: '提交答案' }).click()
  await expect(page.locator('.answer-analysis')).toContainText('回答正确')
  verifyErrors()
})

test('AI 实战完成五题、扣减权益并生成证据报告', async ({ page }) => {
  const verifyErrors = await expectNoRuntimeErrors(page)
  await page.goto('/ai-interview')
  await expect(page.locator('.account-trigger b')).toContainText('3 次')

  const setupButton = page.locator('.accent-panel button.primary')
  await setupButton.click()
  await expect(setupButton).toHaveText('确认解析并生成面试方案')
  await setupButton.click()
  await expect(page.locator('.question-preview article')).toHaveCount(5)
  await page.getByRole('button', { name: '开始面试' }).click()

  for (let index = 0; index < 5; index += 1) {
    await page.locator('.question-card textarea').fill(
      `第${index + 1}题：我先明确目标和约束，分析用户问题，组织跨团队行动，通过数据验证结果并复盘改进。`,
    )
    await page.getByRole('button', { name: '提交并继续' }).click()
  }

  await expect(page.locator('.report-card .score')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.evidence-card')).toHaveCount(5)
  await expect(page.locator('.account-trigger b')).toContainText('2 次')
  await page.getByRole('button', { name: '有帮助' }).click()
  await expect(page.locator('.report-card')).toContainText('感谢反馈')
  verifyErrors()
})

test('游客可升级为真实账号并读取持久化账户数据', async ({ page }) => {
  const verifyErrors = await expectNoRuntimeErrors(page)
  await page.goto('/')
  await page.locator('.account-trigger').click()
  await page.getByLabel('昵称').fill('浏览器验收用户')
  await page.getByLabel('邮箱').fill(`e2e-${Date.now()}@example.com`)
  await page.getByLabel('密码').fill('RealAccount2026')
  await page.getByLabel(/我已阅读并同意/).check()
  await page.getByRole('button', { name: '注册并登录' }).click()

  await expect(page.locator('.account-summary')).toContainText('浏览器验收用户')
  await expect(page.locator('.account-summary')).toContainText('3 次可用权益')
  await expect(page.locator('.product')).toHaveCount(2)
  await page.getByRole('button', { name: /申请数据导出/ }).click()
  await expect(page.locator('.drawer-message')).toContainText('数据导出申请已受理')
  verifyErrors()
})
