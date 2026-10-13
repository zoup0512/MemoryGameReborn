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

## 构建

- Android Gradle Plugin 8.13.2 / Gradle 8.13 / JDK 17
- Android：`cd android_memory-game-reborn && gradlew assembleDebug`，产物位于 `build/outputs/apk/debug/`，
  文件名带版本号与编译时间戳：`memory-game-<versionName>-<versionCode>-<variant>-<yyyyMMdd-HHmm>[-unsigned].apk`
- `gradlew assembleRelease` 结束后会把已签名的 release APK 复制到 `android_memory-game-reborn/release/`
  （未签名的 `-unsigned` 包只留在 `build/` 里）。编译时间戳同时写入 `BuildConfig.BUILD_TIME` 与清单
  `com.memory.brain.training.games.build_time`，安装包被改名或转发后仍能确认它出自哪次构建
- 支持库沿用原版 `com.android.support:26.1.0`（未迁移 AndroidX），依赖从 Google Maven / Maven Central 下载

## 目录说明

仓库分两个子工程：`android_memory-game-reborn/`（客户端）与 `server_memory-game-reborn/`（服务端），`docs/` 为两端共享文档。

- `android_memory-game-reborn/src/main/java/com/cube/memorygames` — 游戏本体（反编译还原 + 修复）
- `android_memory-game-reborn/src/main/java/com/cube/memorygames/api/online` — 在线 PK 客户端（新增）
- `server_memory-game-reborn/` — 在线 PK 服务端（Spring Boot 3，新增）
- `docs/online-pk-prd.md` — 在线 PK 需求文档
- `android_memory-game-reborn/src/main/java/com/activeandroid` 等 — 内嵌的第三方库（反编译修复版）
- `android_memory-game-reborn/src/main/res/xml/bottombar_tabs.xml` — 底部导航定义（5 tab，含 Online）
- `android_memory-game-reborn/tools/` — 还原过程中使用的脚本（资源 ID 重映射、资源去重、官方库源码抓取等），仅供参考

## 备注

- 反编译产生的资源 ID / styleable / 代码伪影问题及修复脚本见 `android_memory-game-reborn/tools/` 内注释
- 运行需要 Android 4.4（API 19）及以上
