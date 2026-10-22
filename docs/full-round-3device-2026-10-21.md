# 三端全量回归报告（真机 + 2×API35 模拟器，2026-10-21）

## 结论摘要

> 本文记录 2026-10-21 测试窗口的历史结果，不代表当前发布状态。2026-10-22 已修复本文发现的 P1，完成签名 release 验收并部署云端逐题诊断白名单；真实上传回执验证通过，未直接读取管理员报告正文。后续结果见[修复与部署验收](resign-settlement-fix-cloud-2026-10-22.md)。本次补测不等于重新执行本文整轮三端矩阵。

本轮使用 **1 台 USB 真机 + 2 台 Android 15（API35）模拟器**，对当前工作区的云端隔离包做了三人端到端回归，覆盖：

- 单机在线池 6 款游戏：记忆网格、颜色、舒尔特方格、正确吗、和之前一样吗、数一数；
- 双人好友房 5 轮全流程；
- 双人排位 5 轮全流程与 ELO/金币结算；
- 三人亲友赛 2 场 × 3 轮、同房再来一局、历史与房间释放；
- 对战历史完整对局编号、复制与粘贴核对（12 位与 UUID 两种格式）；
- “数一数”逐题诊断日志在双人与三人模式下的生成与字段检查；
- 练习场机器人对局与中途退出；
- 三端崩溃/ANR 扫描。

单测：Android **126/126**、服务端 **189/189** 通过。

**发现 1 个高优先级客户端缺陷（P1）：主动弃赛/退出练习的一方会丢弃服务端的 `match_end`，导致结算页不显示。** 服务端结算、历史记录和对手方结算均正常；缺陷只影响弃赛者自己的结果页与金币/排位分刷新。练习场与双人好友房均可稳定复现，详见第 4 节。

另有一项发布前置条件未完成：**服务端诊断白名单改动尚未部署到云端**。客户端逐题日志已在设备上正确生成，但旧服务端会丢弃新事件，因此本轮不能声称云端报告端到端可用。

## 1. 环境与构建

| 项目 | 内容 |
|---|---|
| 时间 | 2026-10-21 18:00–18:49 CST |
| 工作区 | `main` + 未提交诊断/历史改动（`GameDiagnostics`、历史对局编号、服务端白名单等） |
| Android 包 | `com.memory.brain.training.games.cloudpartytest` 3.6.3(74)，debug，端点 `wss://www.razor123.site/ws` |
| 服务端 | 腾讯云 `124.220.35.87:8091`，运行 `app-party-d461800-1fe6632d.jar`（10:26 部署）；默认 5 轮/入场 30/无热配置；未启用管理 token |
| 真机 | CDY-AN90，Android 10，zh-Hans，720×1600 逻辑分辨率 |
| AVD A/B | `MemoryParty_A_API35` / `MemoryParty_B_API35`，Android 15 google_apis_playstore x86_64，1080×2400，英文系统 |
| 隔离性 | 三端均使用 `.cloudpartytest`，未覆盖正式包、`.partytest`、`.accounttest`，未清正式应用数据 |

构建与单测命令：

```bash
# Android：单测 + 云端 debug 包
JAVA_HOME=F:/jdk17 ANDROID_HOME=D:/AndroidSdk ./gradlew \
  testDebugUnitTest assembleDebug \
  -PpartyCloudUiTest=true -PonlineEndpoint=wss://www.razor123.site/ws --offline

# 服务端：H2 全量单测（本机无 Docker，PostgreSQL/Testcontainers 未执行）
JAVA_HOME=F:/jdk17 ./gradlew test --offline
```

结果：

- Android `testDebugUnitTest`：**126 项，0 失败，0 错误，0 跳过**；
- 服务端 `test`：**189 项，0 失败，0 错误，0 跳过**；
- 三端 APK 安装/更新成功；真机保留原账号数据。

## 2. 单机 6 款游戏（真机及 AVD）

| 游戏 | 实际路径 | 结果 |
|---|---|---|
| 记忆网格 | 启动第 1 关、答题、暂停、结算 | 通过 |
| 颜色 | 教程→开始→出现色词/选项→点击选项→返回 | 通过 |
| 舒尔特方格 | 教程→开始→点击数字格→返回 | 通过 |
| 正确吗？ | 教程→开始→出现算式→返回 | 通过 |
| 和之前一样吗？ | 教程→开始→第 2 关→点击→返回 | 通过 |
| 数一数 | AVD A：教程→开始→网格→选项→答题→自动换题→暂停→查看结果 | 通过 |

说明：部分单机游戏按返回会直接回主菜单（颜色/舒尔特/正确吗/和之前一样吗），数一数与记忆网格会显示暂停/结算；这是本轮观察到的既有行为，未按缺陷处理。单机模式不写逐题诊断日志，符合 `GameDiagnostics` 只在在线模式启用的设计。

## 3. 在线对战与三人亲友赛

### 3.1 双人好友房（真机 vs AVD A）

服务端计划（`MATCH_CREATED match=d3616821be6e mode=FRIEND rounds=5`）：

`1:10:60000, 2:0:80000, 3:15:30000, 4:22:45000, 5:4:60000`

即正确吗？→ 记忆网格 → 和之前一样吗？→ 舒尔特方格 → 数一数。

- 双方进入相同的游戏计划，题目与得分同步正常；第 5 轮“数一数”两端均出现。
- 最终 **真机 14 : 12 AVD A**，真机获胜；结算显示金币 **+50**、排位分 **+8 → 1008**，AVD A 排位分 **-8 → 992**。
- 服务端 `SETTLED ... scores=14/12 round=5 reason=rounds_complete`。
- 两端历史均出现同一对局编号 `d3616821be6e`。

### 3.2 双人排位（AVD A vs AVD B）

服务端计划（`match=a0303aea484a mode=RANKED rounds=5`）：

`1:15:30000, 2:10:60000, 3:0:80000, 4:22:45000, 5:19:30000`

- 排队配对成功，两端均需按下 Start；完成后 **AVD A 26 : 16 AVD B**。
- 服务端 `SETTLED ... scores=26/16 round=5`。
- AVD A：排位分 **+16 → 1008**、金币 **+50**；AVD B：排位分 **-16 → 984**、金币 **+0**。ELO 零和正确。
- 历史编号 `a0303aea484a`。

### 3.3 三人亲友赛（真机房主 + AVD A/B）

**第一场**（房码 `990552`，3 轮）：

| 轮次 | 游戏 | 真机原始分 | AVD A | AVD B |
|---|---|---:|---:|---:|
| 1 | 颜色 | 0 | 0 | 0 |
| 2 | 和之前一样吗？ | 0 | 0 | 0 |
| 3 | 正确吗？ | 126 | 0 | 0 |
| 合计 | — | **126 / 2 分 / 第 1 名** | **0 / 第 2 名** | **0 / 第 2 名** |

三端最终榜单、每轮明细、名次与积分完全一致；对局编号 `2aae6a4d-a47d-4c1d-8052-d32d4fa33af4`，3 人 3 轮已完成。

**第二场**（同一房间“再来一局”，3 轮）：

| 轮次 | 游戏 | 结果 |
|---|---|---|
| 1 | 数一数 | 真机 18 原始分 / +2 积分；A/B 均 0 |
| 2 | 舒尔特方格 | 三端 0 |
| 3 | 颜色 | 三端 0 |

三端最终榜单一致；对局编号 `0b66a548-1f57-478e-8a0d-0c61883e5107`。第二场积分从 0 重新计算（第 1 轮后 HUD 显示总积分 2），未串场。

**房间生命周期**：

- 三人准备后房主开赛；倒计时/加载后自动进入游戏；
- 结束后“回原房间 · 再来一局”保留房码 `990552`、3 名成员，准备状态重置；
- A/B 先后离房，真机房主最后离房；AVD A 用 `990552` 重新加入返回 **“Room not found. Check the 6-digit code.”**，房间占用已释放；
- 两场历史均保留，未因离房丢失。

**多人输入核验**：第二场“数一数”中，三端均记录到 `question_answer`：真机 5 次（4 错 1 对），A/B 各 2 次（均错）。A/B 的 0 分是答案错误，不是点击未送达。

### 3.4 练习场（AVD B）

- 选择 Bronze Bot（Rating 800）→ 免费对局 → 首轮颜色 → 机器人得分 8。
- 中途返回并确认“Leave practice”后，服务端按弃赛结算；客户端结果页问题见 4.1。

## 4. 缺陷

### 4.1 P1：主动弃赛方丢弃 `match_end`，结算页不显示

**现象**

- 练习场：确认离开后停留在“Round 1 of 5 complete / Confirming this round's score… / Waiting for server sync. Stay connected.”，按返回键才能退出；服务端已结算。
- 双人好友房：B 在第 1 轮“数一数”中确认“Leave match”后，停留在赛前等待页“Your opponent is Ready / The match starts as soon as both players are ready.”；对手 A 在回合计时结束后正常看到获胜结算（+50 金币、+7 排位分）。
- 弃赛方历史记录正确（Loss、对局编号 `4f11db8f540a`），但顶部金币/排位分仍显示旧值，因为结算页没有应用。

**客户端证据**（`evidence/diagnostics/duelresign-B2/*.ndjson`）：

```text
seq 317 ts 1792579531163 event snapshot_clear matchId 4f11db8f540a round 1 mode FRIEND
seq 319 ts 1792579531165 event frame_received reason match_end matchId 4f11db8f540a
seq 320 ts 1792579531165 event frame_dropped reason wrong_match mode RANKED
```

练习场同样模式：

```text
match 2384a63ab31f: snapshot_clear .446 → frame_received match_end .447 → frame_dropped wrong_match
match b81bc307d3ad: snapshot_clear .976 → frame_received match_end .1073 → frame_dropped wrong_match
```

服务端同期记录：

```text
SETTLED match=4f11db8f540a mode=FRIEND forfeit=true scores=0/0 round=1 reason=resign
SETTLED match=2384a63ab31f mode=PRACTICE forfeit=true scores=8/24 round=1 reason=resign
SETTLED match=b81bc307d3ad mode=PRACTICE forfeit=true scores=0/0 round=1 reason=resign
```

**根因判断**

客户端在 `resign_ack`（`snapshot_clear`）时先清空 `matchId`，服务端随后到达的 `match_end` 因对局编号不匹配被 `frame_dropped reason=wrong_match` 丢弃，`onMatchEnd → showSettlementUi` 没有执行。练习场与双人好友房均复现；对手方不受影响，历史由服务端结算正常落库。

**影响与建议**

- 影响所有主动弃赛路径（排位/好友/练习）；数据未丢失，但弃赛者看不到金币/排位变化，容易误判。
- 后续修复保留 ACK 立即释放占用的语义，另设精确编号、限时的一次性待结算快照，并新增真实会话回归。不能由 ACK 合成金币/结果，也不能为等待结算一直保留活跃 matchId；详见 2026-10-22 的修复验收。
- 现有 39 项脚本套件的弃赛场景只让 WS 探针弃赛（手机作为胜方），没有覆盖“手机自己弃赛”，因此该缺陷此前未被套件发现。

**复现次数**：练习场 2 次、双人好友房 1 次，均稳定出现。

### 4.2 诊断索引约定：亲友赛 round 为 0 基（非缺陷）

双人/练习日志的 `round` 从 1 开始，亲友赛沿用协议的 0 基索引（第 1 轮 `round=0`，第 2 轮 `round=1`）。例如第一场 `gameId=19 round=0`、`gameId=15 round=1`、`gameId=10 round=2`。这是既有协议约定，不归类为 P3，也不应仅为日志一致性更改协议索引；按轮次过滤时需结合 `mode` 区分。

## 5. 对局历史与编号

- 双人历史：`d3616821be6e`（好友）、`a0303aea484a`（排位）、`4f11db8f540a`（好友弃赛）。
- 亲友赛历史：`2aae6a4d-a47d-4c1d-8052-d32d4fa33af4`、`0b66a548-1f57-478e-8a0d-0c61883e5107`（UUID 格式）。
- 中英文界面均显示“对局编号 / Match ID”和复制按钮。
- 点击复制后出现“对局编号已复制”提示；通过 debug 预览页粘贴，剪贴板内容核对为 `d3616821be6e`（无多余文本）。
- debug 预览同时验证了 12 位、UUID 与空编号三种展示；空编号显示“暂无对局编号”，复制按钮不可用。

## 6. “数一数”逐题诊断

| 场景 | 设备 | `question_generated` | `question_answer` | 其他 |
|---|---|---:|---:|---|
| 双人好友第 5 轮 | 真机 | 1 | 0（回合超时） | options 2、visible 1、hidden 3、callback 12 |
| 双人好友第 5 轮 | AVD A | 2 | 1 正确 | options 2、visible 2、hidden 5、callback 27 |
| 亲友赛第 1 轮 | 真机 | 6 | 5（4 错 1 对） | options 6、visible 14、hidden 18、callback 50 |
| 亲友赛第 1 轮 | AVD A/B | 3 | 各 2 错 | options 3、visible 7、hidden 9、callback 24 |

- 事件字段包含 `matchId/round/gameId/mode/activityId/question/level/fingerprint/elapsedMs`，以及 `phase/reason/callbackId/targetQuestion/flowRun/flowStep/generation`；
- 未发现原始种子、题面、正确数量、选项值或玩家选择内容；`fingerprint` 为进程内 HMAC 截断值，与文档一致；
- 双人模式的指纹两端不同，符合“进程随机密钥、跨进程不可比”的设计；
- 三端无 FATAL/ANR，日志文件正常滚动（真机 28–35 KB，模拟器同级）。

**未完成的端到端环节**：云端运行的仍是 10:26 的旧 JAR，服务端白名单未包含新增游戏事件。客户端本地日志已正确生成，但若此时上传，新事件会被旧服务端丢弃。因此本轮只能确认“客户端生成正确”和“本地服务端白名单单测/集成测试通过”，不能确认“云端报告可检索逐题事件”。上线前需先部署服务端诊断改动，再上传一份真实报告复核。

## 7. 覆盖边界与未测项

- 未做弱网/真实网络切换、强杀进程恢复、跨设备断线重连；
- 未做 6 人满载亲友赛，也未用 6 台物理设备；
- 未安装/验收 release（R8/资源压缩）包；本轮只验证 debug 包；
- 未启用云端管理 token/热配置，沿用默认 5 轮与默认回合时长；
- 未运行 `tools/pk-test-1010/regression.py` 的 39 项脚本套件；本轮以三端手工/半自动 GUI 为主；
- 未执行 PostgreSQL/Testcontainers（本机无 Docker），服务端验证为 H2；
- 未做云端诊断报告上传后的服务端检索（见第 6 节）；
- 未提交代码、未部署服务端；测试游客、对局与历史保留在云端，未直接清理数据库。

## 8. 证据与产物

以下截图、原始日志和探索脚本仅保留在本地，不随源码提交；干净检出不包含这些文件。含设备或账号运行信息的原始材料不上传仓库。

- 截图/UI dump：`android_memory-game-reborn/build/full-round-20261021/evidence/`
  - 单机：`offline/`
  - 双人：根目录 `20-duel-room-created.png`、`30-history-phone.png`、`31-history-copied.png`、`32-history-A.png`、`friend/`、`live/rank-*`
  - 亲友赛：`party/`（含 `40-party-host-lobby.png`、`44-party-history-phone.png`、`50-room-released.png`）
  - 练习场：`practice/60-practice-r1.png`、`practice/61-practice-left.png`（卡住界面截图）
  - 诊断日志：`evidence/diagnostics/`（`friend*`、`party-*`、`final-*`、`duelresign-*`、`practice-B`）
- 三设备驱动/场景脚本：`android_memory-game-reborn/tools/full-round-20261021/`（`drive.py`、`offline_smoke.py`，未提交）
- 服务端日志：云端 `journalctl -u memorygames-pk` 的 `MATCH_CREATED` / `SETTLED` 行；报告第 4 节已摘录关键行。

## 9. 结论与发布建议

本轮三端主流程（6 款在线游戏、双人好友、排位、三人亲友赛、历史编号、逐题诊断）整体通过，服务端与客户端单测全绿，三端无崩溃。亲友赛的同题、同关、最终榜单和房间生命周期符合预期。

本轮结束时的发布门槛是修复 4.1 并补本机主动弃赛回归，以及部署诊断白名单、验证真实上传。2026-10-22 的后续验收已完成这两项，并补充签名 release/R8 真机及 AVD 冒烟；云端证明采用同 uploadId 的持久化回执字节数与新旧 sanitizer 对比，未直接执行管理员正文检索。详见文首链接。

弱网/重连/强杀恢复及满载亲友赛仍未验证。诊断/历史改动本身未发现阻塞性问题；亲友赛 round 0 基按既有协议解释，不作为缺陷或协议改动建议。
