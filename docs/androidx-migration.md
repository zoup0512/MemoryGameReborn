# AndroidX 支持

Android 客户端已从 Support Library 26.1.0 迁移至 AndroidX 与 Material Components，并将 targetSdk 从 26 提升至 35。保留 minSdk 21、compileSdk 36、Java 8、现有包名及签名配置，不涉及服务端或 HarmonyOS。

## 迁移范围

- `android.useAndroidX=true`，显式关闭 Jetifier。业务源码和内嵌第三方源码直接使用 AndroidX，不依赖构建时改写旧库。
- AppCompat 1.7.1、Core 1.13.1、RecyclerView 1.3.2、CardView 1.0.0、Collection 1.4.0、ViewPager 1.0.0、CustomView 1.1.0、MultiDex 2.0.1、Material Components 1.12.0。
- ButterKnife runtime/compiler 同步升级至 10.2.3；继续保留 final 资源 ID 和 `_ViewBinding` 的反射保护规则。
- 同步迁移 debug 页面、XML 控件、BottomBar 资源代理、Calligraphy 字体探测及内嵌库导入。图片布局改用 AppCompat 的 `app:tint`，颜色选择器同时保留平台与兼容 alpha，RecyclerView 方向参数使用对应的公共常量。
- 对局页 `onActivityResult` 调用超类实现，保留 AndroidX Fragment 与 ActivityResult 的结果分发，然后执行原有回合返回处理。
- 游戏 12 的三个布局使用 `androidx.percentlayout:percentlayout:1.0.0`，保留原来的百分比布局。该组件已弃用，但本次不重写布局，避免混入玩法与屏幕适配变化。
- 删除仅为借用数值常量而引用的旧 Support 内部接口与 media API，不为这些常量引入额外媒体依赖。

## targetSdk 35 兼容（2026-10-23）

targetSdk 从 26 直接提升到 35，必须处理的平台行为：

- **Android 12 exported**：带 intent-filter 的 `SplashActivity` 显式 `android:exported="true"`；`DeviceBootReceiver` 显式 `false`；`MainMenuActivity`、`OnlineHistoryActivity`、`OnlineTopActivity`、`OnlineMatchActivity` 显式 `false`，使 debug overlay 的 `tools:replace` 有确定的基础声明。
- **Android 12 PendingIntent 可变性**：`NotificationManager` 与 `reminder/AppReminder` 的所有 `PendingIntent.getBroadcast` 都加 `FLAG_IMMUTABLE`（保留 `FLAG_UPDATE_CURRENT`）。否则首次启动游戏调用 `AppReminder.setAlarm` 时会在 Android 12+ 抛 `IllegalArgumentException`。
- **Android 14 动态广播**：内嵌 Picasso 的 `NetworkBroadcastReceiver` 改用 `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`；它只监听系统广播，接收器保持私有。
- **Android 13 通知权限**：清单声明 `POST_NOTIFICATIONS`，清除 lint 的 `NotificationPermission` 错误。应用仍不主动弹运行时授权；原有提醒通知缺少通知渠道、使用隐式广播 PendingIntent，属于 targetSdk 26 时代遗留，本次没有重构，通知在 Android 13+ 的实际展示仍取决于系统授权与后续修复。
- **Android 15 边到边**：targetSdk 35 会强制 edge-to-edge，而恢复版页面的顶部栏按固定系统栏高度排版。`values-v35/styles.xml` 在 `GdxTheme` 上设置 `android:windowOptOutEdgeToEdgeEnforcement=true`，保持既有布局；这是临时兼容桥，后续如要支持更大屏/新系统，需要逐屏处理 WindowInsets 后移除。
- **Android 15 Calligraphy 反射**：API 35 上 `LayoutInflater.mConstructorArgs` 不再可反射获取，旧内嵌 Calligraphy 会在 `abc_screen_simple` 膨胀时 NPE。`ReflectionUtils` 对空 Field 做保护，`CalligraphyLayoutInflater.createCustomViewInternal` 在拿不到构造参数时回退到 `createView(name, null, attrs)`，继续用当前 inflater 的 context 完成字体注入。

回归保护新增 `TargetSdk35CompatibilityTest`（targetSdk、exported、PendingIntent 标志、动态广播标志、v35 主题）和 `ReflectionUtilsTest`（空反射目标）。

## 回归保护

`AndroidXMigrationSourceTest` 检查 main/debug 的 Java 和 XML 中没有旧包名、AndroidX/Jetifier/final ID 开关符合要求，以及游戏 12 的所有布局变体均使用已打包的 AndroidX 控件。Gradle 对所有 configuration 拒绝解析 `com.android.support` 依赖，防止新依赖间接带回旧库。

这些静态检查不能替代设备上的布局加载和交互验收。

## 构建与验证

在 `android_memory-game-reborn/` 下使用 JDK 17：

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug -PaccountUiTest=true
./gradlew assembleRelease
./gradlew dependencies --configuration debugRuntimeClasspath
./gradlew dependencies --configuration debugAnnotationProcessor
```

依赖报告不应包含 `com.android.support` 或 `FAILED`。现有 lint 配置不因错误中止构建，因此须读取报告，而不能只看任务退出码。`assembleRelease` 会执行 R8，并按现有逻辑复制已签名 APK；常规构建验证不替代 `build-release.bat` 的正式发布签名锚点流程。

设备验收重点：启动与底部导航、在线 TabLayout/ViewPager 联动、弹窗、字体、ButterKnife 点击绑定、游戏 12 的手机/平板布局，以及 Release 的 Count’em All 反射资源。可用隔离的 `-PaccountUiTest=true` 调试包，避免覆盖正式应用。

## 构建验证（2026-10-22）

- JDK 17 下 `testDebugUnitTest assembleDebug lintDebug assembleRelease -PaccountUiTest=true` 成功；23 个测试类、172 个测试，失败/错误/跳过均为 0。
- Debug 为独立的 `.accounttest` 包；Release 编译、R8、资源压缩与既有签名构建成功。反射资源门禁：DEX 95、资源表 94，均高于门槛 50。该包用于构建验证，未执行正式发布的签名锚点登记流程，也未部署或发布。
- 运行时和注解处理器依赖报告均不含旧 Support 依赖或解析失败；main/debug 的 Java/XML 无旧包引用。
- 独立检查通过：诊断核心 45 条断言、逐题诊断 74 条断言、计数答案 323751 条断言，以及弹窗布局/时序/对比度静态检查。全部资源 XML 可解析，`git diff --check` 通过。
- lint **不是零错误**。以 HEAD 源码在 `build/androidx-baseline/` 下隔离构建作对照，迁移前为 619 Error、27 Fatal、1768 Warning；AndroidX 迁移后为 633 Error、27 Fatal、1780 Warning；targetSdk 35 最终为 632 Error、27 Fatal、1774 Warning。`NotificationPermission`、`ExportedReceiver`、`PendingIntentMutability`、`UnspecifiedRegisterReceiverFlag`、`ExpiredTargetSdkVersion` 均为 0。新增的 tint、alpha、方向常量诊断已修复；新增 21 条 `MissingSuperCall` 为 AndroidX 注解对既有 `onBackPressed` 拦截的检测，保留既有暂停/退出/弹窗行为，不为消除诊断而调用默认返回逻辑。既有问题（如反编译数值常量、遗留翻译/资源）未在本次扩大修复。结果报告位于 `android_memory-game-reborn/build/reports/lint-results-debug.html`。

## AndroidX 真机验收（2026-10-23，targetSdk 26 包）

设备：HUAWEI CDY-AN90，Android 10（API 29），720×1600、320dpi、中文。手机宽度约 360dp，只触发了 `layout/` 资源，未触发 `sw600dp` 平板布局变体。

Debug 隔离包（`.accounttest`，2026-10-22 17:56 构建）：

- `tools/smoke-dialogs.py` 15/15 通过：暂停、重试、挑战结算、奖杯、反馈、训练邀请/帮助、练习退出、房间码、房间等待、难度、金币不足，以及结果/答案/解锁布局。
- `tools/smoke-online-lobby.py` 7/7 通过：Tab 与左右滑动切页、三种模式独立战绩、练习难度与加入房间弹窗、Activity 重建后选中页保持、旧服不可用、空记录、320dp 大字体滚动、英文布局。
- 游戏 12「多与少」第 1 关正常加载百分比布局；调试包的本地数据已启用 `gamesUnlocked`，未改动正式包数据。

Release 签名包（3.6.3 / versionCode 74，证书 SHA-256 `f04258e2…a31ed7`）安装成功：

- 启动、主菜单滚动、底部导航（冲刺/在线/我的）、“我的”资料页、Count’em All 第 1 关答题、暂停弹窗、结束本局与结算页均正常。
- logcat 未出现 `FATAL EXCEPTION`、`InflateException`、`NoClassDefFoundError`。Count’em All 正常显示格子数量选项，说明 R8 后反射读取的 `icg_` drawable 名称仍在。
- Release 为新装数据（60 金币），游戏 12 被 500 金币门槛锁住，所以其运行时布局只在 Debug 包验证；两份包使用同一套布局与 AndroidX 控件，Release 的 R8 风险由 Count’em All 反射路径覆盖。
- 本次 Release 包由裸 `assembleRelease` 产出，未走 `build-release.bat`，签名锚点未登记，仅用于测试。
- 在线页只做了页面导航，未点击匹配、创建房间、加入房间或上传；应用仍可能按既有逻辑连接配置的云端端点。完整 PK 端到端回归需要管理 token 与热配置，不在本次 AndroidX 验收范围内。

补充：`tools/smoke-online-lobby.py` 原先仍断言亲友赛内嵌胜/负/平/胜率，但 b300762 已把亲友赛改为“创建房间/加入房间/亲友赛战绩”统一入口并隐藏内嵌统计。本次按当前产品行为更新了该断言，避免把过期测试当作迁移缺陷。

首次安装后出现过系统“USB 连接方式”对话框和主页“每日训练”建议弹窗遮挡 uiautomator，关闭后重跑通过；两者均非应用缺陷。

证据截图与 UI 层级位于 `android_memory-game-reborn/build/device-evidence/`、`build/dialog-ui-evidence/`、`build/online-ui-evidence/`（`build/` 不入库）。

## targetSdk 35 验收（2026-10-23）

**API 35 / Android 15 模拟器**（`MemoryParty_A_API35`，1080×2400、420dpi、英文）：

- 首个 targetSdk 35 调试包在启动时崩溃：`abc_screen_simple` → `FitWindowsLinearLayout` → Calligraphy `ReflectionUtils.getValue` NPE。按上面的反射回退修复后重新安装，主菜单正常。
- 调试包：`tools/smoke-dialogs.py` **15/15**、`tools/smoke-online-lobby.py` **7/7**；游戏 12「More, Less」第 1 关百分比布局正常。
- Release 签名包：启动、底部导航、主菜单游戏图片（Picasso）、Count’em All 第 1 关均正常；logcat 无 `FATAL EXCEPTION`、`InflateException`、`SecurityException`、`PendingIntent` 异常。
- 截图确认 `values-v35` 的 edge-to-edge 退出选项生效：状态栏与手势导航栏独占区域，页面内容未被覆盖。
- `POST_NOTIFICATIONS` 已声明，但本次未触发运行时授权弹窗。

**HUAWEI CDY-AN90 / Android 10（API 29）真机**（targetSdk 35 包）：

- 调试包：弹窗 **15/15**、在线大厅 **7/7**。
- Release：启动、“我的”资料页、Count’em All 第 1 关正常；logcat 无崩溃。说明 targetSdk 35 未破坏低版本兼容路径。
- 只覆盖 360dp 手机宽度，未触发 `sw600dp` 平板布局。
- Release 仍为裸 `assembleRelease` 测试包，签名锚点未登记；正式发布继续使用 `build-release.bat`。

证据位于 `build/device-evidence/api35-*`、`build/device-evidence/phone-target35-*`、`build/api35-dialog-evidence/`、`build/api35-online-evidence/`、`build/phone-target35-dialog-evidence/`、`build/phone-target35-online-evidence/`。

历史加固设计中关于“未迁移 AndroidX”的描述是当时的技术背景，当前构建状态以本文与 Gradle 配置为准；本次不改变 CryptoVault 的平台 Keystore 实现。
