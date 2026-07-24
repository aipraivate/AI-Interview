CREATE TABLE question_categories (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(240) NOT NULL,
    icon VARCHAR(20) NOT NULL,
    color VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_question_category_code UNIQUE (code)
);

CREATE TABLE practice_questions (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT NOT NULL,
    correct_answer_json TEXT NOT NULL,
    explanation TEXT NOT NULL,
    tags_json TEXT NOT NULL,
    source VARCHAR(80) NOT NULL,
    version VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_practice_question_category FOREIGN KEY (category_id) REFERENCES question_categories(id)
);
CREATE INDEX idx_practice_question_category ON practice_questions(category_id, enabled);

CREATE TABLE practice_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    category_code VARCHAR(40),
    status VARCHAR(20) NOT NULL,
    question_ids_json TEXT NOT NULL,
    total_count INT NOT NULL,
    current_index INT NOT NULL,
    answered_count INT NOT NULL,
    correct_count INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT fk_practice_session_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);
CREATE INDEX idx_practice_session_user ON practice_sessions(user_id, created_at);

CREATE TABLE practice_answers (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(36) NOT NULL,
    selected_answer_json TEXT NOT NULL,
    correct BOOLEAN NOT NULL,
    duration_seconds INT NOT NULL,
    answered_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_practice_answer_session FOREIGN KEY (session_id) REFERENCES practice_sessions(id),
    CONSTRAINT fk_practice_answer_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_practice_answer_question FOREIGN KEY (question_id) REFERENCES practice_questions(id),
    CONSTRAINT uk_practice_answer_question UNIQUE (session_id, question_id)
);
CREATE INDEX idx_practice_answer_user ON practice_answers(user_id, answered_at);

CREATE TABLE user_question_progress (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(36) NOT NULL,
    attempts INT NOT NULL,
    correct_count INT NOT NULL,
    wrong_count INT NOT NULL,
    last_correct BOOLEAN NOT NULL,
    last_answer_json TEXT NOT NULL,
    answered_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_question_progress_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_question_progress_question FOREIGN KEY (question_id) REFERENCES practice_questions(id),
    CONSTRAINT uk_question_progress UNIQUE (user_id, question_id)
);

CREATE TABLE question_favorites (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_question_favorite_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_question_favorite_question FOREIGN KEY (question_id) REFERENCES practice_questions(id),
    CONSTRAINT uk_question_favorite UNIQUE (user_id, question_id)
);

CREATE TABLE practice_shares (
    id VARCHAR(36) PRIMARY KEY,
    share_token VARCHAR(48) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    title VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    view_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_practice_share_token UNIQUE (share_token),
    CONSTRAINT fk_practice_share_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_practice_share_session FOREIGN KEY (session_id) REFERENCES practice_sessions(id)
);

INSERT INTO question_categories VALUES
('cat-general','GENERAL','通用面试','自我介绍、STAR 表达、职业素养','🎯','#5B6CFF',1,TRUE),
('cat-java','JAVA_BACKEND','Java 后端','Java、数据库、缓存与系统设计','☕','#F59E0B',2,TRUE),
('cat-frontend','FRONTEND','前端开发','JavaScript、Vue、性能和工程化','🧩','#10B981',3,TRUE),
('cat-product','PRODUCT','产品经理','需求分析、数据、增长与协作','💡','#EC4899',4,TRUE),
('cat-ai','AI_DATA','AI / 数据','大模型、数据分析与效果评估','🤖','#8B5CF6',5,TRUE),
('cat-scenario','SCENARIO','情景沟通','冲突、压力、协作与管理情景','💬','#06B6D4',6,TRUE);

INSERT INTO practice_questions VALUES
('q-gen-01','cat-general','SINGLE','EASY','面试自我介绍最合适的结构是？','["从出生经历开始完整叙述","岗位匹配结论—相关经历—量化成果—求职动机","只介绍兴趣爱好","重复朗读简历全部内容"]','["岗位匹配结论—相关经历—量化成果—求职动机"]','自我介绍应服务于目标岗位，用有限时间建立匹配度，并留下可追问的证据。','["自我介绍","表达"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-gen-02','cat-general','MULTIPLE','MEDIUM','STAR 回答中应当包含哪些要素？','["情境 Situation","任务 Task","行动 Action","结果 Result","星座 Sign"]','["情境 Situation","任务 Task","行动 Action","结果 Result"]','STAR 用情境、任务、行动、结果组织行为证据，其中行动要突出个人贡献。','["STAR","结构化表达"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-gen-03','cat-general','TRUE_FALSE','EASY','面试中不确定一个专业事实时，应该明确说明边界并给出验证思路。','["正确","错误"]','["正确"]','承认事实边界并说明验证方法，比编造确定答案更能体现专业判断。','["诚信","事实边界"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-java-01','cat-java','SINGLE','MEDIUM','高并发读多写少场景中，引入缓存后首先需要重点设计什么？','["页面颜色","缓存一致性、穿透和失效策略","代码行数","开发者电脑配置"]','["缓存一致性、穿透和失效策略"]','缓存不是简单加速层，需要同时设计一致性、击穿/穿透、雪崩和降级策略。','["Redis","缓存"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-java-02','cat-java','MULTIPLE','HARD','数据库慢查询治理通常包括哪些步骤？','["查看执行计划","确认索引选择性","分析锁等待和数据分布","直接增加所有字段索引","用压测验证改动"]','["查看执行计划","确认索引选择性","分析锁等待和数据分布","用压测验证改动"]','治理需要测量、定位、修改和验证闭环；无差别加索引会增加写放大并可能误导优化器。','["MySQL","性能"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-java-03','cat-java','SCENARIO','HARD','订单服务发布后错误率突然升高，最合理的第一步是？','["立即删除日志","先止损：暂停发布或回滚，并按 Trace/指标定位影响范围","等待用户反馈","直接扩容数据库十倍"]','["先止损：暂停发布或回滚，并按 Trace/指标定位影响范围"]','线上事故优先控制影响，再依据可观测数据定位，避免无证据的破坏性操作。','["稳定性","事故处理"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-front-01','cat-frontend','SINGLE','EASY','Vue 中用于声明响应式状态的常用 API 是？','["ref / reactive","console.log","JSON.stringify","setTimeout"]','["ref / reactive"]','ref 和 reactive 是 Vue Composition API 的核心响应式状态工具。','["Vue","响应式"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-front-02','cat-frontend','MULTIPLE','MEDIUM','改善 Web 首屏性能的有效措施包括？','["路由懒加载","压缩静态资源","合理缓存","把所有包合并成一个超大文件","优化关键渲染路径"]','["路由懒加载","压缩静态资源","合理缓存","优化关键渲染路径"]','首屏优化需要减少关键资源体积和请求阻塞，并结合缓存与加载优先级。','["性能","工程化"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-front-03','cat-frontend','TRUE_FALSE','MEDIUM','只要页面视觉正常，就可以忽略键盘操作和语义化标签。','["正确","错误"]','["错误"]','可访问性是产品质量的一部分，需要支持键盘、焦点、语义和辅助技术。','["可访问性","HTML"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-prod-01','cat-product','SINGLE','EASY','收到“做一个竞品同款功能”的需求后，产品经理首先应该？','["立即画高保真原型","澄清用户问题、业务目标和成功指标","直接排进开发","复制竞品全部细节"]','["澄清用户问题、业务目标和成功指标"]','先确认问题与目标，才能判断功能是否是正确方案以及如何验收。','["需求分析","目标"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-prod-02','cat-product','MULTIPLE','MEDIUM','设计产品指标体系时通常需要？','["明确北极星指标","拆解过程指标","定义统计口径","只看总注册量","设置护栏指标"]','["明确北极星指标","拆解过程指标","定义统计口径","设置护栏指标"]','指标需要目标、过程、口径和护栏共同组成，避免单指标优化带来副作用。','["数据分析","指标"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-prod-03','cat-product','SCENARIO','HARD','核心功能上线后使用率低，最合理的分析顺序是？','["直接下线","先验证曝光—进入—完成漏斗，再结合用户访谈定位原因","给研发扣绩效","立刻买量"]','["先验证曝光—进入—完成漏斗，再结合用户访谈定位原因"]','先用漏斗定位流失环节，再用定性研究解释原因，最后形成可验证方案。','["漏斗","用户研究"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-ai-01','cat-ai','SINGLE','MEDIUM','评估 RAG 问答效果时，除最终答案外还应重点评估？','["显示器尺寸","检索召回质量和引用依据","键盘品牌","代码文件数量"]','["检索召回质量和引用依据"]','RAG 的错误可能来自检索或生成，必须分层评估召回、相关性、忠实度和答案质量。','["RAG","评测"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-ai-02','cat-ai','MULTIPLE','HARD','降低大模型应用幻觉风险的合理方式包括？','["要求引用来源","结构化输出校验","设置人工复核阈值","让模型永远自称百分百确定","构建离线评测集"]','["要求引用来源","结构化输出校验","设置人工复核阈值","构建离线评测集"]','需要从数据、提示、校验、评测和人工兜底多个层次控制风险。','["大模型","安全"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-ai-03','cat-ai','TRUE_FALSE','MEDIUM','模型离线指标提升，必然代表线上业务效果提升。','["正确","错误"]','["错误"]','离线指标与业务指标可能存在偏差，仍需灰度、线上实验和护栏指标验证。','["评测","实验"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-scene-01','cat-scenario','SCENARIO','MEDIUM','你与同事对方案有分歧，最合适的处理方式是？','["私下抱怨","明确共同目标，用事实和实验比较方案并记录决策","拒绝沟通","直接越级投诉"]','["明确共同目标，用事实和实验比较方案并记录决策"]','高质量协作围绕共同目标，以证据降低观点冲突，并保留可复盘的决策记录。','["协作","冲突"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-scene-02','cat-scenario','MULTIPLE','MEDIUM','项目延期风险出现时，负责人应该？','["尽早透明同步","说明影响范围","提供取舍方案","隐藏到最后一天","更新计划和责任人"]','["尽早透明同步","说明影响范围","提供取舍方案","更新计划和责任人"]','风险沟通要及时、具体并带方案，让相关方能够参与取舍。','["项目管理","风险"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP),
('q-scene-03','cat-scenario','SHORT_ANSWER','HARD','请用关键词写出一个完整 STAR 回答应包含的四个部分。','[]','["情境","任务","行动","结果"]','完整 STAR 包含情境、任务、行动、结果；面试中还建议补充复盘和岗位关联。','["STAR","简答"]','平台审核题库','2026.1',TRUE,CURRENT_TIMESTAMP);
