# Online PK 真机回归测试报告（2026-10-04 第二轮）

**范围**：弹窗对齐后的 Online 模块全量回归——大厅、历史、Bot 局、好友房（含错误路径与弃赛）、金币门槛、结算。
**结论**：**16 项通过 / 1 项失败（bug#6 弃赛未生效）**；测试过程中发现并修复 2 个客户端架构 bug（3a/3b），全部修复项已在终版构建上回归通过。
**产物**：截图与 uiautomator dump 存于 `android_memory-game-reborn/tools/pk-test-1004/`（`r2-*` 为本轮，`cdy/bkl-0*` 为上一轮）。

## 1. 环境与配置

| 项 | 值 |
|---|---|
| 设备 | CDY-AN90（720x1448，`25HUT20515012964`）、BKL-AL20（1080x2160，`P7CDU17B22004602`） |
| 构建 | debug（统一工作区构建，含全部修复）；金币门槛用例临时翻转 `OnlineDebug.freeEconomy` 后还原 |
| 服务端 | LAN NAS `ws://192.168.110.140:8090/ws`，admin token `lan-test-token` |
| 测试期热配置 | 回合 10s、入场费 0、bot 等待 8s（测后恢复 90s/0/15s） |
| 验证手段 | uiautomator dump 结构断言 + 截图存档 + 服务端事件环（`GET /api/admin/events`）+ Python WS 探针 |

## 2. 测试矩阵

| # | 用例 | 预期 | 实际 | 结果 |
|---|---|---|---|---|
| T01 | 双端冷启 → Online tab | 档案卡绑定昵称/ELO/段位/胜负平/赛季行，Play Online/Create room/Join room/History/教程 5 条齐全 | 双端一致（Player9531 / Player9438） | ✅ |
| T02 | 长按大厅标题（debug） | 端点弹窗预填 `ws://192.168.110.140:8090/ws`，可取消 | 一致 | ✅ |
| T03 | 历史页双端 | W/D/L 字母、对手名+`friend` 标签、`MM-dd HH:mm`、比分，跨端镜像 | 一致（CDY 见 9438，BKL 见 9531/ProbeHost） | ✅ |
| T04 | Bot 局全流程（BKL） | 搜索页（doodle+取消）→ Bot 兜底 → 5 轮 → 结算 | `You lost 0:160`、+0 币、-16 ELO→976 | ✅ |
| T05 | 建房→等待卡 | 白卡：Room code: + 40sp 房码 + 等待文案 + TTL 倒计时圆盘 + 取消 | <1s 出卡，房码 0737/4734/3511 | ✅ |
| T06 | 取消房 | 卡关闭，房间服务端销毁 | 一致（旧码随后 404 佐证） | ✅ |
| T07 | 加入已取消房 | 服务端 404 → 客户端 Toast + 不误启对局（3a/3b 修复回归） | Toast 窗口被轮询实锤 16 帧；未启对局 | ✅ |
| T08 | 好友房加入 → 双端同题 | 双端同 Game 同回合 | 双端同屏 Game5/Game23 | ✅ |
| T09 | 局中返回键（在线） | 原版 `dialog_lose_bet` 平台对话框（与 19 个游戏 Activity 的原版死代码同款） | "You will lose your bet if you quit. Are you sure?" + Cancel/YES | ✅ |
| T10 | 弃赛生效 | 服务端 forfeit 结算，对手胜 | **弃赛未生效**：局继续打完，自然 0:0 平局，弃赛者 +15 币 | ❌ bug#6 |
| T11 | 自然平局结算（FRIEND） | 双端镜像 Draw 结算卡：+15 币 / +0 ELO / 双按钮 | 双端一致（BKL 110/976，CDY 60/1000） | ✅ |
| T12 | Bot 负局结算卡 | 白卡 + doodle_31 + 金币行 + ELO 行 + Play again(绿)/Exit(蓝) | 一致 | ✅ |
| T13 | 金币不足门槛 | 原版 `dialog_free_online` 卡（标题/云朵 doodle/关闭 X/警告文案/Yes） | 一致，Yes 关闭正常 | ✅ |
| T14 | 输码弹窗 | dialog_unlock 式白卡 + 数字输入 + Yes/取消 | 一致，输入提交生效 | ✅ |
| T15 | 大厅错误反馈 | 僵尸连接下建房/输码有可见反馈（3a 修复） | 修复后正常路径秒回；超时路径见 bug#5 备注 | ✅* |
| T16 | 环境还原 | 配置/代码/干净包 | 全部还原，双端冷启冒烟通过 | ✅ |

\* T15：应答超时与 Toast 机制已实现并在 404 路径验证；僵尸连接的完整复现未在本轮稳定重演（见 bug#5）。

## 3. 本轮发现并修复的 bug（均在终版构建回归通过）

### bug#3a 房间命令无应答超时（僵尸连接静默吞命令）
- **现象**：对局结束后回大厅建房/输码，命令发出但服务端无响应、客户端零反馈（等待卡不出、无提示）。TCP 僵尸连接可通过 `isConnected()` 检查，`send()` 只入缓冲不报错。
- **修复**：`OnlineSession.sendRoomCommand()` —— 断连先重连、2.5s 后重试一次；create/join 命令附带 3s 应答超时（`pendingRoomAck`），超时 `notifyError(-1,"no response")`；`room_created`/`match_found`/error/断开时撤销超时；`room_cancel` 服务端不应答故不设超时。
- **回归**：T05/T07 通过。

### bug#3b 返回大厅后 MatchListener 被置空（大厅变聋）
- **现象**：`OnlineMatchActivity/OnlineHistoryActivity.onDestroy` 无条件 `setMatchListener(null)`，而返回大厅时 `MainMenu.onResume` 先重挂监听器、随后销毁中的 Activity 又把它清掉（生命周期顺序），导致 `room_created`/error 帧无人接收——建房无卡、404 无 Toast。
- **修复**：两个 Activity 的 onDestroy 改为**仅当当前监听器是自己时**才置空（新增 `OnlineSession.getMatchListener()`）。
- **回归**：历史页往返 → 建房秒出卡（此前 100% 失败）；404 Toast 正常。

### 测试方法学发现
- **服务端事件环是权威诊断工具**：`GET /api/admin/events` 直接给出 AUTH_OK/MATCH_JOIN/BOT_FALLBACK/ROUND_START/SETTLED 时间线，本轮用它实锤了 bug#6（弃赛后 ROUND_START 继续 → SETTLED forfeit=false）。
- **Python WS 探针**（`tools/pk-test-1004/probe_host.py`/`probe_room.py`）可当"第三方客户端"分离客户端/服务端侧问题。

## 4. 遗留问题（未修复，按优先级）

### bug#6（P1，本轮唯一失败项）弃赛未生效
- **复现**：好友局中游戏内返回 → `dialog_lose_bet` → YES。客户端 `Game1.openMainMenu()` 在线分支调 `OnlineSession.resign()` → `send(T_RESIGN)`；服务端事件环显示对局继续（rounds 3-5）并自然结算 0:0 平局，**弃赛者反获 +15 平局奖**（经济负向）。
- **嫌疑**：①对局中 socket 发送半失效（接收正常、发送缓冲蒸发——与 3a 同类但发生在对局中，`send()` 无应答机制覆盖）；②服务端 `T_RESIGN` 分支无 `events.emit`，无法从事件环确认是否收到——**建议先加一行 RESIGN emit 再复测**。
- **影响**：金币经济可被"输了就退"白嫖平局奖。

### bug#5（P2）debug 包首连失败后永不重试
- **现象**：某次会话冷启后静默连接失败（标志：大厅昵称行为空），此后建房点击全部无效，重启应用才恢复。`connect()` 自身无重试（`handleDisconnected` 仅在 matchId!=null 时重连）。
- **建议**：`handleDisconnected` 对 matchId==null 的失败也做有界退避重连；或 bindProfile 时对非 IDLE 状态补一次 connect。

### 观察项（P3，记录待查）
- 历史里出现"服务端已开局但双端客户端均未进局"的 0:0 平局记录（`room_join` 到达服务端但 `match_found` 被旧版守卫丢弃时期产生）——被放弃对局按平局结算并入账 +15 的语义需要产品确认（与 bug#6 同根：无响应方不应获平局奖）。
- 等待板 TimerView 倒计时在当前时序下不可达（服务端从 round_start 起算，客户端 3s 倒计时+同长游戏 → 返回时剩余恒 ≤0）。代码保留无害，TimerView 实际可见处仅好友房等待卡。
- BKL 余额轨迹（60→95→110）与预期不完全吻合，属运行时配置工作流的经济范畴。

## 5. 环境还原清单（已执行）

- 服务端热配置恢复：90s 回合 / 入场费 0 / bot 等待 15s（已核回读）。
- `OnlineDebug.freeEconomy` 临时翻转已还原，干净构建重装 CDY；BKL 保持终版构建。
- 双端冷启冒烟：大厅档案绑定正常。

## 6. 未覆盖项

- 弱网/断线重连（opponent_disconnected/reconnected Toast）——需可控断网手段。
- 排行榜、快捷语、异步战书（M3 未实现）。
- 真人双端同时在线的 5 轮完整自然局（本轮好友局因 bug#6 走到自然平局，已覆盖结算面）。
- 性能/内存、Process death 恢复路径。
