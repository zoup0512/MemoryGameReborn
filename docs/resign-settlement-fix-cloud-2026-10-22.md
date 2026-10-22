# 主动弃赛结算 P1 修复与云端诊断上线（2026-10-22）

## 结论

已修复 Android 主动弃赛后 `resign_ack` 清除对局快照、紧随其后的 `match_end` 被 `wrong_match` 丢弃而不显示结算的问题。修复经过真实会话调度器单测、真机/模拟器 GUI 以及签名 release/R8 包验证。云端同步部署此前未上线的逐题诊断白名单，实际 HTTPS 上传及服务器保存字节数核验通过。

**交付状态：**

- 服务端已于北京时间 **2026-10-22 09:34** 上线，数据库仍是 V10，无新迁移。
- 真机 CDY-AN90 已使用原证书保留数据更新最终 WSS 签名 release；旧账号 #51 可正常练习并显示弃赛结算。
- 两台 API35 AVD 均保留修复后的 `.cloudpartytest` 包；AVD A 另装最终 WSS 签名 release，通过“数一数”启动与弃赛结算。
- 部署与设备验收先于源码提交；构建基于 `4f7b67bf905e777015e02bae6f6614bebeac0edf` 加本次修复和诊断改动，后续提交以 Git 历史为准。未替换/重置账号凭据或清除原应用数据。

## 1. 修复内容

相关文件：

- `android_memory-game-reborn/src/main/java/com/cube/memorygames/api/online/OnlineSession.java`
- `android_memory-game-reborn/src/test/java/com/cube/memorygames/api/online/OnlineSessionExitTest.java`
- `android_memory-game-reborn/src/test/java/com/cube/memorygames/api/online/PartyIntegrationSourceTest.java`（运行时接缝对应的既有断言调整）

处理原则：

1. 保留 `resign_ack` 立即释放占用、停止重试、进入 IDLE 的语义，避免结算丢失时无限阻止下一场、入房或账号操作。
2. ACK 释放前，单独保留该局的待结算身份、原账号快照、首次弃赛时的监听者及回合展示计划；等待期最多 90 秒，不算进行中的对局。
3. 只接收明确带有同一 `matchId` 的 `match_end` / `match_cancelled`，不放宽普通对局消息的隔离检查。
4. 结果只处理一次，先消费待结算身份再执行回调；新排队、好友房、练习、亲友赛、断连或账号重置会使旧等待失效。
5. 结算余额/排位分只采用服务端权威结果，不从 ACK 合成金币或胜负。界面回调只交付给最初的弃赛页面；页面已关闭时仍可更新未被替换的账号快照。
6. 只恢复结算展示所需的回合名称，不恢复可开赛状态、当前 round、倒计时或 matchId。

为避免仅验证 DTO/源码字符串，增加 `OnlineSession.Runtime` 接缝，让 JUnit 直接驱动真实 `sendBotMatchRequest → handleMessage(match_found/round_start) → resign → resign_ack → match_end`。未新增 Robolectric/Mockito 等依赖。

## 2. 自动化验收

| 项目 | 结果 |
|---|---:|
| Android 完整 JVM 测试 | **161 / 161**，失败/错误/跳过均 0 |
| 新增真实会话弃赛回归 | **17 项**，包含在 161 内 |
| 服务端完整无 Docker 测试 | **240 / 240**，失败/错误/跳过均 0 |
| 管理控制台 JavaScript 测试 | **37 / 37** |
| 诊断核心 + 游戏日志独立断言 | **45 + 74 = 119** |
| debug、临时验证 instrumentation 构建 | 成功 |
| signed release（R8 + 资源压缩） | 成功 |
| `verifyReflectionKeep` | dex=95、arsc=94，通过 |
| release APK 签名校验 | v1/v2 验签通过，原发布证书 |
| 独立定点代码审查 | 通过，无需额外修正 |
| `git diff --check` | 通过 |

17 项会话回归覆盖：ACK 先到、END 先到、重复 ACK/END、错误/空/畸形编号、等待过期、赛前取消、替代队列/好友房/练习/亲友赛、替代对局分数/时钟隔离、账号/断连重置、ACK 前后监听者替换、重复弃赛仍保留原所有者、回调重入及回合名称快照隔离。修复前真实调度器测试实际失败（期望结算回调 1 次、实际 0 次），修复后通过。

PostgreSQL/Testcontainers 未执行（本机无 Docker），不以 H2 测试代替 PostgreSQL 验证。

## 3. 设备与线上 GUI 验收

### 3.1 隔离 debug 包

`com.memory.brain.training.games.cloudpartytest`，WSS，版本 3.6.3(74)，构建时间 2026-10-22 09:51。

| 场景 | 实际结果 |
|---|---|
| 真机主动退出练习 | 对局 `d02af130bb72`，显示“对局结束/你输了”、金币 +0、排位 +0，正常显示结果 |
| AVD B 主动退出练习 | 对局 `31841c3e8337`，显示 `Match complete / You lost` 及 `Memory Grid` 回合名称 |
| AVD A 排位主动弃赛（机器人兜底） | 对局 `3de9e8965c90`，显示败方结算、-16 排位分、余额 40、回合明细 |
| 真机排位主动弃赛（机器人兜底，额外补测） | 对局 `f072e408daf4`，服务端 `forfeit=true reason=resign`；真机显示败方结算、-16、余额 35 |
| 弃赛后新建亲友房 | AVD B 正常建出 6 位房码 `798312`，随后显式离房成功，未被旧对局占用阻挡 |

修复后日志可见 `snapshot_clear` 后同局 `match_end` 正常交付，未再出现对应局的 `frame_dropped reason=wrong_match`。例如真机 `d02af130bb72`：ACK 清快照后约 4ms 处理结算并回调页面。

### 3.2 最终签名 release 包

| 设备/场景 | 实际结果 |
|---|---|
| 真机，原账号 #51，`正确吗？` | 对局 `942c0e41f7ec`，主动退出练习后正常显示“对局结束/你输了”、余额 0、ELO 984，均无额外扣除 |
| AVD A，`数一数` | 对局 `0df3ceefcc72`，选项正常出现，主动退出后显示 `Match complete / You lost`、`Count'em All`、余额 60、ELO 1000；验证 R8 下图片反射与退出路径 |

最终 release 固定 `wss://www.razor123.site/ws`。曾先构建默认 WS 候选，真机正确提示身份属于另一环境；随后从真机原 APK 的端点 getter 反汇编确认原固定地址为 WSS，重建最终 WSS 包恢复原身份。**未改写身份环境、未清身份、未伪造迁移。** 非最终 WS 候选已移到 ignored `build/`，不作为交付。

### 3.3 工具失败与覆盖边界

以下不记作产品测试通过：

- 自动化最初误用了中文“离开练习/退出对局”文本，实际是“退出练习/退出对战”；另有教程页“开始”与匹配页 Start 混淆。修正定位后，练习与排位自弃赛已取得实际结算截图/日志。
- 一场真机与 AVD 的真实排位对局 `3eb2e5e68d41` 因上述退出按钮未命中，自然完成 5 轮 0:0 平局；**它只证明双端自然结算，不算双端主动弃赛镜像验证**。
- 后续重试因 AVD 金币不足/机器人兜底没有再次形成两名真人的弃赛镜像，不伪称完成。P1 的真实主动弃赛验证以表中真机/AVD 练习、排位机器人兜底和真实会话乱序测试为准。
- 本次未重跑所有六款小游戏/六人负载/弱网/强杀恢复。Activity 进程重建后自动恢复结算页并非本次改动范围；过期或新流程已开始的旧结算不会冒出覆盖新页面。

三端 crash buffer 中未发现本游戏 FATAL 记录。最终云端对局、匹配与数据库队列 gauge 均为 0。

## 4. 云端部署

### 4.1 实际范围与版本

部署前在线服务已是统一亲友赛 V10，不是前一天测试报告中的旧 V9。基线 JAR：

```text
7ead8de236817abbc14be8ba99eaa885288ca88fbb3ef2f728748c098b80007a
```

当前候选/线上 JAR：

```text
1988dc1fbdedd489a3cc27a69bcbb377f7c1cc5053565f8028997ac3ff3e876b
```

线上运行地址 `/root/memorygames-pk/app.jar`，服务 `memorygames-pk.service`、端口 8091。本次服务端唯一业务运行时代码差异是 `diagnostics/DiagnosticSanitizer.class` 的新增逐题事件/字段白名单；另有 build timestamp。V7 SQL 打包字节仅 CRLF/LF 差异，规范化后 V1–V10 内容完全一致；Flyway 实测校验通过，无新迁移。

Android P1 是客户端修复，**不是只重启云端就能修好旧 APK**；用户真机已更新最终包，其他旧客户端仍需安装更新。

### 4.2 安全部署与回退

- 候选上传至独立 hash 命名路径，校验 SHA-256，未覆盖运行中的 JAR。
- 使用已有闭库 V10 快照的独立副本，仅监听 loopback：候选与旧包分别启动、10 条迁移均通过、表计数不变，旧包可回退。
- 换包前 `pk_rooms_active=0`、`pk_queue_depth=0`、`pk_db_queue_depth=0`；当前 service invocation 未发现未结算双人局。亲友赛无实时 gauge，未宣称可完整证明大厅零占用；按用户本次明确部署授权执行维护。
- 暂时设置 `SendSIGKILL=no`，正常停止 PK 服务；确认 MainPID=0、8091 关闭、无人持有 data 文件后进行冷备。
- 冷备包含旧 JAR、完整 data、systemd unit/drop-ins、env 和站点 nginx 配置，权限限制为私有。
- 同文件系统 fsync + 原子替换 JAR；正常启动后验证健康与版本。
- 本次停启至验收窗口约 **13.06 秒**（09:34:06–09:34:19 CST）；未强杀进程。
- 临时 no-force guard 已移除，原 `SendSIGKILL=yes` 恢复；管理员、TLS、昵称敏感词配置及 nginx 内容校验不变。

冷备路径：

```text
/root/memorygames-pk/backups/diagnostics-p1-20261022-1988dc1fbded/cold
```

需要回退时：正常停止 PK → 原子换回本次冷备旧 JAR → 保留当前 V10 数据再启动。**不能用冷备数据库覆盖上线后已接收的新写入。**

### 4.3 线上验收

- systemd active，进程 PID 399567。
- 本地/公网 HTTPS readiness 均 200 UP。
- WS/WSS handshake 均 101，并校验 Sec-WebSocket-Accept。
- 启动日志 `Successfully validated 10 migrations`、`No migration necessary`，无新增启动 ERROR。
- 冷备计数：用户 59、双人历史 56、对局实例 65、金币流水 131、亲友赛 3、参与者 9、诊断报告 1；这是维护前快照，不包含之后测试写入。
- 本次测试正常创建并保留两位 release 模拟器游客、测试对局和报告；不直接清数据库测试记录。

## 5. 逐题诊断真实上传证明

通过 AVD A 正常“Settings → Help and diagnostics → Upload diagnostic logs → Upload over HTTPS”上传真实冻结快照。内容含已实际生成的逐题事件，未注入题面/答案，未创建替代凭据。

```text
报告编号：4a8ecfdc-8e73-4903-a21f-e32e35983add
uploadId：f14dc015-4e4f-400b-a5b8-f2f4141a2f1a
记录数：494
原始内容：128648 UTF-8 字节
```

为避免“上传成功但旧白名单丢事件”的误判，先在点 Send 前从本次隔离测试包获取精确 `pending.bin`，分别由部署前/候选包的实际 sanitizer 类离线处理同一正文：

| 处理器 | 保留事件数 | 保存字节数 |
|---|---:|---:|
| 旧 V10 白名单 | 248 | 40283 |
| 新白名单 | 494 | 128648 |
| 云端同 uploadId 幂等回执 | 同 GUI 报告编号 | **128648** |

新白名单保留了 87 条 `question_callback`、6 条 `question_generated` 及 phase/activityId/fingerprint/targetQuestion 等字段。服务器回执大小明确等于新白名单、不等于旧白名单，证明实际持久化未按旧白名单过滤。

回执通过临时 Android instrumentation 在 **原测试 app 内调用已有 AccountManager** 做同正文/同 uploadId 重试取得：未从设备导出/解密令牌，未重置管理员密码、未开管理 token，未连接运行 H2，未为查报告再次停服。临时 runner 已卸载。报告正文未通过管理员详情 API 直接读取，此项不作虚假声明。

## 6. 最终产物

签名 release：

```text
android_memory-game-reborn/release/memory-game-3.6.3-74-release-20261022-1002.apk
SHA256 93072ba491a1f46ec10ed7a7f2d933fcaf4ee61dc0d8f664386721aa9d660fc8
```

保留原证书，包含证书锚点；使用同签名版本覆盖安装保留应用数据。versionCode 仍为 74，versionName 3.6.3；这是本次修复交付包，不代表已上传商店或完成商店 versionCode 递增发布。

证据目录（ignored，不提交设备/账号运行日志）：

- `android_memory-game-reborn/build/p1fix-20261022/`：161 项单测日志、17 项真实会话测试结果、GUI 截图/XML/JSON、最终 R8 mapping、签名/端点校验。
- `server_memory-game-reborn/build/p1fix-20261022/`：240 项服务端构建、部署脚本/演练/安装验收、sanitizer 双版本对比、真实快照和幂等回执证明。
- 远端备份同上，未对无关服务进行重启/改动。

真机最终停留在已修复的签名 WSS 应用在线大厅；两台 AVD 保留运行。AVD B 中为试验首次安装的非最终 WS 正式包与 AVD A 的临时回执 runner 已卸载；原 `.cloudpartytest` / `.partytest` / `.accounttest` 数据未清除。
