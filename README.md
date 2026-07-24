# AI 面试辅助平台

依据《AI面试辅助平台 — 商业级产品需求与技术规格说明书 V2.0 完善版》实现的 C 端 P0 可交付版本。主流程为：账号/游客 → 简历上传解析与确认 → JD 结构确认 → 结构化题集 → 可恢复文字面试 → 异步证据式复盘 → 权益与数据权利。

详细完成度与边界见 [实施进度](docs/FULL_SYSTEM_PROGRESS.md)，部署验收与外部门禁见 [交付清单](docs/DELIVERY_CHECKLIST.md)，生产操作见 [运行手册](docs/RUNBOOK.md)。

## 技术基线

- 后端：Java 21、Spring Boot 4.1、Spring Security、JPA、Flyway、PDFBox、Apache POI
- 前端：Vue 3、TypeScript、Pinia、Vue Router、Vite
- 数据：MySQL 8.4、Redis 7.4；本地和测试使用 H2
- 部署：Docker Compose，或 Nginx + Java 原生进程

## 本地运行

后端默认使用 H2 文件库和确定性本地 AI，不需要外部密钥：

```bash
cd backend
./mvnw spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。系统会创建匿名体验账号并发放 3 次文字面试权益。

## 完整质量门禁

```bash
cd backend && ./mvnw test
cd ../frontend && npm run build && npm run test:unit -- --run && npm run lint
```

要求 Java 21、Node 22.18+。如果本机只有 Java 17，可以暂用 `./mvnw -Djava.version=17 test` 做兼容验证，生产仍须使用 Java 21。

## Docker 验收环境

```bash
cp .env.example .env
# 修改所有 replace-with-* 值
docker compose up --build -d
docker compose ps
```

访问 `http://localhost:8088`。Compose 会启动 MySQL、Redis、API 和 Web；数据库变更由 Flyway 自动、只向前执行。

## 核心 API

- `/api/v1/auth/*`：游客、注册、登录、Refresh Token 轮换、退出
- `/api/v1/resumes/*`：手填、PDF/DOCX 上传、确认、默认版本和历史
- `/api/v1/jd/analyze`：JD 结构化和提示注入检查
- `/api/v1/interviews/*`：创建、开始、回答、跳题、暂停、恢复、结束、消息/SSE、报告
- `/api/v1/orders/*`、`/api/v1/entitlements/*`：订单、退款、账户和不可变流水
- `/api/v1/payments/webhooks/sandbox`：HMAC-SHA256 验签支付回调
- `/api/v1/privacy/requests/*`：数据导出、下载和注销删除
- `/api/v1/analytics/events`：只接受白名单事件和低基数字段

## 安全约束

密钥只通过环境变量或 Secret Manager 注入；不得提交 `.env`、模型密钥或支付密钥。Web 默认禁止摄像头、麦克风、定位、外部脚本和 iframe；Nginx 不向公网暴露 Actuator。支付沙箱按钮不会真实扣款。
