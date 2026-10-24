# MemoryGameReborn

"Memory Games / Brain Training"（原包名 `com.memory.brain.training.games`，2018 年老工程）的反编译离线复刻版 + 多人在线 PK 模式（`dev/online-PK` 分支开发中）。

原工程使用 Gradle 3.0.1 + jcenter，早已无法构建。本项目基于保留下来的反编译源码与资源完成重建，并按需求做了离线化改造：

- 移除全部联网逻辑（在线对战、战绩上传、S3、Firebase 推送、网络同步）
- 移除全部广告与 Facebook SDK（Appodeal / AdMob / Chartboost / MoPub / Unity Ads / Crashlytics 等）及 Google Play 内购
- 完整保留本地益智玩法：23 个游戏 + Sprint / Workout / Challenge / Menu 四个板块（关卡进度、奖杯、IQ 训练、金币、生命值、音效）

## 在线 PK 模式（dev/online-PK）

在离线复刻基础上重建原版的在线 1v1 对战（需求文档 `docs/online-pk-prd.md`）：

- 客户端：恢复第 5 个 Online tab 与原版游戏内在线分支（同种子同题、限时得分竞速），新增
  `api.online` 包（WebSocket 会话、对局编排、对手比分 HUD），单机路径零改动
- 服务端：`server_memory-game-reborn/` 下独立的 Spring Boot 3 工程（匹配/ELO/Bot 兜底/服务端权威计分），
  `./gradlew test` 含双端全量对局集成测试
- minSdk 提升至 21（TLS 1.2）；申请权限：INTERNET、ACCESS_NETWORK_STATE

## 游客与注册账号

游客可直接游玩，邮箱验证后在原 `userId` 上升级；金币、ELO与战绩不丢失。新增账号中心提供登录、绑定、设备会话管理、找回密码及注销冷静期。单机数据库仍离线独立，在线金币以服务端为准，不把本地余额导入线上。

新账号需要 HTTPS/WSS 与 SMTP；默认旧游客明文地址继续兼容，不能直接用于密码登录。详见 [账号体系](docs/account-system.md)、[部署与验收](docs/account-system-deployment.md) 和 [实施验收报告](docs/account-system-test-report-2026-10-13.md)。

## 个人资料

“我的”和账号中心提供内置头像选择与昵称编辑。头像限定为 12 款预设，新游客昵称由受控中文词库自动生成；自定义昵称通过服务端本地规则及文本机器审核后才公开，审核异常保留原资料。服务端默认调用同机 `127.0.0.1:18081` 的敏感词检测服务（无需开放公网端口），命中或检测不可用时拒绝改名，不影响安全默认昵称和单独更换头像。详见 [昵称机器审核](docs/nickname-moderation.md) 与 [账号体系](docs/account-system.md#个人资料与昵称审核)。

## 在线模式与战绩

在线内容区仅保留“排位赛 / 练习场 / 与好友对战”三个可点击、可左右滑动的标签页，每页展示各模式独立的胜负场、平局和胜率。战绩从服务端完整有效对局记录汇总，不混用旧版总战绩；真实独立统计需配套更新服务端。详见 [页面与统计口径](docs/online-mode-tabs.md)。

## 对局诊断

客户端在“我的”页面提供手动上传诊断日志，服务器记录可关联的回合、连接与退出事件，管理员可按报告编号查看上传记录。日志有容量、保留期和脱敏限制，详见 [诊断日志与上传](docs/diagnostic-logging.md)。安装新客户端并部署新服务端后生效，不能补回旧版本缺失的日志。

## 构建

- Android Gradle Plugin 8.13.2 / Gradle 8.13 / JDK 17
- Android：`cd android_memory-game-reborn && gradlew assembleDebug`，产物位于 `build/outputs/apk/debug/`，
  文件名带版本号与编译时间戳：`memory-game-<versionName>-<versionCode>-<variant>-<yyyyMMdd-HHmm>[-unsigned].apk`
- `gradlew assembleRelease` 结束后会把已签名的 release APK 复制到 `android_memory-game-reborn/release/`
  （未签名的 `-unsigned` 包只留在 `build/` 里）。编译时间戳同时写入 `BuildConfig.BUILD_TIME` 与清单
  `com.memory.brain.training.games.build_time`，安装包被改名或转发后仍能确认它出自哪次构建
- 出正式签名包（首次配置见 `android_memory-game-reborn/keystore.properties.example`，该
  `keystore.properties` 不入库）：在 `android_memory-game-reborn/` 下执行 `build-release.bat`。
  脚本先登记签名指纹锚点（`writeIntegrityAnchor`）、再打签名包、校验产物落在 `release/`、打印证书
  SHA-256（用于留存），最后把 `IntegrityAnchor.java` 还原为仓库中的占位版本。没有
  `keystore.properties` 时不要直接用 `assembleRelease` 当发行包：产物是 unsigned，且签名哨兵未生效。
  加固设计与发布注意见 [客户端加固设计](docs/android-client-hardening-design.md)
- Android 客户端使用 AndroidX + Material Components，ButterKnife 10.2.3 生成 AndroidX 绑定；不启用 Jetifier，依赖从 Google Maven / Maven Central 下载。`minSdk 21`、`targetSdk 35`、`compileSdk 36`；targetSdk 升级涉及的通知权限、PendingIntent、动态广播与 Android 15 边到边兼容见 [AndroidX 支持](docs/androidx-migration.md)


## HarmonyOS 手机 App

`harmony_memory-game-reborn/` 是把 Android 单机版复刻成 HarmonyOS 手机 App 的独立工程：
23 款“冲刺”游戏、新手教学、关卡推进、暂停/失败/结算与音效均已移植。它不包含服务端、
金币、广告、奖杯、战绩图表和底部菜单栏；全部游戏默认解锁，失败可免费重试当前关。
`bundleType` 为 `app`，`installationFree: false`，仅面向 `phone`；包名为 `com.razor.memory.brain.training.games`，安装后从桌面启动。2026-10-24 Release 未签名 HAP 为 1,937,670 字节，真机与正式分发仍需配置签名。旧元服务保留在本地分支 `backup/main-atomic-service-20261024-46aa3f1`；新包名不自动继承旧元服务存档。

构建、签名、与 Android 版的差异和验收截图见
[harmony_memory-game-reborn/README.md](harmony_memory-game-reborn/README.md)。

## 目录说明

仓库包含 `android_memory-game-reborn/`（Android 客户端）、`server_memory-game-reborn/`（在线 PK 服务端）与 `harmony_memory-game-reborn/`（HarmonyOS 手机 App 复刻），`docs/` 为共享文档。

- `android_memory-game-reborn/src/main/java/com/cube/memorygames` — 游戏本体（反编译还原 + 修复）
- `android_memory-game-reborn/src/main/java/com/cube/memorygames/api/online` — 在线 PK 客户端（新增）
- `server_memory-game-reborn/` — 在线 PK 服务端（Spring Boot 3，新增）
- `harmony_memory-game-reborn/` — HarmonyOS 手机 App 复刻（ArkTS，单机 23 款游戏，新增）
- `docs/online-pk-prd.md` — 在线 PK 需求文档
- `android_memory-game-reborn/src/main/java/com/activeandroid` 等 — 内嵌的第三方库（反编译修复版）
- `android_memory-game-reborn/src/main/res/xml/bottombar_tabs.xml` — 底部导航定义（5 tab，含 Online）
- `android_memory-game-reborn/tools/` — 还原过程中使用的脚本（资源 ID 重映射、资源去重、官方库源码抓取等），仅供参考

## 备注

- 反编译产生的资源 ID / styleable / 代码伪影问题及修复脚本见 `android_memory-game-reborn/tools/` 内注释
- 运行需要 Android 5.0（API 21）及以上
