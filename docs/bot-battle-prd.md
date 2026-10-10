# 人机大战（Play vs Bot）设计方案

> 2026-10-10 定稿，基于 `dev/online-PK` 现有在线 PK 架构（M0+M1+M2+Admin+运行时配置+origin 吸收批次）。
> 关联：`docs/online-pk-prd.md` §4.4（Bot 参数化）、§3.4（Bot 兜底）。

## 1. 现状盘点（可复用资产）

| 资产 | 位置 | 状态 |
|---|---|---|
| Bot 大脑（节拍+概率得分） | `MatchRoom.startBotBrain/botTick` | 已有，固定节拍模型 |
| Bot 兜底（15s 超时配 Bot） | `Matchmaker.scan()` | 已有，仅排位被动触发 |
| Bot rubber-banding（分差驱动命中率漂移） | `MatchRoom.botTick` | 已有 |
| `opponent.isBot` 协议标志 + 客户端识别 | `match_found` 帧 | 已有 |
| Bot 局局前教程（免计时公平坑） | `OnlineSession`（isBot 分支） | 已有，直接复用 |
| 战绩 `botOpponent` 落库标记 | `MatchRecord` | 已有 |
| Bot 按 ELO 推导 W/L/D（STATISTICS 板） | `MatchRoom.opponentInfo` | 已有 |

**现状短板**：
1. Bot 只能被动等 15s 兜底触发，玩家无法主动选择；
2. 难度单一（accuracy 由玩家自己的 ELO 推导）；
3. Bot 比分曲线匀速直线，HUD 上一眼假；
4. 兜底局按排位结算（入场费/ELO），不适合做练习场。

## 2. 定位与总体决策

**定位**：Online tab 的第四入口——练习场 + 冷启动兜底体验。三种 Bot 局语义分离：

| 类型 | 触发 | ELO | 金币 |
|---|---|---|---|
| 排位兜底局 | 排队 15s 超时（现状保留） | 正常 | 正常（入场费/胜局奖励） |
| **显式人机局（本方案新增）** | 玩家选难度，秒配 | **零** | 零入场费，按难度胜局奖励，每日上限 |
| 反作弊限制局 | restrictedMatches>0（现状保留） | 正常 | 正常 |

三个架构决策：
1. **走服务端权威，不做本地模拟**——复用整套 MatchRoom/结算/落库/反作弊；本地模拟要重写回合编排且金币可刷（replica 分支 MatchSimulator 只适合 UI 演示）。
2. **零 ELO**——ELO 只属于排位（含兜底局），人机局动 ELO 等于送分。
3. **免入场费 + 按难度递增的胜局奖励 + 每日奖励局数上限**——可玩性不设限，只管住刷金币。

## 3. 协议与流程

```
客户端                          服务端
  │ bot_match_request ───────────▶ PkWebSocketHandler 新 case
  │   {difficulty:"GOLD"}            │ roomFor(id) 防重入守卫（同 match_join）
  │                                  │ MatchManager.createPracticeRoom(user, ep, difficulty)
  │ ◀───── match_found ────────── 立即（无队列等待）
  │         mode="PRACTICE"
  │         opponent.isBot=true, elo=难度ELO
  │ match_start（START 门保留，Bot 即时接受）
  │ ◀───── opponent_start + round_start ×N ── 照旧
  │ level_win/round_complete ─────▶ 照旧（服务端重算权威分 + 反作弊仍生效）
  │ ◀───── match_end ─────────── settle PRACTICE 分支：eloDelta=0
  │                                  coinDelta=按难度 / 超每日上限则 0
```

**改动点**：
- `ProtocolCodec`：新消息类型 `T_BOT_MATCH_REQUEST`（`bot_match_request`）+ `BotMatchRequest{difficulty}`；`MODE_PRACTICE = "PRACTICE"`。
- `MatchRoom`：`friendMode boolean` 升级为 `mode String`（RANKED/FRIEND/PRACTICE），构造时接收 `BotDifficulty`；`settle` 的 PRACTICE 分支：不收入场费、`delta0=0`、金币按难度查表并受每日上限约束。
- `MatchManager`：新方法 `createPracticeRoom()`——Bot 席 `botElo` 取难度 ELO（不再 `=userA.elo`）。
- 反作弊：`evaluateAntiCheat` 对人机局**保留**（开挂刷金币也要抓）。

## 4. 难度系统（五档，对齐现有段位名）

| 档位 | Bot ELO | 命中率基线 | 作答间隔倍数 | 胜局金币 |
|---|---|---|---|---|
| Bronze | 800 | 0.60 | ×1.5 | 8 |
| Silver | 1000 | 0.68 | ×1.25 | 10 |
| Gold | 1250 | 0.78 | ×1.0 | 14 |
| Diamond | 1500 | 0.86 | ×0.85 | 20 |
| Master | 1800 | 0.92 | ×0.72 | 30 |

- 命中率 = 档位基线 × per-game 修正（P1）：Colors/Like Previous +0.05，回忆型按基线。
- 作答间隔 = `GameCatalog.botLevelIntervalMs` × 档位倍数。
- Bot ELO 同时驱动 STATISTICS 板（`opponentInfo` 的 W/L/D 推导自动生效）。
- 整张表进 `PkProperties` 走 admin 热配（`GET/PUT /api/admin/config`）。

## 5. Bot 行为模型 v2（拟真升级，P1 ✅ 已实现 2026-10-10）

- **动态排程**：「答完一题再排下一题」（`scheduleBotAnswer` 链式调度，单飞任务 + `botEpoch` 失效守卫）替代整场预排等间隔 ticks。
- **截断正态间隔**：`N(μ, σ=0.25μ)` 截断到 `[0.7μ, 1.6μ]`（Box-Muller；兑现 PRD 4.4 的 μ/σ 标定）。
- **开局热身**：前 2 题 interval ×1.3（真人读题）。
- **疲劳**：回合后半 interval ×1.1。
- **失误簇**：每题 8% 概率进入连错态 1~2 题（命中率 ×0.3）。
- **rubber-banding 保留**，按难度相对夹紧 `[基线−0.10, 基线+0.05]`（排位兜底 bot 保持原 [0.45, 0.95]）。
- **per-game 修正**：Colors("19")/Like Previous("15") 命中率 +0.05，其余按基线。

## 6. 反刷分治理

- 每日奖励局数上限 `practiceDailyRewardCap`（默认 5，热配）：settle 时统计当日该用户 PRACTICE 胜局数（MatchRecord 查询），超限 `coinDelta=0`，对局照常。（✅ 已随 P0 落地）
- 平局奖励 = 胜局金币的一半。（✅ 已随 P0 落地）
- **限制局经济语义（P2 拍板）**：反作弊限制局（restrictedMatches）**保持排位语义**（扣入场费、动 ELO），不并入 PRACTICE 经济——限制是惩罚，若改走零 ELO + 有奖金币路线等于给作弊者送无风险刷币渠道。
- **人机连胜 streak（P2）**：`UserAccount.practiceStreak`（可空列，老行视为 0）只随 PRACTICE 结果增减（胜 +1、非胜清零；排位/好友局不动它）；经 auth_ok 与 match_end 下发，难度卡显示「Win streak: N」（≥2 时）。
- **难度卡数值同步（P2）**：auth_ok 携带 `practiceTiers[]`（id/elo/winCoins），客户端卡片区实时镜像 admin 热配；档位行带段位彩点徽章（bronze/silver/gold/diamond/master）。

## 7. 客户端改动

- 大厅入口：`OnlineLobbyAdapter` 加同款 180dip 绿 `button_play` 菜单项（新文案 `menu_online_play_bot`，风格对齐 `menuOnlinePlay`；原版无此文案，属新造资产）。
- 难度选择卡：`OnlineDialogs` 新增，照抄输码卡配方（白卡 + 五行段位名 + ELO/奖励提示 + 取消），复用 Bronze..Master 段位名。
- 零入场费 → 人机入口跳过金币不足检查；首跑教程 pref 检查保留。
- `OnlineSession`：`sendBotMatchRequest(difficulty)` + `practiceMode` 标志（`match_found.mode==PRACTICE` 置位、`match_end` 复位）；局前教程复用 bot 局分支。
- 结算差异：ELO 行显示 +0；History 页 PRACTICE 局标记区别于排位兜底局。

## 8. 测试计划

- **集成**（`gradlew -p server test`，新增 `BotPracticeIntegrationTest`）：`bot_match_request` → 断言 `match_found` 的 mode/isBot/elo → 打完 5 轮 → 断言零 ELO、金币=难度奖励、`MatchRecord.mode=PRACTICE`、超每日上限胜局金币为 0。
- **真机**：入口→难度卡→秒配→完整局→结算 +0 ELO 行（`tools/waitui.py` 轮询法）。
- **压测**：`pk_load.py` botmatch 场景直接复用。

## 9. 分期

| 期 | 内容 | 状态 |
|---|---|---|
| **P0** | 协议 + 服务端 PRACTICE 模式 + 五档难度 + 大厅入口/难度卡 + 零 ELO 结算 + 集成测试 | ✅ 2026-10-10（含 NAS 部署 + 双路径真机验证） |
| **P1** | Bot v2 拟真（动态排程/截断正态/热身/失误簇）+ per-game 修正 | ✅ 2026-10-10 |
| **P2** | 每日奖励上限（✅ 已随 P0 落地）+ Bot 段位徽章 + 人机连胜 streak + 限制局经济语义（待议） | 待做 |
