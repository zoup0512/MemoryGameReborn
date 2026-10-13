# Online PK 全量回归测试报告（2026-10-13，线上云服务器 124.220.35.87）

**分支**：`main` @ `7cd49a7`（工作区另含未提交改动：客户端崩溃修复 + 测试工具修复，见下）
**服务端**：线上腾讯云 **124.220.35.87:8091**（systemd `memorygames-pk.service`，jar md5 `edda048e…`，Flyway v3）；
本地 `./gradlew test` 全量 28 用例 / 11 测试类全绿（BUILD SUCCESSFUL）
**设备**：CDY-AN90（Android 10，**zh-Hans 中文系统**），debug 包 `com.memory.brain.training.games` 3.6.3(74)，账号 `Player2630`
**期间热配**（管理 API，测毕已还原并重启）：2 局 / 20s 回合 / 宽限 1s / 间歇 0.5s / 起局延迟 0.5–3s / 免入场费 / 重连窗口 10s
**套件**：`android_memory-game-reborn/tools/pk-test-1010/regression.py`（单真机 + 脚本化 WS 探针）

## 结果总览：39/39 通过，零崩溃；另发现并修复 1 个 P0 客户端崩溃

| 阶段 | 场景 | 结果 | 关键证据 |
|---|---|---|---|
| S1 | 排位配对 / 同局计划 / 二段 START / HUD 同步 | ✅ | 探针与手机同种子同题；Waiting→“对手已就绪”翻转；HUD `OPP 18:0 ME` |
| S1 | 自然结算 + 败方卡片 + 零和 | ✅ | 探针 W +17 ELO；手机 “你输了” 卡；`phone=-17 probe=17` 零和 |
| S2 | 局中弃赛镜像 + forfeit 结算 | ✅ | `sendResign retriesLeft=6`；SETTLED forfeit=true；探针收 W |
| S3 | 好友房建房 / 输码 / 加房 | ✅ | 4 位房码（content-desc 提取）；FRIEND 模式；对手昵称为手机本人 |
| S3 | 双方闲置平局 + 好友减半 ELO | ✅ | D 结算；`delta=0`（结果减半后为 0）；手机 “平局！” 卡 |
| S4 | 对手硬断线 → 重连补发 → 窗口判负 | ✅ | 重连收到 round 补发；SETTLED forfeit=true；手机回大厅 |
| 服务端 | `gradlew test` 全量 | ✅ | 28/28，0 failures |

另：云端公网 `health=UP`、`/ws` 升级 101、`/actuator/prometheus` 200；手机以 debug 包默认端点
`ws://124.220.35.87:8091/ws` 直连云服（无本地 adb reverse / 无端点覆盖），旧 token 在空库云上
自动 401→重注册（`auth_ok` 新账号），链路自愈正常。

## 发现并修复的缺陷（P0）：回合结束→局间过场时客户端崩溃

```
FATAL EXCEPTION: main
java.lang.RuntimeException: Failure delivering result (request=1001) to OnlineMatchActivity
Caused by: java.lang.IllegalStateException: The specified child already has a parent.
  at OnlineMatchActivity.showScrollableBoard(OnlineMatchActivity.java:1088)
  at OnlineMatchActivity.showIntermissionUi(OnlineMatchActivity.java:945)
  at OnlineMatchActivity.onActivityResult(OnlineMatchActivity.java:1410)
```

- **机制**：局间过场卡（`intermissionBoard`）是缓存复用的 View；第 2 次展示时
  `showScrollableBoard → inner.addView(board)` 未先把 board 从旧父容器摘下 → 抛异常 → 进程崩溃重启。
  第 1 回合结束是首次挂载（正常），**第 2 回合及以后回合结束时必崩**。
- **为何线上必现、本地测试漏掉**：回合 Activity 的结果回调（本地、即时）与 `match_end` 帧（走网络）
  存在竞态；`onActivityResult` 只在 `matchId != null`（比赛未结算）时走 `showIntermissionUi`。
  本地 localhost 时 match_end 通常先到 → 走 `matchId == null` 分支不触发；经公网时结果回调先到 → 崩溃。
  这也解释了此前录到的 `SEND_OVERFLOW` 事件：崩溃后 App 的 socket 变死连接，服务端背压队列按设计
  强制断线（修复后再没出现过）。
- **修复**（未提交）：`showScrollableBoard` 挂载前先 `board.getParent().removeView(board)`。
- **影响面**：所有 ≥2 回合的真实对局（真人/机器人）在公网条件下都会崩；崩溃即断线判负。

## 测试工具修复（全套件为中文设备适配，未提交）

1. `ui.py`：adb 路径改为 `$ADB`/PATH（原硬编码 `C:/MyProgram/Sdk/...` 已失效）；
2. `ui.py`：`find()` 支持中英双语 + **精确匹配优先**（修复 “开始” 误中提示句 “…即可**开始**。”）；
3. `regression.py`：S3 对手昵称改为从手机 `shared_prefs` 动态读取（原硬编码 `Player1673`）；
4. `regression.py`：房间码改从节点 `content-desc` 提取（新版 UI 房码带空格渲染 `0  4  8  3`，
   原 `\d{4}` 会误取大厅 ELO `1000`）；
5. `regression.py`：S3 ELO 对齐改为按昵称动态解析手机 uid（原写死 uid=1，复用库时错位）。

## 观察（非缺陷，记录在案）

1. **管理 API 无法在运行时清除回合时长覆盖**：`PUT /api/admin/config` 对
   `roundDurationOverrideMs` 钳制 `[10000, 600000]`，传 0 被钳到 10000，而 0 才是“无覆盖”语义 →
   测试后需**重启**服务才能回落各游戏默认时长。建议支持显式清除（如传 -1 或专门字段）。
2. 云端生产部署自 **7cd49a7 起**：Flyway v3 全新结构（旧空库已备份 `data.bak-empty-20261013`，
   旧 jar 备份 `app.jar.pre-7cd49a7.bak`）。
3. 测试期间临时开启过管理 token（systemd drop-in），**测毕已移除并重启**，线上管理面恢复为禁用
   （无 token 404）。如需再跑回归：`/etc/systemd/system/memorygames-pk.service.d/admin-token.conf`
   写 `[Service] Environment=PK_ADMIN_TOKEN=<token>` 后 `daemon-reload && restart`。

## 结论

线上云服务（含 Flyway 新结构、单飞状态门、结算幂等、断线重连窗口等 7cd49a7 全部服务端改动）
在真机 + 脚本化对手的 39 项全链路断言下全部通过，服务端 28 项测试全绿。回归过程中发现并修复了
1 个此前未被任何测试触达的 P0 客户端崩溃（第 2 回合过场，公网条件下必现），修复后整套回归零崩溃复现。
