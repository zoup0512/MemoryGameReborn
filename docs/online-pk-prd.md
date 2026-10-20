# 《Memory Games / Brain Training》多人在线 PK 模式需求文档

| 项目 | 内容 |
|---|---|
| 文档版本 | v1.0（草案） |
| 日期 | 2026-10-02 |
| 状态 | 待评审 |
| 适用范围 | 客户端（本工程）+ 全新服务端 + 运维部署 |
| 关联代码基线 | main @ ab47849（离线化改造完成后） |

---

## 1. 背景与现状

### 1.1 项目现状

本工程是 2018 年老应用 `com.memory.brain.training.games` 的反编译离线复刻版，已完成离线化改造并真机验证通过。当前形态：

- **玩法**：23 个益智小游戏，覆盖 6 大脑力类别（Memory / Speed / Problem Solving / Attention / Flexibility / Imagination），注册于 `Games.java`。
- **模式**：Sprint（关卡冲刺）/ Workout（每日训练）/ Challenge（挑战）/ Menu 四个板块，底部导航定义于 `src/main/res/xml/bottombar_tabs.xml`。
- **架构**：每个游戏是独立 `Activity`（`games/Game1~23*Activity.java`），内部用 `GameFlowState` 状态机 + `GameFlowStateTimer` 驱动流程，关卡难度由 `logic/GameProgression1~12/21` 控制，随机源统一走 `logic/GameRandom`。
- **数据**：ActiveAndroid（SQLite）本地库。`api/local/model/LocalUser` 保存用户资料、金币（`money`）、评分（`rating` / `challengeRating`）；`api/local/LocalDataManager` 管理金币交易流水。
- **网络**：**无**。原版全部联网逻辑（在线对战、战绩上传、S3、Firebase 推送）在离线化时移除，Manifest 权限为 0 项。

### 1.2 代码考古：原版本就有在线对战（重要）

离线化清理时保留了原版在线模式的数据层骨架，这既是产品形态的历史依据，也是本次可以直接复用的资产：

| 遗产 | 位置 | 含义 |
|---|---|---|
| `onlineRank`、`rating`、`unlimitedOnline` 字段 | `LocalUser.java:42-51` | 原版有段位排名与"无限在线"订阅位 |
| `chargeForOnline()` / `refundForOnline()` | `LocalDataManager.java:176-185` | 原版在线对战**入场费 30 金币**，异常退款 +30 |
| `TYPE_START_ONLINE` / `TYPE_WIN_ONLINE` / `TYPE_DRAW_LEVEL("DrawOnline")` 交易类型 | `LocalDataManager.java:52-61` | 原版金币流水区分开局/胜利/平局 |
| `ANALYTICS_CATEGORY_ONLINE = "Online Games"` | `MemoryApplicationModel.java:46` | 原版有在线对局埋点体系 |
| `GameRandom.init(long seed)` | `logic/GameRandom.java` | **随机源天然支持种子注入**——原版"双方同题"的技术基础，本次可直接复用 |
| 底部导航曾含 Online tab | `bottombar_tabs.xml`（离线化时移除） | 原版在线模式是一级入口 |

**结论**：本次改造本质是"重建原版在线对战"，而非发明新玩法。经济数值（30 金币入场）、数据字段（rating/onlineRank）均可对齐原版，降低设计风险。

### 1.3 目标与非目标

**目标**

1. 为单机游戏增加多人在线 1v1 实时 PK 模式，作为独立一级入口，与现有单机模式互不干扰。
2. 建立账号、匹配、段位、金币经济闭环，形成"单机练手 → 在线对战"的成长循环。
3. 服务器判题 + 服务端权威计时，保证公平性达到主流脑力对战产品水准。

**非目标（本期不做）**

- 多人（>2）同场对战、团队战。
- iOS / 跨平台客户端。
- 内购充值（`unlimitedOnline` 订阅位保留字段但不出售）、广告变现。
- 语音/自由文字聊天（只做快捷语，见 P2）。
- 现有 23 个单机玩法的改动（PK 池内游戏仅做"在线模式分支"，不改变单机行为）。

---

## 2. 用户与场景

**目标用户**：现有单机玩家（已完成关卡进度、持有金币存量）+ 被"与人比拼"吸引的回归玩家。

**核心用户故事**

- US-1：作为一名单机玩家，我想随机匹配一位水平相近的对手实时比拼同一组题目，赢了涨段位赢金币，让我有持续挑战的动力。
- US-2：作为一名玩家，我想通过房间码邀请朋友对打，不用加好友也能约战。
- US-3：作为一名玩家，即使 30 秒内没人匹配，我也希望立刻开局（机器人兜底），不想干等。
- US-4：作为一名玩家，对局中我接电话/切后台回来，对局还在、比分还在，我可以继续打或体面认输。
- US-5：作为一名玩家，我断网时单机模式一切照常，只有 PK 入口提示不可用。

---

## 3. 产品需求

### 3.1 总体玩法形态（推荐方案）

**实时 1v1 同题竞速对战**（原版同款形态）：

- 一局对战 = **5 轮**；每轮从 PK 游戏池中按种子随机抽出 1 个游戏 × 1 组题。
- 双方拿到**完全相同的题目**（服务端下发同一 `seed`，两端调用 `GameRandom.init(seed)` 确定性复现关卡），各自作答，互见进度。
- 每轮限时 30–60 秒（按游戏而定，见 3.7 表）；轮末展示双方本轮得分与累计比分。
- 5 轮打完，总分高者胜。

> 备选形态"异步挑战（好友发战书、对方离线作答）"列入 P2（见 3.11），不作为主形态：实时对抗的留存拉动远强于异步，且实时对局的服务端判题框架天然覆盖异步。

### 3.2 功能清单与优先级

| 编号 | 功能 | 优先级 |
|---|---|---|
| F-01 | 游客账号（设备绑定 + 昵称/头像本地设置） | P0 |
| F-02 | 随机匹配（按 ELO 分段 + 机器人兜底） | P0 |
| F-03 | 1v1 实时对局（5 轮同题竞速、比分浮层、对手进度） | P0 |
| F-04 | PK 游戏池（首发 6 个，见 3.7） | P0 |
| F-05 | 结算页（胜负、得分明细、ELO 变化、金币结算） | P0 |
| F-06 | ELO 段位系统（青铜→大师） | P0 |
| F-07 | 金币经济（入场费/奖金，复用原版数值） | P0 |
| F-08 | 断线重连（90 秒窗口）+ 认输 | P0 |
| F-09 | 超时/逃跑判定（服务端权威） | P0 |
| F-10 | Online 一级入口（恢复底部 tab）+ 匹配大厅页 | P0 |
| F-11 | 战绩历史页（最近 20 局） | P1 |
| F-12 | 好友房间（房间码创建/加入对战） | P1 |
| F-13 | 赛季与段位结算 | P1 |
| F-14 | 排行榜（全球/周边） | P2 |
| F-15 | 快捷语互动（GG / 加油等预设短语） | P2 |
| F-16 | 异步挑战（战书） | P2 |

### 3.3 账号系统（F-01）

- **游客账号**：首次进入 PK 模块时客户端生成 UUID，向服务端注册换取 `userId + token`（`LocalUser.token` 字段复用），写入本地 SQLite。卸载重装 = 新账号（P0 接受此损失；P1 可加"账号绑定"找回）。
- **资料**：昵称（默认"玩家xxxx"、可改、2–16 字符）、头像（本地预置头像包，不上传图片）。
- **登录态**：token 过期自动静默重注册；一个 token 同一时刻仅允许一个 WebSocket 会话，后登录踢前登录。

### 3.4 匹配系统（F-02）

1. 玩家点击"快速匹配"→ 进入匹配队列（Redis），按 ELO 分段（±100 内优先）。
2. 等待 **8 秒**未有真实对手 → 逐步放宽分段（±200/±400）。
3. 等待 **15 秒**仍无对手 → **机器人兜底**：服务端生成 Bot，其作答延迟与正确率按玩家当前段位参数化（见 4.4），玩家无感知。
4. 匹配成功双方收到 `match_found`（含对局 `roomId`、5 轮游戏清单、每轮 `seed`、对手资料与段位）→ 双方确认加载 → 3-2-1 倒计时开局。
5. 任一方在倒计时结束前退出 = 取消对局，无惩罚（金币未扣或原路退回）。

### 3.5 对战全流程（时序）

```
[客户端A]                [服务端]                [客户端B]
   │── auth(token) ─────────►│◄───── auth(token) ──│
   │── match_join ──────────►│◄───── match_join ────│  （各扣30金币入场费）
   │◄─ match_found(room, seeds, opponent, rounds) ─►│
   │── ready ───────────────►│◄───── ready ─────────│
   │◄─ round_start(r=1..5, gameId, seed, deadline) ─►│  （服务端权威倒计时）
   │── answer(item, choice, ts) ─►  …校验/计分…  ◄──│
   │◄─ opponent_event(得分/连击) ──────────────────►│  （准实时互见）
   │◄─ round_end(r, 双方本轮得分) ──────────────────►│
   │        … 重复 5 轮 …                              │
   │◄─ match_end(胜负, 比分明细, eloDelta, coins) ───►│
```

### 3.6 对局规则细则

**计分（每轮内）**

| 项 | 分值 |
|---|---|
| 单题答对 | +100 |
| 连对加成 | 每连击 +10（上不封顶，答错清零） |
| 速度分 | 每题按剩余时间比例加 0–50 |
| 答错 | 0 分且连击清零（不倒扣） |

**胜负与平局**

- 5 轮总分高者胜；总分相同为平局。
- 平局：双方入场费各退一半（15 金币），ELO 各按 0.5 胜期望微调（±3 以内）。

**超时与逃跑**

- 单轮倒计时以**服务端**为准；时间到未答完按已答计分，自动进入下一轮。
- 对局中主动退出：弹出二次确认"退出将判负"。
- 断线超过 **90 秒**重连窗口 → 判负；对手正常结算胜利。
- 逃跑/判负方：损失入场费，ELO 正常扣减；累计逃跑率进入风控标记（见 4.5）。

**暂停**

- 在线模式禁用 `PauseDialog`（单机的暂停按钮在在线分支隐藏）：对局计时不停，切后台回来仍按服务端时间续打（见 4.6）。

### 3.7 PK 游戏池（23 个游戏逐个评估）

评估标准：① 题式化（离散题目、可判定）② 单题时长 3–15 秒 ③ 随机源走 `GameRandom`（可种子复现）④ 双端可同题。

| 游戏 | 类别 | 结论 | 说明 |
|---|---|---|---|
| Game1 Memory Grid | Memory | **首发** | 经典记忆重现，题式标准 |
| Game20 Colors | Attention | **首发** | Stroop 色词干扰，竞速天然 |
| Game23 Schultz Tables | Speed | **首发** | 舒尔特表，竞速经典 |
| Game11 Correctly? | Speed | **首发** | 算式判断，作答密度高 |
| Game16 Like Previous? | Memory | **首发** | 连续记忆，连击机制契合 |
| Game5 Count'em All | Problem Solving | **首发** | 速算计数 |
| Game4 Who's New? | Attention | 二批 | 视觉搜索，可直接进 |
| Game10 One and Only | Attention | 二批 | 同上 |
| Game13 All the Same | Attention | 二批 | 同上 |
| Game9 Find the Picture | Memory | 二批 | 图片素材需同 seed 复现，验证后进 |
| Game21 Symmetry | Problem Solving | 二批 | 题式标准 |
| Game12 More, Less | Problem Solving | 二批 | 题式标准 |
| Game19 Find All | Attention | 二批 | 题式标准 |
| Game3 Hexagons | Memory | 二批 | 需验证动画节奏同步观感 |
| Game6 Follow the Path | Memory | 二批 | 路径展示动画期间对手进度需静默 |
| Game2 Rotating Grid | Memory | 改造后进 | 旋转动画耗时长，需压缩动画/固定节奏 |
| Game7 Image Vortex | Flexibility | 改造后进 | 图形推理，判定逻辑需从 UI 剥离 |
| Game8 Catch Them | Attention | 改造后进 | 连续实时玩法，需改题式化计分 |
| Game15 Paper Planes | Flexibility | 改造后进 | 同上 |
| Game22 Mirrors(Laser) | Problem Solving | 改造后进 | 关卡式，需题组化 |
| Game17 248 | Problem Solving | **不进池** | 无尽合并，单局长不可控 |
| Game18 Pyramids | Problem Solving | **不进池** | 剧情关卡型（`Games.java` 中带额外标记），不适合同题竞速 |

首发 6 个覆盖 Memory / Speed / Attention / Problem Solving 四类，全部满足"出题纯逻辑、无长动画"，改造成本最低。

### 3.8 段位与天梯（F-06）

- ELO 初始 **1000**，K=32（复用 `LocalUser.rating`，注意与单机 `challengeRating` 分开：线上分 = `rating - challengeRating` 的口径在 `LocalDataManager` 已有先例，服务端为准、客户端只展示）。
- 段位：青铜（<1100）/ 白银 / 黄金 / 铂金 / 钻石 / 大师（≥2000），每 200 分一档。
- 匹配保护：新玩家前 5 局只与机器人或同为新手的玩家匹配。

### 3.9 金币经济（F-07）

对齐原版数值（`LocalDataManager.chargeForOnline`）：

| 事件 | 金币 | 对应流水类型 |
|---|---|---|
| 进入对局（匹配成功时） | −30 | `StartOnline`（已有） |
| 胜利 | +50 | `WinOnline`（已有） |
| 平局 | +15 | `DrawOnline`（已有） |
| 失败 | 0 | 新增 `LoseOnline`（原版缺失，补齐做流水审计） |
| 逃跑判负 | 0（不退入场费） | `LoseOnline` + 逃跑标记 |
| 金币不足 30 | 引导打单机赚金币（原版已有赚币途径），**不提供充值** | — |
| 新用户首次进入 PK | 赠 60（对齐原版首启赠币） | `FirstStart`（已有） |

金币余额为服务端权威，客户端 `LocalUser.money` 做缓存展示，对局后以服务端结算覆盖。

### 3.10 战绩与个人主页（F-11）

- PK 主页展示：段位徽章、ELO、胜/负/平、连胜、逃跑率、金币余额。
- 最近 20 局历史：对手昵称段位、比分、用时、结果，本地缓存 + 按需拉取。

### 3.11 好友与社交（P1/P2）

- **P1 房间码**：创建房间生成 4 位数字码，好友输入加入，房主开局；同分段不强制（好友局 ELO 变动减半）。
- **P2**：排行榜（全球 Top100 + 自己排名）、快捷语（对局内 6 条预设短语，1 次/10 秒防刷屏）、异步战书。

---

## 4. 技术方案

### 4.1 总体架构

```
┌─────────────── Android 客户端（本工程） ────────────────┐
│ 单机模块（现状不动）                                     │
│ PK 模块：OnlineClient(WS) │ OnlineSession │ 对局桥接层    │
│ LocalDataManager（金币/ELO 缓存）                        │
└──────────────┬──────────────────────────────────────────┘
               │ WSS（JSON 信令，长连接 + 心跳）
┌──────────────▼─────────────── 服务端（全新） ────────────┐
│ Gateway（会话/鉴权/路由）                                 │
│ Matchmaker（队列 + 分段 + Bot 兜底）                      │
│ Room（对局编排：轮次状态机、权威计时、事件广播）           │
│ GameMaster（seed 生成 + 期望答案预计算 + 判题校验）        │
│ Ledger（金币/ELO 结算，事务落库）                          │
│ Bot（参数化作答模拟器）                                   │
│ PostgreSQL（账号/战绩） + Redis（队列/房间态/在线会话）    │
└──────────────────────────────────────────────────────────┘
```

### 4.2 客户端改造点（贴合现有代码）

新增包 `com.cube.memorygames.api.online`，不侵入现有 `api.local`：

1. **网络层**：引入 `org.java-websocket:Java-WebSocket:1.5.x`（纯 Java、零传递依赖，与 support 26.1.0 / Java 8 无冲突；**不用** OkHttp 以避免引 AndroidX 风险）。`OnlineClient` 封装连接、心跳（10s ping/pong）、自动重连、信令收发；JSON 序列化沿用工程内 **gson 2.7**。
2. **会话层**：`OnlineSession` 单例（未连接/已连接/匹配中/对局中/结算 状态机），持有当前 `roomId`、轮次上下文，是断线重连的恢复锚点。
3. **入口**：`bottombar_tabs.xml` 恢复第 5 个 tab（Online，icon `@id/bottomBarOnline` 若 `public.xml` 仍有保留则复用 id），`MainMenuActivity` 增加 PK 大厅 Fragment（段位卡 + 快速匹配按钮 + 战绩入口）。
4. **对局桥接层**（本方案核心改造）：定义 `OnlineMatchContext` 接口：

   ```java
   public interface OnlineMatchContext {
       void onAnswer(int itemIndex, boolean correct, long elapsedMs); // 单题作答上报
       void onRoundFinished(int score);                                // 轮结束（正常或超时）
       long serverRemainingMs();                                       // 服务端权威剩余时间
   }
   ```

   为首发池 6 个 `Game*Activity` 增加 `EXTRA_ONLINE_MATCH` 启动参数分支：在线模式下 ①开局调用 `GameRandom.init(roundSeed)`；②作答回调打入 `OnlineMatchContext`；③隐藏暂停按钮与失败重试对话框，胜负由服务端 `match_end` 驱动。**单机路径零改动**（默认无该 extra 走原逻辑）。
5. **对局 HUD**：新建 `OnlineGameHostActivity` 作为对局容器（比分条、对手进度点、轮次指示、倒计时），按轮加载对应 `Game*Activity`；或以浮层方案叠加在游戏 Activity 上——M0 期间二选一定稿（推荐容器方案，隔离更干净）。
6. **金币对接**：匹配成功扣 30 走 `LocalDataManager.chargeForOnline()`（现成）；结算按服务端推送刷新本地缓存；补 `TYPE_LOSE_ONLINE` 流水类型。
7. **出题逻辑剥离（服务端复用的前提）**：把 6 个首发游戏的"出题"（生成题目与期望答案）从 Activity/Progression 中抽成纯函数类 `QuestionGenerator`（仅依赖 `GameRandom`），客户端渲染层调用它。**Java 同语言的便利：这套纯函数直接拷贝到服务端复用**，实现服务端独立判题。

### 4.3 通信协议（WebSocket + JSON）

| 方向 | 信令 | 载荷（关键字段） | 说明 |
|---|---|---|---|
| C→S | `auth` | token | 建连后第一条，失败则重注册游客 |
| C→S | `match_join` / `match_cancel` | — | 进/出匹配队列 |
| C→S | `room_create` / `room_join` / `ready` | roomCode? | 好友房（P1） |
| C→S | `answer` | round, itemIndex, choice, clientTs | 单题作答，服务端校验计分 |
| C→S | `resign` | matchId（旧客户端可省略） | 主动退出指定比赛；重试不得影响新局 |
| C→S | `ping` | ts | 10s 心跳 |
| S→C | `auth_ok` | userId, profile, balance | |
| S→C | `match_found` | roomId, opponent, elo, rounds[{gameId, seed, durationMs}] | 匹配成功 |
| S→C | `countdown` | startAt | 开局倒计时锚点 |
| S→C | `round_start` | round, gameId, seed, serverNow, deadline | 客户端以 serverNow+deadline 倒计时 |
| S→C | `opponent_event` | round, itemIndex, gainedScore, streak | 对手进度（打码：不透露对错明细给对方利用） |
| S→C | `round_end` | round, myScore, oppScore | 轮末比分 |
| S→C | `resign_ack` | matchId | 已同步释放退出玩家的对局占用，不等待异步结算；不代表账务完成 |
| S→C | `match_cancelled` | matchId（队列取消无此字段） | 已配对比赛的取消必须绑定比赛 ID，客户端忽略旧局通知 |
| S→C | `match_end` | matchId, result, detail[], eloDelta, coinDelta | 终局结算；不得改变其他比赛的本地状态 |
| S→C | `opponent_disconnected` / `opponent_reconnected` | deadlineTs | 逃跑倒计时提示 |
| S→C | `reconnect_state` | 全量对局快照 | 重连时补发 |

版本化：所有信令带 `v` 字段，首版 `v:1`。

### 4.4 服务端设计

- **技术栈**：Java 17 + Spring Boot 3（`spring-boot-starter-websocket`，Netty 容器）+ PostgreSQL + Redis。与客户端同语言，`QuestionGenerator` 纯函数直接复用（见 4.2-7）。
- **Matchmaker**：Redis ZSET 队列按 ELO 入桶；配对成功生成 `roomId`；15s 超时触发 Bot。
- **Bot**：每题作答延迟 = 截断正态分布（μ、σ 按目标段位标定，如青铜 μ=2.5s/σ=0.8s、钻石 μ=1.2s/σ=0.4s），正确率 p 按段位（青铜 0.65 / 钻石 0.92）；**禁止秒答**（下限 400ms）以免露馅。
- **Room**：轮次状态机（`WAIT_READY → COUNTDOWN → ROUND_i → INTERMISSION(5s) → … → SETTLED`）；所有计时器服务端持有，信令只广播不协商；单 Room 生命周期 ≤ 8 分钟强制结算。
- **GameMaster / 判题**：开局即为每轮预计算期望答案序列（同 seed 调用 `QuestionGenerator`），收到 `answer` 后 O(1) 比对；MVP 即做服务端判题，不做"信任客户端"的过渡方案（6 个首发游戏均为纯逻辑，成本可控）。
- **Ledger**：终局事务内完成 ELO 更新 + 金币流水 + 战绩落库；对局事件流写 append-only 表供审计与反作弊回放。

### 4.5 反作弊

1. **作答合理性**：单题作答间隔 < 150ms（低于人类反应下限）连续 ≥3 次 → 判异常；正确率与作答速度的联合分布离群（如钻石段位 100% 正确且 μ<0.5s）→ 进入人工复核队列。
2. **答案外挂**：服务端持有期望答案，客户端只上报选择，改本地数据无效。
3. **时间篡改**：作答间隔一律以**服务器接收时刻**差值计算，不信任 clientTs。
4. **逃跑治理**：逃跑率 > 20% 且 ≥10 局 → 匹配降权（延长 Bot 兜底等待，让其更多遇 Bot）+ 结算页提示。
5. **多开**：同 token 二连踢前会话；同 IP 高频注册限流。

### 4.6 断线重连

- WebSocket 断开 ≠ 对局结束：Room 继续按服务端时钟推进，Bot 不会顶替真实玩家（仅匹配阶段有 Bot）。
- 客户端 `OnlineSession` 在网络恢复/回前台时携 token + roomId 重连，服务端下发 `reconnect_state` 全量快照（当前轮、剩余时间、双方比分），客户端恢复 HUD 并继续作答。
- 90 秒未重连 → 判负结算，对局关闭。
- **切后台策略**：Android 侧对局期间请求前台服务（FOREGROUND_SERVICE + 常驻通知"对局进行中"），降低进程被杀概率；被杀后依赖重连窗口。

### 4.7 数据模型（服务端）

```
user(userId PK, uuid, nickname, avatar, created_at, deleted_at)
user_stat(user_id PK, elo, wins, losses, draws, streak, flee_count, flee_rate)
match(match_id PK, mode, status, seed_meta jsonb, created_at, settled_at,
      player_a, player_b, result)
match_round(match_id, round, game_id, seed, deadline_ms, expected jsonb)  -- 期望答案
match_answer(match_id, round, user_id, item_index, choice, server_recv_ts, correct)
coin_ledger(id, user_id, match_id, type, amount, created_at)              -- 对齐 StartOnline/WinOnline/DrawOnline/LoseOnline
```

客户端侧仅扩展现有表：`LocalUser` 复用 `rating/onlineRank/money/token/displayName`，新增 `userId`；本地 `match_history` 缓存表（ActiveAndroid Model）。

### 4.8 技术约束与选型风险

| 约束 | 影响 | 对策 |
|---|---|---|
| minSdk 19（Android 4.4） | API 16–20 默认未启用 TLS 1.2，WSS 可能握手失败 | 自定义 `SSLSocketFactory` 强制 enable TLSv1.2；M0 真机验证；**建议将 minSdk 提至 21**（见待定项 D-1） |
| support 26.1.0 / 非 AndroidX | 网络库选型受限 | Java-WebSocket（纯 Java）+ 现有 gson，零新 AndroidX 依赖 |
| 游戏无公共基类（各自独立 Activity） | 桥接层需逐游戏接入 | 只接入 PK 池内游戏（首发 6 个）；`EXTRA_ONLINE_MATCH` 分支保证单机零回归 |
| 反编译代码库，行为敏感 | 改动易引入回归 | PK 功能全部走新增包/新增分支；每次发版跑单机全模块冒烟（23 游戏 × 4 板块） |
| 服务端从零建设 | 运维成本 | 单区域单机容器部署起步（Docker Compose：app + pg + redis），目标 1k CCU |

---

## 5. 非功能需求

| 维度 | 指标 |
|---|---|
| 匹配等待 | P50 ≤ 10s、P95 ≤ 16s（含 Bot 兜底） |
| 信令延迟 | 端到端 P95 ≤ 300ms |
| 对局时长 | 单局全程 3–5 分钟（5 轮 × ~40s + 间歇） |
| 并发目标 | MVP 1,000 CCU / 匹配 10 QPS / 信令 200 msg/s 峰值 |
| 可用性 | 服务端 99.5%/月；服务端不可用时单机模式 100% 可用（PK 入口灰置） |
| 兼容 | 与当前一致 minSdk 19（或提升至 21，见 D-1）；无 Google Play 服务依赖 |
| 电量/流量 | 对局期间心跳 + 信令流量 < 100KB/分钟；局外无长连接 |
| 隐私合规 | 联网后首启弹隐私政策与用户协议（新增 INTERNET 权限需在更新说明中声明）；提供账号数据删除入口；不收集 IMEI/定位等敏感信息 |

---

## 6. 里程碑规划

| 阶段 | 周期 | 内容 | 出口标准 |
|---|---|---|---|
| **M0 技术预研** | 1.5 周 | ① `GameRandom` 种子同题 PoC：两台真机同 seed 玩 Game1 出完全相同的题；② WebSocket 通道 + 心跳 + 重连骨架；③ 服务端权威倒计时方案验证；④ API 19 真机 TLS 1.2 验证；⑤ `QuestionGenerator` 从 Game1 剥离并在 JVM 里跑通（为服务端复用奠基） | 两台真机完成一局"人对人"手工对打（服务端最小原型） |
| **M1 MVP** | 5 周 | F-01~F-10：游客账号、匹配（含 Bot）、6 游戏 PK、ELO、金币、结算、断线重连、Online tab；服务端正式架构 + Docker 部署 | 内测 20 人：匹配成功率 > 95%、对局完整完成率 > 98%、单机全模块冒烟通过 |
| **M2 社交与打磨** | 4 周 | F-11~F-13：战绩页、房间码、赛季；反作弊 v2（分布检测）；Bot 拟真调优 | 公开 beta |
| **M3 扩展** | 4 周+ | F-14~F-16：排行榜、快捷语、异步战书；PK 池扩至二批游戏 | — |

### 6.1 实现进度（2026-10-02，dev/online-PK 分支）

M0 + M1 的代码实现已完成并通过构建/集成测试，与原计划的差异来自代码考古后的方案修正：

- **判题方案修正（替代 M0-⑤）**：调研发现原版在线模式的完整分支仍保留在
  `Game1MemoryGridActivity`（`isOnlineGame`、`gameSeed`、`updateOnlineScore()`、
  每游戏自带在线计时器），且 6 个首发游戏全部继承该基类。因此放弃"出题逻辑剥离
  QuestionGenerator"路线，改为**恢复原版在线分支 + 过关事件流上报**：客户端在
  `updateOnlineScore()` 钩子上报过关，服务端用同一张分值表重算权威分，辅以关卡序列
  校验与按游戏标定的作答间隔下限（首关免检）。R1 风险随之消除。
- M0-④ 已由 D-1（minSdk 21）覆盖；M0-①②③ 在服务端集成测试中验证（双端全量对局）。
- **M1 已交付**：客户端 `api.online` 包 + Online tab + 基类接通（`assembleDebug` 通过）；
  服务端 `server_memory-game-reborn/`（Spring Boot 3，`gradlew test` 通过，含双端全量对局/Bot 兜底/余额
  拒绝三个集成测试）；docker-compose（app + Postgres，海外部署用）。
- **M1 简化项**（见 server_memory-game-reborn/README）：Redis 未引入（内存态单实例）；战绩历史未持久化；
  断线重连为"重挂监听 + 当前轮补发"。
- **待真机验证**：Online tab UI、6 游戏在线实况、弱网重连——需部署服务端到海外机器后
  用 debug 构建联调（大厅长按标题改端点）。

### 6.2 M2 实现状态（2026-10-04）

- **F-11 战绩历史**：MatchRecord 表落库（每局结算时持久化，先落库后推送防竞态），
  WS history_request/response 消息 + 客户端 History 页（最近 20 局，胜负图标/比分/时间/Bot 标记）
- **F-12 好友房间码**：4 位房码，10 分钟过期，建房等待弹窗/输码加入；
  mode=FRIEND 对局 ELO 变动减半（真机验证 ±8 镜像）；房主断开自动清房
- **F-13 赛季**：30 天窗口，过期自动结算 + 全服 ELO 软重置（向初始值靠拢一半），
  大厅赛季行展示（Season N · 剩余天数）；赛季快照内存缓存（鉴权零 DB，避免连接池饿死）
- **反作弊 v2**：结算后按游戏间隔下限做均值分布检测，超速累计 suspicion，
  达阈值后限制为仅 Bot 对局 N 局（递减解禁）；限制中的用户跳过真人配对
- **Bot 拟真**：rubber-banding——比分差 >60 时 Bot 命中率逐题 ±2% 漂移（45%-95% 夹紧）
- **测试**：新增好友房（含 ELO 减半/404 用例）、历史双端视角、反作弊限制、赛季快照四个集成测试，共 7 绿
- **运维教训**：NAS 部署重启改 SIGTERM 优雅关库（kill -9 丢 H2 未刷盘提交）；
  部署后必须比对 jar md5（本次曾因只 cp 未上传导致服务端跑旧版、验证结果作废）
- **已知问题（open）**：偶发幽灵 match_join——对局结束/退出时刻偶有队列条目出现，
  触发额外 Bot 兜底局；客户端已埋调用栈日志待下次复现定位
- **Redis 房间态**：继续推迟——单实例下无用户价值，重启判负模型已验证可用

### 6.3 运行时配置化（2026-10-04）

回合时长与金币经济等全部改为**服务端运行时可调**（免重启），并明确 debug/release 行为分界：

- **服务端**：管理 REST（GET/PUT /api/admin/config，X-Admin-Token 守卫，空 token 即禁用/404 隐藏），
  热改 PkProperties 单例——所有消费点（回合时长/入场费/胜负平金币/礼金/Bot 等待/回宽限/间歇）
  每次读 getter 即时生效；数值钳制 + 审计日志。集成测试覆盖：热改后新对局的 round duration、
  入场费扣减与 auth_ok 经济回显、无 token 拒绝。
- **客户端回合时长改为服务端驱动**：round_start/match_found 携带 durationMs，4 个游戏的本地
  计时器优先取服务端值（本地仅作离线兜底）——服务端改时长，双端下一回合立即一致。
- **经济参数服务端下发**：auth_ok 携带 entryFee/winCoins/drawCoins；release 构建的大厅门槛与
  结算镜像使用服务端值（服务端改奖金，客户端本地账同步正确）。
- **debug/release 分界**：debug 构建=免金币（跳门槛、不镜像记账、长按标题可改端点）+ 可被
  test_round_ms 本地兜底；release 构建=完全服从服务端配置。生产 docker-compose 通过
  PK_ADMIN_TOKEN 环境变量启用管理 API，默认不开启。

---

## 7. 风险与待定项

**风险**

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| R1 | 出题逻辑与 UI 耦合在 800± 行的 Activity 内，剥离工作量超预期 | 高 | M0 先做 Game1 打样，按模板推广；二批游戏按需进池不贪多 |
| R2 | Bot 拟真不足被玩家识破，损伤信任 | 中 | 作答延迟/正确率按段位标定 + 偶发失误；上线后根据举报调整 |
| R3 | API 19 设备 TLS 兼容 | 中 | M0 真机验证；必要时 minSdk 提 21 |
| R4 | 服务端单人运维（成本/故障） | 中 | 单局 ≤8 分钟强制结算、全状态可重建（Redis 房间态可丢失重算）；监控告警 |
| R5 | 反编译库继续改动的回归风险 | 中 | PK 代码只增不改单机路径；每版全量冒烟 |
| R6 | 对局节奏（5 轮）与单局时长不匹配影响留存 | 低 | M1 埋点对局时长/放弃率，轮数与限时做成远程配置可调 |

**待定项（需决策）**

| # | 决策点 | 建议 |
|---|---|---|
| D-1 | minSdk 是否 19→21 | **已决策（2026-10-02）：提升至 21**。砍掉 TLS 1.2 特殊处理与老设备测试矩阵 |
| D-2 | 服务端部署位置（国内云需备案 / 海外 / 自托管） | **已决策（2026-10-02）：部署海外**。无需 ICP 备案，以 Docker Compose 单机起步；对国内玩家延迟目标放宽为信令 P95 ≤ 400ms |
| D-3 | 对局轮数 5 轮、每轮限时数值 | 按 M0 人肉对打体感初调，M1 远程配置化 |
| D-4 | HUD 采用容器 Activity 还是浮层 | M0 出可运行对比后定稿（倾向容器） |
| D-5 | Bot 使用的时机（15s 无对手）与占比上限 | 建议冷启动期 Bot 占比不设上限，DAU 起来后压到 30% 以下 |

---

## 8. 附录：原版在线逻辑考古索引

后续开发对齐原版行为时，可查阅以下残留代码：

| 内容 | 位置 |
|---|---|
| 入场费 30 金币 / 退款 | `src/main/java/com/cube/memorygames/api/local/LocalDataManager.java:176-185`（`chargeForOnline` / `refundForOnline`） |
| 金币流水类型（在线） | 同上 `:52-61`（`DrawOnline` / `StartOnline` / `WinOnline`） |
| 用户在线字段 | `src/main/java/com/cube/memorygames/api/local/model/LocalUser.java:42-51`（`onlineRank` / `unlimitedOnline` / `rating` / `token`） |
| 在线埋点分类 | `src/main/java/com/cube/memorygames/MemoryApplicationModel.java:46` |
| 种子化随机源 | `src/main/java/com/cube/memorygames/logic/GameRandom.java`（`init(long seed)` 即"同题"钩子） |
| 关卡难度曲线（出题参数来源） | `src/main/java/com/cube/memorygames/logic/GameProgression1~12/21` |
| 游戏注册表（23 游戏 + 6 类别） | `src/main/java/com/cube/memorygames/Games.java` |
| 原 Online tab 位置 | `src/main/res/xml/bottombar_tabs.xml`（已移除，恢复时新增第 5 个 `<tab>`） |
| 离线化移除清单（背景） | `README.md` "移除全部联网逻辑（在线对战、战绩上传、S3、Firebase 推送、网络同步）" |
