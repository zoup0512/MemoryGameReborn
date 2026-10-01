# MemoryGameReborn

"Memory Games / Brain Training"（原包名 `com.memory.brain.training.games`，2018 年老工程）的反编译离线复刻版。

原工程使用 Gradle 3.0.1 + jcenter，早已无法构建。本项目基于保留下来的反编译源码与资源完成重建，并按需求做了离线化改造：

- 移除全部联网逻辑（在线对战、战绩上传、S3、Firebase 推送、网络同步）
- 移除全部广告与 Facebook SDK（Appodeal / AdMob / Chartboost / MoPub / Unity Ads / Crashlytics 等）及 Google Play 内购
- 完整保留本地益智玩法：23 个游戏 + Sprint / Workout / Challenge / Menu 四个板块（关卡进度、奖杯、IQ 训练、金币、生命值、音效）
- 申请的权限：无（0 项）

## 构建

- Android Gradle Plugin 8.13.2 / Gradle 8.13 / JDK 17
- `gradlew assembleDebug`，产物位于 `build/outputs/apk/debug/`
- 支持库沿用原版 `com.android.support:26.1.0`（未迁移 AndroidX），依赖从 Google Maven / Maven Central 下载

## 目录说明

- `src/main/java/com/cube/memorygames` — 游戏本体（反编译还原 + 修复）
- `src/main/java/com/activeandroid` 等 — 内嵌的第三方库（反编译修复版）
- `src/main/res/xml/bottombar_tabs.xml` — 底部导航定义（Online tab 已移除）
- `tools/` — 还原过程中使用的脚本（资源 ID 重映射、资源去重、官方库源码抓取等），仅供参考

## 备注

- 反编译产生的资源 ID / styleable / 代码伪影问题及修复脚本见 `tools/` 内注释
- 运行需要 Android 4.4（API 19）及以上
