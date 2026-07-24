import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:15173',
    channel: 'chrome',
    headless: true,
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: "./mvnw -Djava.version=17 spring-boot:run -Dspring-boot.run.arguments='--server.port=18081 --app.cors.allowed-origins=http://127.0.0.1:15173 --spring.datasource.url=jdbc:h2:mem:e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1'",
      cwd: '../backend',
      url: 'http://127.0.0.1:18081/actuator/health',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
      env: { ...process.env, VITE_API_PROXY_TARGET: 'http://127.0.0.1:18081' },
      url: 'http://127.0.0.1:15173',
      timeout: 30_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
})
