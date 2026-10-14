# Memory Games PK Server

《Memory Games / Brain Training》多人在线 PK 服务端（需求见 `../docs/online-pk-prd.md`）。

## 模块

- `ws/PkWebSocketHandler` — WebSocket 网关（`/ws`）：游客注册/Token 鉴权、单会话踢出、消息路由、注册/认证/房码限流与协议版本门
- `match/PlayerLifecycle` — 每用户单飞状态门（QUEUED/FRIEND_WAITING/MATCHING/IN_MATCH）：一个账号无法同时进入两局或"排队+建房"分叉
- `match/Matchmaker` — 匹配队列：ELO 分桶（±100 起步，等待中逐步放宽），15s 无对手触发 Bot 兜底
- `match/MatchRoom` — 对局状态机：5 轮种子化出题下发、服务端权威计分（按过关流重算，客户端分值表对齐）、回合截止/弃赛/断线判负、重连补发剩余时长
- `match/MatchTerms` — 每局不可变经济/规则快照：热配置只影响新开的对局
- `match/SettlementService` — 单事务幂等结算：双方 ELO/金币/胜负、反作弊标记与战绩一次提交；实例状态 CAS + 战绩唯一(match_id) 双保险
- `match/MatchInstance` / `MatchRecovery` — 对局托管：入场费与 ACTIVE 实例同事务落库；启动时幂等回收上个进程遗留的活动局并退款
- `user/CoinLedger` — 追加式金币流水：入场/派彩/退款/礼金/管理调账每笔可审计
- `game/GameCatalog` — 首发游戏池（6 个）与计分表，从客户端 `updateOnlineScore()` 移植
- `bot` — Bot 由房间内驱动：按段位参数化的作答间隔与正确率
- `rating/Rating` — ELO（K=32）与段位
- `season` — 赛季窗口：条件 UPDATE 收官 + 唯一活动赛季约束 + 单语句软重置，多实例亦不会双重重置
- `user` — JPA 持久化（H2 默认 / Postgres 生产 profile），Flyway 管理结构（`ddl-auto: validate`）

## 构建与测试

```
./gradlew test        # 含双端全量对局集成测试（两个真实 WS 客户端打满 5 轮）
./gradlew pgTest      # PostgreSQL Testcontainers 并发测试（需 Docker，CI 可选）
./gradlew bootJar     # 产物 build/libs/*-boot.jar
```

数据库结构由 Flyway 管理（`db/migration/V1..V6`）；已有部署首次启动时自动 baseline。V4 新增账号身份与设备会话，V5 新增昵称修改冷却，V6 扩大管理审计字段容量。升级前备份数据库，不修改已执行迁移。

## 游客与邮箱账号

游客与邮箱用户共用永久 `pk_user.id`。邮箱验证后原地升级，保留金币、ELO与战绩；账号切换不合并在线资产。`/api/v2` 提供游客安全恢复、旧 token 迁移、注册、登录、刷新、设备会话管理、找回密码与七天注销冷静期。

账号接口默认要求 HTTPS，包括 loopback。隔离本地验收显式设置 `PK_AUTH_ALLOW_HTTP=true`；生产需要 HTTPS/WSS、`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` 与 `PK_AUTH_MAIL_FROM`。没有邮件服务返回 503，不能在默认明文旧游客地址发送账号密码。

见 [账号体系](../docs/account-system.md) 与 [部署和验收](../docs/account-system-deployment.md)。

## 运维特性

- **优雅关闭**：`server.shutdown=graceful`；关闭时所有活动局作废退款（幂等，写 voided 战绩），就绪探针先行转 OUT_OF_SERVICE
- **崩溃恢复**：启动时扫描遗留 ACTIVE 对局实例，按入场费幂等退款（实例状态 CAS + 流水唯一键防双付）
- **可观测性**：`/actuator/health`（含 readiness）、`/actuator/prometheus`（连接数/队列深度/活动房间/结算失败/发送溢出等指标）
- **WS 发送背压**：每会话有界发送队列（256 帧）+ 发送超时，慢消费者被强制断线而非拖垮房间线程
- **管理面**：默认不信任 `X-Forwarded-For`；仅当置于可信代理后时设置 `pk.admin.trust-forwarded-headers=true`

## 部署（海外单机，PRD D-2）

```
docker compose up -d   # app + postgres 16；环境变量 PK_DB_PASSWORD、PK_ADMIN_TOKEN
```

## 运行时配置（管理 API）

端点 GET/PUT /api/admin/config（请求头 X-Admin-Token；docker-compose 经 PK_ADMIN_TOKEN 环境变量设置，
不设置则 Token 访问禁用，但可另配置密码通过网页会话访问）。可热调：回合时长覆盖、入场费、胜/平金币、礼金、Bot 等待、回宽限/间歇、
起局延迟等，免重启即时生效（数值有钳制，变更写审计日志）。已开局对局沿用开局时的
`MatchTerms` 快照，退款/结算不受热配置影响。客户端回合时长完全由服务端驱动
（round_start.durationMs），auth_ok 下发经济参数——release 构建完全服从服务端配置，debug 构建
免金币且可本地兜底时长。

### 管理网页与固定密码

访问 `/api/admin/console`，使用固定管理员密码登录。先在交互终端运行 `JAVA_HOME=F:/jdk17 python tools/admin-password-hash.py`，将输出的 bcrypt 哈希配置到 `PK_ADMIN_PASSWORD_HASH`；没有默认密码，不保存明文密码。网页登录会话默认有效 8 小时，刷新自动恢复；重启使会话失效，重新输入同一密码即可。退出会撤销服务器会话。生产必须配置 HTTPS 与管理网络隔离。

`PK_ADMIN_TOKEN` 仍供自动化工具使用，不作为网页密码；两种凭证独立。网页通过 HttpOnly/SameSite Cookie 与 CSRF 防护访问管理 API。所有管理写操作要求非空白 `reason`，既有回归工具继续用 Token。

使用、Cookie 安全、可信反代、密码哈希生成和部署配置见 [管理控制台](../docs/admin-console.md)。

### 管理端点（P0 已实现）

除下述配置 API 外：`/api/admin/dashboard`（全量状态快照）、`/api/admin/matches`
（列表/详情/强制结算/作废退款）、`/api/admin/users`（搜索/调币/调 ELO/限制/封禁/删除）、
`/api/admin/events`（业务事件流，match_join 带会话 id）、`/api/admin/audit`（变更审计）、
`/api/admin/maintenance`（维护模式，拒绝新对局 503）、`/api/admin/loglevel`（运行时日志级别）。
统一 X-Admin-Token 守卫 + 每 IP 限流（pk.admin-rate-limit，默认 60/min）。详见
`docs/online-pk-admin-design.md`。

客户端默认连 `ws://124.220.35.87:8091/ws`（`OnlineConfig`；云上 8090 被 Halo 占用，PK 服务跑 8091），
debug 构建可在 Online 大厅长按标题改为其他地址（如内网 `ws://192.168.x.x:8090/ws`）真机联调。

## 协议要点（v1）

- 信封 `{"v":1,"t":"<type>","d":{...},"rid":"..."}`：版本不符返回 400；未知类型返回 405 而非静默丢弃
- `rid`（可选请求关联 id）：命令可携带，即时应答（auth_ok/match_queued/room_created/error 等）原样回显，供客户端安全重试
- 注册忽略客户端申报余额，新账户只从服务端礼金开始；重连 `round_start.durationMs` 为当前轮剩余时间
- 计分截止为 回合时长 + `pk.timing.score-accept-grace-ms`（默认 2s），迟到 level_win 不计分
