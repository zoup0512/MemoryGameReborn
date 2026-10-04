# 在线 PK 服务端 Admin 管理功能设计

| 项目 | 内容 |
|---|---|
| 版本 | v1.1（P0 已实现并部署 LAN 验证；P1/P2 待排期） |
| 日期 | 2026-10-04 |
| 关联 | `docs/online-pk-prd.md` §4/§6.3（现有运行时配置 API）、`server/README.md` |
| 现有基线 | `AdminConfigController`（token 守卫的热改配置，已上线） |

---

## 1. 背景

M2 后服务端已具备完整业务面（匹配/对局/结算/赛季/反作弊），管理面只有一个最小配置 API。联调期暴露的运维缺口（均真实发生过）：

| 事件 | 缺口 |
|---|---|
| 服务端进程两次被静默击杀，半小时后才发现 | 无存活监控/心跳可见性 |
| 幽灵 match_join（对局退出时刻偶发队列条目）至今未定位 | 无法在线查看队列/近期事件，只能翻日志文件 |
| kill -9 导致 H2 丢注册数据 | 无优雅停机状态指示、无数据快照 |
| 一轮"验证成功"因 jar 未上传而作废 | 无版本/构建信息端点，部署核对靠手工 md5 |
| 观测对局只能看 settle 日志行 | 无对局列表、无在线用户视图 |

## 2. 目标与非目标

**目标**
1. 单管理员通过 HTTP API + 极简 Web 页完成日常运营：看状态、管对局、管用户、改配置、处理反作弊、控赛季。
2. 所有变更可审计、可回溯。
3. 与业务面同进程部署（NAS 单机友好），管理面故障不影响对局面。
4. 为幽灵 match_join 这类疑难问题提供在线排查手段。

**非目标（本期不做）**
- 多租户/多服务器集群管理。
- 计费、支付、道具发放（游戏无内购）。
- 大数据分析平台（只做计数器快照，不做时序库）。

## 3. 总体架构

```
                     ┌────────────────────────────────────┐
  手机客户端 ──WS──►  │  PkServerApplication (单进程)       │
                     │  业务面: /ws, 对局/匹配/结算         │
  管理员 ──HTTP──►   │  管理面: /api/admin/** (token+白名单)│
                     │    ├── AdminConfigController (已有) │
                     │    ├── AdminDashboardController (新) │
                     │    ├── AdminMatchController (新)     │
                     │    ├── AdminUserController (新)      │
                     │    ├── AdminSeasonController (新)    │
                     │    ├── AdminOpsController (新)       │
                     │    └── AdminAuditService + EventRing (新) │
                     └────────────────────────────────────┘
```

**关键决策**

| 决策 | 选择 | 理由 |
|---|---|---|
| 管理端口 | P0 与业务同端口（路径前缀区分），P2 可选独立 `management` 端口 | 单机部署简单；海外生产用反代路径 + IP 白名单即可隔离 |
| 鉴权 | 静态 token（沿用 `pk.admin-token`）→ P2 升级为账号+角色 | 单管理员规模下静态 token 够用，端点已隐藏（无 token 404） |
| 审计 | 每个变更操作写 `PK_ADMIN_AUDIT` 表 | H2 已在，成本最低 |
| 事件流 | 进程内环形缓冲（最近 1000 条业务事件）| 排查幽灵 match_join 类问题；P2 再考虑落盘 |
| UI | 服务端内嵌单页 HTML（`/api/admin/console`） | 免独立前端部署；P0 先不做 UI 只出 API，P1 补页面 |

## 4. 功能模块

### 4.1 仪表盘（AdminDashboardController）

只读快照，`GET /api/admin/dashboard`：

```json
{
  "server": {"version": "0.2.0", "startedAt": 1696..., "upSeconds": 8123,
             "javaVersion": "17.0.19", "jarMd5": "c59199..."},
  "online": {"wsConnections": 4, "authenticatedUsers": 3},
  "matchmaking": {"queueDepth": 1, "queuedUsers": [{"userId": 7, "nickname": "P4040", "waitingMs": 3200}],
                  "botFallbacksToday": 6, "avgQueueWaitMsToday": 8420},
  "matches": {"active": 2, "byState": [{"matchId": "ab12..", "round": 3, "mode": "FRIEND",
               "seats": ["P8872","P4040"], "scores": [120, 98], "roundEndsInMs": 41200}]},
  "economy": {"entryFee": 0, "winCoins": 50, "balanceTop": [{"nickname": "P8872", "balance": 230}]},
  "season": {"index": 1, "endsAt": 1735.., "daysLeft": 29},
  "anticheat": {"restrictedNow": 1, "strikesToday": 2},
  "events": ["12:50:05 bot fallback user=1", "12:52:00 match 7edf.. settled 0/4898", "..."]
}
```

实现挂点：`MatchManager.rooms`/`roomByUser`、`Matchmaker` 队列（加只读快照方法）、`PkWebSocketHandler` 会话注册表、`SeasonService.snapshot()`、`GameCatalog`。`events` 来自 EventRing。

**计数器**（进程内 `AtomicLong`，重启清零）：matchesStarted/settled、botFallbacks、queueJoinTotal、queueWaitMs 累计、levelWinsTotal、anomaliesTotal、adminOpsTotal。

### 4.2 对局管理（AdminMatchController）

| 端点 | 说明 |
|---|---|
| `GET /api/admin/matches` | 活跃对局列表（matchId、模式、双方、当前轮、比分、回合剩余时间、断线状态） |
| `GET /api/admin/matches/{id}` | 单局详情：轮次计划（游戏/种子/时长）、每轮得分、事件时间线（来自 EventRing 过滤） |
| `POST /api/admin/matches/{id}/settle` | 强制结算：`{"reason": "ADMIN", "refund": true}`——正常按分判定；`refund=true` 时退还双方入场费（当前按 server 经济值退） |
| `POST /api/admin/matches/{id}/terminate` | 强制作废：不计 ELO/胜负、退还入场费、双方收 `match_end`（result 取当前分判定但 eloDelta=coinDelta=0，payload 加 `voided: true` 标记） |

`MatchRoom` 需新增 `adminSettle(boolean refund)` / `adminTerminate()`（复用 `settledOnce` 幂等闸；绕过正常轮转路径，直接进结算段）。

### 4.3 用户管理（AdminUserController）

| 端点 | 说明 |
|---|---|
| `GET /api/admin/users?q=P40` | 按 id/uuid/昵称模糊搜索，返回档案+ELO/战绩/余额/suspicion/restrictedMatches/bannedUntil/在线状态 |
| `GET /api/admin/users/{id}` | 详情 + 最近 20 局（复用 MatchRecord 查询）+ 账号事件（注册/限制/解禁，来自审计表过滤） |
| `POST /api/admin/users/{id}/balance` | `{"delta": 100, "reason": "补偿"}`——服务端权威发/扣币，写 coin 流水审计 |
| `POST /api/admin/users/{id}/elo` | `{"set": 1200, "reason": "..."}`——直接设定 ELO（赛季纠纷处理） |
| `POST /api/admin/users/{id}/restrict` | `{"matches": 10}`——手动设仅 Bot 限制；`{"matches": 0}` 解除 |
| `POST /api/admin/users/{id}/ban` / `unban` | `{"hours": 24}`——封禁窗口；封禁后 `auth` 拒绝（错误码 403）、活跃对局按断线判负路径处理 |
| `DELETE /api/admin/users/{id}` | 账号删除（合规）：抹除昵称/uuid 关联，战绩保留但昵称置 `deleted`；拒绝后续 auth |

`UserAccount` 需新增：`bannedUntil long`、`deleted boolean`。`UserService`/WS 鉴权处增加封禁与删除检查。

### 4.4 配置管理（扩展现有 AdminConfigController）

在已上线的热改 API 上补齐：

| 增补 | 说明 |
|---|---|
| `GET /api/admin/config/history` | 近 50 次配置变更（审计表过滤 action=CONFIG） |
| `POST /api/admin/config/preset/{name}` | 预设一键应用：`production`（30-80s 原版时长/30 入场）、`test-fast`（90s/0 入场）、`load-test`（20s/0/短间隔）；预设在 yml 定义 |
| 持久化选项 | `PUT` 支持 `"persist": true`——写入 `data/runtime-config.json`，重启后由 boot 加载（默认不持久，重启回 yml 默认——保持现有语义） |

### 4.5 反作弊运营（并入 AdminUserController + 仪表盘）

- `GET /api/admin/anticheat/flagged`——当前 suspicion>0 或 restrictedMatches>0 名单 + 触发时间与当时对局。
- 复核流：管理员查看该局 `level_win` 时间线（对局详情已含）→ 人工判定 `restrict`（确认）或 `clear`（清 suspicion + 解除限制 + 退还当局入场费）。
- `MatchRoom.evaluateAntiCheat` 触发时写一条事件进 EventRing 与审计（`ANTICHEAT_STRIKE`），复核有据可查。

### 4.6 赛季管理（AdminSeasonController）

| 端点 | 说明 |
|---|---|
| `GET /api/admin/seasons` | 当前赛季 + 历史赛季列表（起止、结算时间） |
| `POST /api/admin/seasons/current/finalize` | 手动收官：立即执行软重置 + 开新季（复用 `SeasonService` 收尾逻辑；需加 `forceFinalize()` 并保证与 `activeSeason()` 的并发安全——同一事务/锁内） |
| `GET /api/admin/seasons/{index}/top?n=20` | 某季 Top N（P1：需按季快照 ELO——收官时把全表写 `PK_SEASON_SNAPSHOT`；P0 只返回当前榜） |

### 4.7 公告与维护模式（AdminOpsController）

| 端点 | 说明 |
|---|---|
| `POST /api/admin/maintenance` | `{"enabled": true, "message": "维护中，20:00 恢复"}`——开启后 `match_join` 返回错误码 503（进行中的对局允许打完）；客户端提示语用 message |
| `POST /api/admin/announce` | `{"text": "赛季 2 开启！", "ttlSec": 60}`——向所有在线 WS 广播 `server_notice` 帧；客户端 Toast 展示（**需客户端配合，P1 排期**） |
| `POST /api/admin/loglevel` | `{"logger": "com.memorygames.pk", "level": "DEBUG"}`——运行时改日志级别（排查幽灵 match_join 必备） |
| `GET /api/admin/logs/tail?n=200` | 返回 EventRing + 最近日志行（logback 内存 appender，容量 500 行） |

### 4.8 审计与事件（横切）

- `PK_ADMIN_AUDIT` 实体：`id, ts, action, paramsJson, operatorIp, result`。所有 `POST/PUT/DELETE` 管理操作必须写。
- `EventRing`（进程内，容量 1000）：订阅关键业务事件——`auth`、`match_join`（**含来源会话 id——直接服务幽灵 match_join 排查**）、`match_found`、`round_start/settle`、`level_win` 异常、封禁/限制触发。通过 WS handler 与 MatchRoom 的既有日志点并行埋点（同一处 `log.info` 旁加一行 ring.add，或封装 `Events.emit()`）。

## 5. 数据模型扩展

```
PK_ADMIN_AUDIT(id, ts, action, params_json, operator_ip, result)
PK_SEASON_SNAPSHOT(season_index, user_id, nickname, elo_final, wins, losses, draws, rank)   -- P1
UserAccount 新增: banned_until(long, 0=未封), deleted(boolean)
data/runtime-config.json（可选持久化配置）
```

## 6. 安全设计

| 层 | 措施 |
|---|---|
| 网络 | 生产仅经反向代理暴露 `/api/admin/**`（HTTPS + IP 白名单）；NAS 测试环境限内网 |
| 认证 | `X-Admin-Token`（现有）；token 为空=端点群整体 404 |
| 限流 | 每 IP 30 req/min（简单令牌桶，防爆破 token） |
| 校验 | 所有数值钳制（沿用 config 的做法）；用户操作 reason 必填 |
| 审计 | 变更操作 100% 落库；`GET /dashboard` 等只读不记 |
| 客户端影响面 | 封禁/维护码需客户端识别（403/503 已走 error 帧）；`server_notice` 需客户端新增展示（P1） |

## 7. 分期计划

| 阶段 | 内容 | 出口标准 |
|---|---|---|
| **P0（1 周）** | 仪表盘全量、对局列表/强制结算/作废、用户查询/封禁/调币调 ELO/限制管理、审计落库、EventRing、维护模式、loglevel | 全部端点带集成测试；用仪表盘+事件流复现一次幽灵 match_join 并定位 |
| **P1（1 周）** | 内嵌 Web 控制台单页、配置预设与持久化选项、赛季手动收官+快照、公告广播（含客户端 Toast）、反作弊复核流 | 管理员全程不碰 curl 完成一次"封号+补偿+强制结算"操作 |
| **P2（按需）** | 独立管理端口、账号+角色多管理员、Prometheus 指标导出、告警（进程存活/错误率）、IP 白名单细化 | 上生产前完成 |

## 8. 与现有代码的衔接点

| 模块 | 改动 |
|---|---|
| `PkWebSocketHandler` | 会话注册表加只读快照；`match_join`/`auth` 处 emit 事件（含会话 id）；封禁/维护检查 |
| `MatchManager`/`MatchRoom` | 活跃对局快照方法；`adminSettle/adminTerminate`；结算路径 emit 事件 |
| `Matchmaker` | 队列快照；bot fallback 计数 |
| `UserService` | 封禁/删除/调币调 ELO 方法（全部 @Transactional + 审计由调用方记） |
| `SeasonService` | `forceFinalize()`（与 `activeSeason()` 互斥，synchronized 已够） |
| `AdminConfigController` | 增 history/preset/persist（见 4.4） |
| 新增 | `AdminAuditService`、`EventRing`、其余 4 个 Controller、`AdminAudit` 实体、logback 内存 appender |

## 9. P0 实现记录（2026-10-04，已上线 LAN）

P0 范围全部落地（16 个集成测试绿）：
- 仪表盘 `/api/admin/dashboard`（版本/jarMd5/在线数/队列/活跃对局快照/经济/赛季/反作弊计数/进程内计数器/事件尾）
- 对局 `/api/admin/matches`（列表/详情含时间线/`settle?refund`/`terminate` 作废——退入场费、零 ELO、不计胜负）
- 用户 `/api/admin/users`（搜索/详情+近 20 局/调币/调 ELO/限制/封禁解封/删除）
- 运维 `/api/admin/events|audit|maintenance|loglevel`
- 审计表 `PK_ADMIN_AUDIT`（全部变更落库）；EventRing 1000 条（**match_join 带会话 id**——幽灵排队排查就绪）
- 统一守卫：token + 每 IP 限流（`pk.admin-rate-limit`，默认 60/min，超限 429）
- UserAccount 新增 `banned_until`/`deleted`；维护模式拒绝新 match_join（503+文案）

**运维注记**：H2 `ddl-auto:update` 对已有表加 NOT NULL 列会失败且不回填——升级含新列的版本时
必须重置数据目录或手工 `ALTER ... SET DEFAULT`；本次 LAN 部署即因旧库 + 孤儿 java 进程
（reparent 到 init 后躲过 pkill 模式）排查了一轮，清理需 `pgrep` 后按 PID kill -9。

## 10. 风险与开放问题

1. **幽灵 match_join 尚未定位**——EventRing 的会话级事件是 P0 最高优先级的动机；若 P0 期间定位并修复，事件流仍长期保留为排障基建。
2. H2 单文件下管理查询与对局写库并发——管理查询全部走只读快照/索引查询，避免锁竞争（赛季缓存教训）。
3. 内嵌 Web 控制台的 XSS 面——单页模板全部服务端转义，token 走 header 不落 localStorage。
4. 客户端配合项（公告 Toast、403/503 文案）排期在 P1 客户端小版本。
