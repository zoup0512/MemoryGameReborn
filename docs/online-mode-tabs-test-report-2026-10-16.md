# 在线三模式标签页验收记录（2026-10-16）

## 结果

已实现“排位赛 / 练习场 / 与好友对战”横向 ViewPager、模式内独立战绩及服务端权威汇总。现有共用金币、奖杯顶栏保留；大厅介绍、个人资料、混合总战绩、账号入口、规则卡已从在线内容区移除。

- Android 单元测试：59 项通过，0 失败，其中新增分模式统计测试 9 项。
- 服务端最终全量测试：138 项通过，0 失败、0 错误、0 跳过，其中新增分模式集成测试 10 项。
- Android 调试 APK 构建通过，服务端 `bootJar` 打包通过。
- 隔离调试包真机 UI 回归：7 组场景全部通过。
- `git diff --check` 通过。

## 实现检查

### Android

- 使用现有 support-design `TabLayout` 与 support-v4 `ViewPager`，未新增依赖或迁移 AndroidX。
- 三个模式各有可纵向滚动的页面，外层 RecyclerView 作为固定视口。
- 标签点击、双向横滑联动；资料刷新只更新组件数据，不重建 Pager，也不抢占正在运行对局的单一监听器。
- 底部导航往返和 Activity 重建保留选择。
- 各页独立显示胜场、负场、平局与胜率。窄屏和大字体按宽度将统计重排为 4 / 2 / 1 列；动作按钮可随文字增长。
- 旧协议缺少 `modeStats` 时显示“—”和不可用提示；已确认空战绩显示 0、0%。
- 对局结束替换完整服务端快照，不做客户端增量累加；重复结算不会重复计数。缓存身份与实时身份不一致时不会读取上一账号的实时战绩。
- 原排位完整性、金币、教程校验，练习难度与连胜，以及好友房间操作保留。
- 新增标签和不可用提示已覆盖现有中、英、德、西、法、葡、俄、乌克兰语资源。

### 服务端

- 对全部非作废 `MatchRecord` 按持久化模式分组，统计不受最近对局列表分页限制。
- 玩家 B 胜负反转；排位机器人仍归排位，明确人机练习归练习场。
- 未知模式、未知结果不计入已知模式，非正数机器人用户 ID 不汇总共同历史。
- WebSocket 登录、对手资料、结算与 REST 用户资料提供同一增量协议。
- 结算事务在持久化记录后读取快照，结算重放路径同样提供当前完整快照；结算投递阶段不再新增查询。
- 原总战绩字段、ELO、金币、奖励上限和连胜规则不变。达到奖励上限后未支付金币的练习胜场仍正确计数。
- 不需要数据库迁移或回填。历史完整性以保留的对局记录为限，不用混合总数猜测缺失历史。

## 自动测试

```bash
JAVA_HOME=F:/jdk17 ./android_memory-game-reborn/gradlew \
  -p android_memory-game-reborn testDebugUnitTest assembleDebug lintDebug --offline

JAVA_HOME=F:/jdk17 ./server_memory-game-reborn/gradlew \
  -p server_memory-game-reborn test bootJar --offline
```

新增测试：

- `android_memory-game-reborn/src/test/java/com/cube/memorygames/api/online/OnlineModeStatsTest.java`
- `server_memory-game-reborn/src/test/java/com/memorygames/pk/ModeStatsIntegrationTest.java`

Android lint 任务完成。旧复刻工程仍有全仓库历史 lint 问题，构建设置 `abortOnError=false`，不能把任务成功解读为全项目 lint 零问题；本功能新增字符串、模式统计模型、标签布局无 Error/Fatal。新 UI 相关剩余 Warning 为调试预览动态语言设置及 TabLayout 背景过度绘制。

### 首轮全量测试中的既有随机时序失败

第一次服务端全量测试为 137 / 138，通过之外的一项是原有 `BotPracticeIntegrationTest.practiceStreakResetsOnLossAndTiersFlowOnAuth`，报错 `the bot must report progress during the round`。日志显示该两秒练习回合机器人没有答对，回合正常结束，并非统计或结算帧投递失败。该测试单独重跑通过，之后未修改机器人、调度或测试断言，服务端完整 138 项重跑全部通过。

首轮失败 XML 保留在 `server_memory-game-reborn/build/online-mode-evidence/bot-practice-first-full-run.xml`，日志匹配 ID 为 `ce58c28ba6bd`。

## 真机 UI 验证

使用 Lenovo TB-Q706F 现有设备，安装隔离包 `com.memory.brain.training.games.accounttest`。正常已安装包未被覆盖。调试预览继承真实 MainMenuActivity，提供本地合成权威快照并阻止真实网络连接。

| 场景 | 结果 |
| --- | --- |
| 三标签点击、双向横滑、各页独立战绩与资料刷新保留标签 | 通过 |
| 练习难度选择和好友房间码输入弹窗打开、取消 | 通过 |
| Activity 重建和底部导航往返恢复选择 | 通过 |
| 旧服务端数据不可用，不冒用混合总数 | 通过 |
| 已确认空战绩为 0 / 0% | 通过 |
| 320dp 配置、1.5 倍字体、统计换行与好友动作纵向滚动可达 | 通过 |
| 英文布局与滑动切页 | 通过 |

样例数据为排位 8 胜 1 负 1 平 / 80%，练习 2 胜 6 负 2 平 / 20%，好友 1 胜 2 负 1 平 / 25%。这些仅是本地预览数据，不是线上用户战绩。

执行脚本：`android_memory-game-reborn/tools/smoke-online-lobby.py`。

截图、UI 树及机器结果：`android_memory-game-reborn/build/online-ui-evidence/`，包括 `01-ranked.png`、`02-practice.png`、`04-swipe-friend.png` 和 `results.json`。已人工查看三模式、中英文、旧协议与大字体滚动截图；三模式布局和战绩显示符合预期。窄屏字体预览只修改隔离 Activity 的资源配置，未改设备全局显示设置。

Android MCP 的 adb 路径包含尾部 CR，设备工具报 `ENOENT`；已验证 SDK 自身的 adb 可用，通过 SDK 绝对路径完成相同安装、UI Automator 和截图操作。未重装 SDK、创建模拟器或更改全局配置。

## 产物与边界

- 正常包名调试 APK：`android_memory-game-reborn/build/outputs/apk/debug/memory-game-3.6.3-74-debug-20261016-1805.apk`。
- 服务端 JAR：`server_memory-game-reborn/build/libs/memorygames-pk-server-0.1.0.jar`。
- 功能与协议说明：`docs/online-mode-tabs.md`。

本次没有执行云端部署、数据库变更、真实用户对局、购买、登录、上传、git 提交或推送。分模式战绩在实际环境需要同步更新服务端；旧服务端只显示不可用。未构建签名 release，未执行 PostgreSQL / Docker `pgTest`，不将 H2 集成测试视作 PostgreSQL 并发验证。
