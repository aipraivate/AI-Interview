# 生产运行手册

## 首次部署

1. 准备 Java 21、MySQL 8.4、Redis 7.4、Nginx；或安装 Docker/Compose。
2. 从 `.env.example` 创建不入库的 `.env`，更换所有口令和密钥。生产模型启用 `AI_PROVIDER=compatible`。
3. 执行后端测试、前端构建/测试/lint；再执行 `docker compose up --build -d`。
4. 检查 `docker compose ps`，API 容器健康检查必须为 healthy；从内网检查 `/actuator/health`，从公网访问该路径必须为 404。
5. 完成 `DELIVERY_CHECKLIST.md` 的 UAT 后再切流。

## 日常检查

- 健康：API、MySQL、Redis；Redis 不可用时限流会退化为单实例并输出 WARN，必须告警。
- API：按 `X-Trace-Id` 关联错误；关注 5xx、429、P95/P99 延迟和登录失败突增。
- 业务：READY 预占过期释放、Outbox 积压/最终失败、报告补偿、支付回调验签失败、退款人工复核、隐私请求失败。
- 容量：数据库连接池、磁盘、慢 SQL、Redis 内存和 Web/API CPU/内存。

## 支付回调协议

验收回调为 `POST /api/v1/payments/webhooks/sandbox`，请求头：

- `X-Payment-Timestamp`：Unix 秒，和服务器相差不能超过 300 秒。
- `X-Payment-Signature`：`HMAC-SHA256(PAYMENT_WEBHOOK_SECRET, timestamp + "." + rawBody)` 的小写十六进制。

Body 字段为 `eventId`、`orderId`、`providerTradeNo`。相同 provider/eventId 只处理一次。接真实渠道时保留此领域接口，把验签和账单适配替换为渠道官方规则，禁止绕过 `OrderService.providerPay` 直接发权益。

## 备份与恢复

原生部署可执行：

```bash
./deploy/backup-db.sh "$PWD"
```

脚本生成权限 600 的 gzip SQL 并校验文件完整性。至少每季度在隔离 MySQL 执行真实恢复：新建空库、导入备份、运行应用只读冒烟、核对订单数/权益总额/报告数/最近时间点。备份必须加密并按法务周期销毁。

## 故障处理

- AI 网关不可用：题集调用自动降级本地实现；报告 Outbox 重试，最终失败标记并自动补偿一次权益。恢复后通过报告重试接口重放。
- Redis 不可用：服务继续运行并使用本地限流；多实例限流不再全局准确，应先缩容到单实例或尽快恢复 Redis。
- 支付回调异常：不要手工改权益余额；保存 eventId/orderId/traceId，在验签和订单状态确认后重放原事件。
- 隐私请求失败：状态进入失败和审计记录；先确认失败范围，再由授权人员重试，禁止把导出 JSON 放入普通日志或工单附件。
- 数据库故障：停止写流量，按最近已验证备份恢复，再由 Flyway 前向迁移。恢复前禁止执行破坏性修复 SQL。

## 回滚

应用镜像/前端静态文件可回滚到上一个已验证版本；数据库迁移只向前，不运行 down migration。若新版本已写入新字段，先发布兼容旧/新 Schema 的修复版本，再切回业务代码。回滚后必须验证登录、简历列表、会话恢复、报告读取和权益账本。
