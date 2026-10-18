# 接受超时后挂机玩家无限重排修复（2026-10-17）

## 问题与原因

双机停留在接受卡、不点击 START，超过生产接受窗口 30 秒后，服务端记录 `MATCH_NOT_ACCEPTED reason=accept_timeout` 并取消比赛。旧 Android 客户端用 `matchId != null` 判断“本方已接受”，排位取消后无条件再次发送 `match_join`；31 秒本地兜底也直接按已接受处理，因此双方会反复配对。

服务端不会在接受超时后自动入队。线上日志中的 `MATCH_NOT_ACCEPTED` 不是客户端错误码；现有取消帧实际是 `match_cancelled`、空 payload。正常费用流程为开房时托管、接受超时后退款，净费用为零，不产生正常战绩。本次未修改费用或服务端生产逻辑。

## 修复后的策略

| 本轮状态 | 取消后的行为 |
| --- | --- |
| 排位，本方未点击 START | 清理比赛并返回大厅，不自动入队 |
| 排位，本方已点击 START，未主动退出 | 自动重新排队，但下一场必须重新点击 START |
| 好友或练习 | 返回大厅，不自动转入排位 |
| 已主动退出 | 不自动重排 |
| 已无比赛快照 | 不再重复触发重排或退出 |

- `MatchAcceptance` 由应用级 `OnlineSession` 持有，只保存本轮本方 START 意图，不把“已配对”当成“已接受”。本方点击不等同于服务端已经确认接受。
- 收到服务端取消和本地接受超时兜底共用 `handleMatchCancelled()`，在清理快照前计算一次动作，并清除旧排队恢复意图。
- 每次配对、结束和清理比赛都会重置本轮确认状态。
- Activity 重建且尚未开局时，从 Session 恢复已点击 START 的等待页，不依赖会随界面重建重置的 `acceptWaiting`。
- 中文取消提示改为中性描述，避免把本方未操作归因为对方未接受。

## 验证

- Android `testDebugUnitTest`：9 个套件、71 项测试全部通过，失败、错误、跳过均为 0；其中新增 `MatchAcceptanceTest` 12 项，覆盖双方挂机、单方确认、确认状态消耗及重置、重复确认、重复取消、主动退出、好友／练习和未知模式。
- Android `assembleDebug` 成功，生成 `android_memory-game-reborn/build/outputs/apk/debug/memory-game-3.6.3-74-debug-20261017-0817.apk`。
- 服务端 `MatchRoomTimingTest` 定向测试：23 项通过，包含新增双方未接受／仅 A 接受／仅 B 接受的 3 个参数化场景。验证截止前不触发、超时只执行一次 abort、先 abort 再释放回调再通知双方、重复超时与迟到 START 不复活比赛、不正常结算；该房间单元夹具不等同于真实匹配队列映射验证。
- 服务端全部非 Docker `test`：23 个套件、141 项测试通过，失败、错误、跳过均为 0。未执行需要 Docker 的 PostgreSQL `pgTest`。
- Android 构建仍有既有的 Manifest replace、非增量注解处理器和 Gradle 弃用警告，未阻止测试或打包。服务端有 JVM CDS、Gradle 弃用、H2／Flyway 兼容性及 HTTP 负向测试警告，未出现 ERROR，关闭流程正常。
- 最终只读代码审查未发现本次改动新增的阻断问题，`git diff --check` 通过。

## 验证边界

- Android 新增测试直接覆盖纯 Java 接受／重排策略，不是 `OnlineSession` 两个取消入口、Activity 重建或重连的集成测试；两入口接线和生命周期恢复已进行代码检查。
- 未进行双机 UI／生产 30 秒端到端复测。Android MCP 的 ADB 路径包含尾部回车，启动报 `ENOENT`；本次未修改工具配置、安装 SDK 或更改设备状态。
- 未验证 Release 混淆包，未部署生产、未安装设备。
- 现有空 payload 取消帧缺少 `matchId` 的迟到帧风险仍未解决，见 `未解决的风险清单.md` 第 2 项；本次不扩大为协议变更。
