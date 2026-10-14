# 安卓客户端加密与防破解加固设计（不依赖第三方加固）

更新日期：2026-10-14
状态：**P1/P2/P3 已实施并在真机验证**（2026-10-14，Huawei BKL-AL20 / Android 9；见 §9.4 审查修正、§9.5 真机测试记录、§9.6 迁移引擎 P0 修复）；P3+ NDK 哨兵未实施，按需追加。
发布注意：**指纹锚点已登记（2026-10-14）**，登记值见 §3.3；本机 keystore `C:/jks/android.jks` 已配置在 `keystore.properties`（不入库）。出正式签名包直接跑 `android_memory-game-reborn\build-release.bat`（见下）。手动路径：
```
gradlew writeIntegrityAnchor "-PintegrityCertSha256=<release证书SHA-256，64位hex>"
```
或注入 release keystore 后直接 `gradlew writeIntegrityAnchor`（指纹可用 `gradlew printReleaseCertSha256` 打印）。未登记时签名哨兵不生效（其余各层照常工作）。

一键出包（2026-10-14 加）：keystore 配置在 `android_memory-game-reborn/keystore.properties`（格式见同目录 `keystore.properties.example`，该文件不入库），执行 `android_memory-game-reborn\build-release.bat` 即可 —— 脚本按「登记锚点 → `assembleRelease` → 校验 `release/` 收到签名包并打印证书 SHA-256 → 还原 `IntegrityAnchor.java` 占位」的顺序执行，`--keep-anchor` 可保留登记后的锚点文件。`keystore.properties` 存在时 `assembleRelease` 本身就是签名包（签名配置由该文件驱动，密码不经命令行）；它与 `android.injected.signing.*` 同时存在会直接报错，避免锚点与签名证书不一致。登记后的指纹请连同 keystore 一并归档。
范围：仅安卓客户端自身（`android_memory-game-reborn`）。API 服务端安全、传输链路安全不在本方案范围内（`ws://` 明文传输是已知的独立风险，另行处理）。
约束：不引入商业加固（360 加固、腾讯乐固、爱加密等）、不引入 ollvm 等第三方保护工具链；优先使用平台自带能力（R8、AndroidKeyStore、javax.crypto），新增依赖仅限构建工具链本身（NDK 可选）。

---

## 0. 现状摘要（设计输入）

| # | 事实 | 位置 | 影响 |
|---|------|------|------|
| 1 | release 构建未开启混淆（`minifyEnabled false`） | `build.gradle:42-44` | jadx 一键还原全部业务逻辑，是最薄弱环节 |
| 2 | 全工程无任何加密代码（无 Cipher/Mac/MessageDigest 调用） | 全局 | 本地数据全部明文 |
| 3 | 联网身份凭据明文存 SharedPreferences：uuid、token、userId | `OnlineIdentity.java` | root/备份可直接提取并冒用账号 |
| 4 | 本地经济数据明文 SQLite：`money`、`gamesUnlocked`、`adsRemoved`、`challengeRating`、`onlineRank` | `LocalUser.java`、ActiveAndroid `memorygames.db` | 可用 SQLite 编辑器直接改金币/解锁 |
| 5 | 欢迎礼包标记明文：`welcomeGiftGiven` | `OnlineIdentity.java` | 清除标记可重复领礼包 |
| 6 | `android:allowBackup="true"` | `AndroidManifest.xml` | adb backup / 云自动备份可带走 token 与数据库 |
| 7 | endpoint override 在 release 仍会被 `getEndpoint` 读取（UI 入口已有 debug 门控） | `OnlineConfig.java:35-46` | 重打包者可写入 pref 把 release 客户端引到假服务器 |
| 8 | 代码库为反编译恢复的源码，大量依赖反射：ActiveAndroid（模型反射）、Gson（模型序列化）、ButterKnife（注解处理）、Calligraphy 等 vendored 库 | `build.gradle:130-139` | 混淆需配套精细 keep 规则，否则运行期崩溃 |
| 9 | 无 NDK 代码（无 .c/.cpp，无 loadLibrary） | 全局 | 现有逻辑全部暴露在 smali 层；引入原生层是可选项 |
| 10 | 技术栈：minSdk 21 / targetSdk 26 / support-26.1.0 / Java 8 / AGP 8.13.2；99 个源文件 import 旧版 `android.support.*` | `build.gradle`、全局 | 引 AndroidX 库（如 Security-Crypto）需整体 AndroidX 迁移（Jetifier 只改依赖字节码，不改 vendored 源码 import）；且 security-crypto 1.1.0 已将全部 API 弃用，官方推荐直接用 Keystore。故自研 CryptoVault，Keystore API 按 API 等级分路径 |
| 11 | 签名 keystore 不入库，构建时注入；产物带版本号+时间戳命名 | `build.gradle:62-108` | 具备做签名校验的锚点条件 |

## 1. 目标与威胁模型

### 1.1 保护对象（按优先级）

- **P0 身份凭据**：PK 模式的 uuid/token/userId。被提取 = 账号被冒用、代打、刷榜。
- **P1 经济与权益数据**：金币镜像、游戏解锁、去广告、欢迎礼包标记。被篡改 = 本地显示作弊、重复领奖。
- **P2 代码与逻辑**：联网协议、反作弊相关客户端逻辑、私有算法。被逆向 = 外挂与协议伪造的成本降低。
- **P3 包完整性**：客户端可识别"自己被重打包/被调试"，并降级运行。

### 1.2 威胁分级

| 级别 | 攻击者能力 | 典型手段 |
|------|-----------|----------|
| A1 | 会用工具 | jadx 反编译、SQLite 编辑器、SharedPreferences 查看器 |
| A2 | 会改包 | smali 修改后重签名、资源替换、去校验 |
| A3 | 会调试 | IDEA/Android Studio attach、断点跟踪、内存搜索改值 |
| A4 | 专业逆向 | root + Frida/Xposed hook、脱壳、so 分析 |

### 1.3 对抗上限与设计原则（必读）

**客户端加固的原理性上限**：一切客户端检测代码都运行在攻击者控制的设备上。对 A4 级攻击者（root + Frida），任何纯客户端方案都无法绝对防住——校验函数本身可被 hook。因此本方案的目标是：

1. **抬门槛**：把 A1/A2 级"随手破解"的成本抬到需要 A3/A4 级专业能力；
2. **可检测**：让被篡改的客户端可被自身识别（即使静默）；
3. **可降级**：对可疑环境功能降级而非崩溃，避免暴露检测点、避免伤及正常用户；
4. **不藏真秘密**：客户端内不嵌入任何"泄露即损失"的密钥。嵌入的常量只做校验用途，且按"迟早被提取"设计（摘要拆散、无明文、多点使用）。

公平性的最终裁决在服务端权威（服务端已有 `AntiCheatPolicy`，超出本方案范围）；本方案使客户端从"破解的软柿子"变成"需要专业投入、且可疑行为可被感知的目标"。

## 2. 总体架构

```
┌─ 构建期（P1，必做）──────────────────────────────┐
│  R8 混淆 + 资源收缩 + 日志剥离 + 备份收敛          │
│  → 对抗 A1 静态分析；消灭"jadx 一键还原"          │
└──────────────────────────────────────────────┘
┌─ 数据层（P2，必做）──────────────────────────────┐
│  CryptoVault：AndroidKeyStore 密钥（不可导出）     │
│   ├─ SecurePrefs：token/uuid/userId AES-GCM 加密  │
│   └─ 经济数据列级 HmacSHA-256 防篡改              │
│  → 对抗 A1/A3 本地数据提取与篡改                  │
└──────────────────────────────────────────────┘
┌─ 运行时（P3，必做检测，可选 NDK）──────────────────┐
│  签名哨兵（多点、降级、无日志）+ 调试/注入信号      │
│  [+ 可选] 极小 NDK 哨兵：检测逻辑下沉 so 层        │
│  → 对抗 A2 重打包；提高 A3 动态调试成本            │
└──────────────────────────────────────────────┘
```

三层独立生效、互不依赖：只做 P1 也已消除最大暴露面；P2/P3 逐层加码。

## 3. P1 构建期加固（性价比最高，1-2 天）

### 3.1 开启 R8 混淆与资源收缩

`build.gradle` 变更：

```groovy
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

`proguard-rules.pro` 草案（针对本工程反射点定制，逐条对应现状 #8）：

```
# ---- 反射敏感模型（ActiveAndroid 按 @Table/@Column 反射建表，Gson 按字段反射序列化）----
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# ---- Gson（2.7 无自带消费规则）：泛型签名 + 防 TypeToken 匿名类被改名/合并 ----
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.cube.memorygames.api.local.model.** { *; }
-keep class com.cube.memorygames.api.local.challenge.** { *; }
-keep class com.cube.memorygames.api.local.workout.** { *; }
-keep class com.cube.memorygames.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.activeandroid.Model { *; }

# ---- ActiveAndroid / vendored 库（反编译源码内嵌，规则无法随 AAR 自带，必须显式保留）----
-keep class com.activeandroid.** { *; }
-dontwarn com.activeandroid.**

# ---- ButterKnife 8.5.1：注解处理器生成的 $$ViewBinder 通过反射查找 ----
-keep class **$$ViewBinder { *; }
-keepclasseswithmembernames class * { @butterknife.* <fields>; }
-keepclasseswithmembernames class * { @butterknife.* <methods>; }

# ---- 其余 vendored 库：先宽后紧 ----
-dontwarn org.java_websocket.**
-keep class com.github.mikephil.charting.** { *; }
-keep class uk.co.chrisjenx.calligraphy.** { *; }
-keep class com.roughike.bottombar.** { *; }
-keep class com.wenchao.cardstack.** { *; }
-keep class com.github.florent37.viewanimator.** { *; }

# ---- 包名扁平化 + 不可读映射字典 ----
-repackageclasses ''
-allowaccessmodification
-obfuscationdictionary proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# ---- 剥离冗长日志（保留 w/e），对应现状 #8 的 analytics/logEvent 噪声 ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```

实施要点：

- 混淆字典 `proguard-dict.txt` 用无意义短字符序列（非 a/b/c 顺序递增，避免被模式识别），随包入库。
- **首次开启必须全量回归**（重点：在线 PK 双端对局、难度/关卡加载、图表页、字体），因为反编译恢复代码可能存在规则未覆盖的隐藏反射。发现崩溃 → 对应类加 keep，逐步收紧，不要一步到位删除宽泛规则。
- 灰度策略：先出一版内测包（自装）跑双设备联机全量回归，再进正式发布。这与现有 `assembleRelease` → `release/` 发布流程兼容，无需改动发布脚本。
- `shrinkResources` 首开注意 `tools:keep` 兜底（多语言 `resourceConfigurations` 已裁剪过，风险低）。

### 3.2 备份与调试面收敛

- `AndroidManifest.xml`：`android:allowBackup="false"`。当前 targetSdk 26 在 Android 6+ 上开启自动备份，SharedPreferences（含 token）与 `memorygames.db` 会被备份到用户云盘，adb backup 亦可本机提取（现状 #6）。关闭后不损失任何功能（游戏无跨设备迁移需求；身份体系本就支持重新注册）。
- `OnlineConfig.getEndpoint`：改为**仅 debug 构建读取 override**，release 恒返回 `DEFAULT_ENDPOINT`（现状 #7）。UI 门控已在 `OnlineLobbyAdapter.showEndpointDialog` 存在，此改动补上存储层的口子，防止重打包/改机写入 pref 把客户端引向假服务器。
- 确认 release 变体 `debuggable=false`（AGP 默认，现状已满足）；`DebugSchulteActivity` 已隔离在 debug 源集，保持不变。
- `OnlineDebug` 的 `FLAG_DEBUGGABLE` 门控现状正确，保持。

### 3.3 签名密钥管理（为 P3 铺路）

- release keystore 继续不入库。本机路径 `C:/jks/android.jks`（本工程用 `keystore.properties` 引用，该文件在 .gitignore），别名 `key0`，keystore 文件 + 密码仍须离线备份——丢失即无法继续用同一证书出包与热更新签名校验。
- **登记 release 证书 SHA-256**（`keytool -list -v`），该指纹是 P3 签名哨兵的校验锚点。
  **已登记（2026-10-14）**：`F0:42:58:E2:20:39:5E:8A:D2:39:60:34:66:ED:18:C3:F7:11:50:B6:87:9B:64:9B:21:A9:ED:8A:69:A3:1E:D7`
  （`L=Hang Zhou, CN=NS`，有效期至 2050-12-12，与 2026-10-13 已签名发行包同一证书；`gradlew printReleaseCertSha256` 可随时复核）。换 keystore 后必须重跑 `writeIntegrityAnchor`，否则哨兵会把自家新包判为重打包。

### 3.4 P1 验收标准

- release APK 经 jadx 打开：业务类名全部混淆、日志 v/d/i 剥离、无明文 `DEFAULT_ENDPOINT` 之外的敏感常量。
- `adb backup` 无法取回 `online_identity` 与数据库。
- 双设备在线 PK 全量回归通过（对照 `docs/online-pk-full-regression-*.md` 流程）。

## 4. P2 本地数据加密（AndroidKeyStore，纯平台 API，3-5 天）

### 4.1 CryptoVault：密钥层

新建 `com.cube.memorygames.security.CryptoVault`，唯一密钥入口。两条路径按 API 等级分派（现状 #10 决定必须分）：

- **API 23+（主路径）**：在 AndroidKeyStore 内生成两把不可导出密钥——
  - `vault_aes`：AES-256/GCM/NoPadding，`setRandomizedEncryptionRequired(true)`（GCM 随机 IV 由系统保证不重用），`setPurposes(ENCRYPT|DECRYPT)`；
  - `vault_mac`：HmacSHA256，`setPurposes(SIGN|VERIFY)`。
  - 密钥材料不出 TEE/StrongBox（`setIsStrongBoxBacked(true)` 在 API 28+ 尽力开启，失败降级 TEE）。
- **API 21/22（兜底）**：AndroidKeyStore 该年代不支持 AES/HMAC 密钥，用 `KeyPairGeneratorSpec` 生成 RSA 密钥对，包裹（RSA/ECB/PKCS1Padding 加密）一把本地随机生成的 AES 密钥后落盘。弱于主路径（密钥可被 root 提取 RSA 私钥后解出），文档明示为 best-effort；Android 5.x 设备存量占比可忽略，不值得更大投入。
- **密钥失效处理**：Keystore 异常（恢复出厂、锁屏凭据变更等导致 `KeyPermanentlyInvalidated`）→ 删除重建密钥。后果是本地身份重新注册（guest 体系重新领 token，代价低），**绝不**为此引入可导出的密钥备份。

### 4.2 SecurePrefs：凭据加密存储

新建 `SecurePrefs`（加密 SharedPreferences 装饰层，接口与现有用法一致）：

```java
// 写：AES-GCM 加密，密文格式 [1字节版本][1字节IV长度][IV][密文+16字节GCM tag]，Base64 落盘
SecurePrefs.put(context, "online_identity", "token", token);
// 读：解密失败/篡改 → 返回 null（按"不存在"处理，见 4.4）
String token = SecurePrefs.get(context, "online_identity", "token");
```

迁移路径（`OnlineIdentity` 改造，保持对外 API 不变）：

1. 读取时先尝试密文键；命中明文旧键（升级场景）→ 返回值不变，同时加密回写新键并删除明文旧键；下次读走密文。
2. 覆盖键：`uuid`、`token`、`userId`。`nickname`、`welcomeGiftGiven` 非机密但一并纳入（welcomeGiftGiven 是 P1 级权益标记，见 4.3）。
3. 不引入 EncryptedSharedPreferences（AndroidX Security-Crypto）：一是需要整体 AndroidX 迁移（99 个源文件 import 旧版 support 库，Jetifier 无法转换 vendored 源码），代价远超收益；二是官方在 1.1.0（2025-07 稳定版）已将全部 API 弃用，明确推荐"直接使用 Android Keystore 平台 API"——即本方案的 CryptoVault 路线。

### 4.3 本地经济数据防篡改（列级 MAC）

目标不是保密（金币数额无所谓），而是**不可被静默篡改**（现状 #4/#5）。不采用 SQLCipher 整库加密（需替换 SQLite 引擎，与 vendored ActiveAndroid 冲突，且引入第三方原生库违背约束，见 §8）。

方案：ActiveAndroid 模型落库前对敏感字段做 HMAC 校验列。

- 新增 `SecurityMac` 工具：`mac = HmacSHA256(vault_mac, versionTag + "|" + money + "|" + gamesUnlocked + "|" + adsRemoved + "|...")`，字段清单与 `LocalUser` 一致：`money`、`gamesUnlocked`、`adsRemoved`、`challengeRating`、`onlineRank`，外加 `welcomeGiftGiven`（存 SecurePrefs，直接受 GCM 完整性保护）。
- `LocalUser.save()` 前计算写入新列 `sec_mac`（DB version 16 → 17 迁移，仅加列，符合"脆弱恢复库不做大迁移"的既有判断）；读取时校验，**不匹配 → 置 tainted 标记**：
  - 金币以服务端余额为准刷新（在线对局结算本来就会回写本地镜像）；
  - 单机功能不封禁、不弹窗（避免误伤与暴露逻辑），仅拒绝以本地 `money` 作为在线入场费扣减依据。
- 局限明示：A4 攻击者可 hook 校验函数或重放合法 MAC。此层的价值是：改值必须同时理解并绕过校验逻辑，成本从"SQLite 编辑器改一个 int"抬到"逆向 R8 混淆后的校验链路 + 动态调试"。

### 4.4 错误处理纪律（防 oracle）

- 所有解密/验签失败路径按"数据不存在"处理：返回 null/默认值，**不 crash、不 toast、不打日志**——避免给攻击者提供可观测的"校验命中"信号。
- CryptoVault 初始化失败（Keystore 不可用的极少数机型）→ 全功能降级为明文模式 + 内存 tainted 标记，功能可用但该设备数据不受保护；不做"加密失败就拒绝启动"。

### 4.5 P2 验收标准

- root 设备直接编辑 prefs/DB：token 读不回（SecurePrefs 置空 → 重新注册）、金币改动在下次校验/结算时被识别并纠正。
- 升级安装（旧明文 → 新版本）自动迁移，无感。
- API 21/22 真机 + API 23+/28+/30+ 模拟器各一台回归通过。

## 5. P3 运行时自保护（1 周；NDK 部分可选）

### 5.1 签名哨兵（重打包检测，核心）

新建 `com.cube.memorygames.security.IntegritySentinel`：

```java
// 启动时与进入 Online 大厅时各调用一次，结果缓存在静态字段
boolean intact = IntegritySentinel.isReleaseSignature(context);
```

- 校验内容：`PackageManager.GET_SIGNATURES` 取签名证书 DER → SHA-256 → 与锚点比对（锚点 = §3.3 登记的 release 证书指纹）。
- **锚点常量混淆**（对应原则 #4）：构建期 Gradle task 把指纹 32 字节与随机掩码异或后拆成两个 int 数组注入常量类，运行期还原。dex 中不出现明文指纹，A1 级 `strings`/jadx 搜索不可直接命中。
- **多点校验、分散使用**（对应原则 #3）：至少两处独立调用（Application 启动、Online 大厅进入），判定结果不做统一 "if(!ok) kill()"，而是分别影响各自的后续行为：
  - Online 大厅：`intact == false` → 本次会话排位入口禁用、仅保留练习模式（UI 表现为普通置灰，无任何"检测"文案）；
  - 会话标记：置内存级 `TAINTED`，供后续功能（如降级开关）分散消费；不落盘、不上报（上报通道涉及服务端，超范围，仅留接口注释）。
- 不做退出/崩溃式响应：重打包者第一目标就是 patch 掉 `exit()`，与其给对方一个明确的 patch 点，不如让功能静默降级且不指出降级原因。

### 5.2 调试与注入信号

- Java 层低成本信号：release 构建出现 `FLAG_DEBUGGABLE=true`（被 tamper 的强信号）、`Debug.isDebuggerConnected()`。采样点与结果消费同 §5.1 的分散原则。
- 检测到异常**不打印、不弹窗**，只推进降级标记。

### 5.3 root / 模拟器弱信号（仅记录，不强拦）

- 信号：`su` 常见路径存在、`Build.TAGS` 含 test-keys、`qemu`/`goldfish` 系统属性、传感器数量异常。
- 单机玩法永不拦截；Online 模式作为降级输入之一。真正风控裁决在服务端（超范围）。此层是"可选项中的可选项"，防止误伤开发与刷机用户是第一原则。

### 5.4 可选：NDK 原生哨兵

现状无任何原生代码（现状 #9）。引入一个**极小**自研模块（1-2 个 `.c` 文件，NDK 自带 clang，无第三方库）：

- 承载：签名指纹还原与比对、`/proc/self/status` 的 TracerPid 读取、自身 `ptrace(PTRACE_TRACEME)` 占位。
- Java 层只拿不透明结论（如 `int nativeState()`），判定逻辑不可经 smali patch 直达。
- 成本：Windows 构建环境装 NDK + CMake，Gradle `externalNativeBuild` 配置；APK 增加 ~几十 KB。
- 收益评估：把最容易被 patch 的"比对+判定"移出 dex 层，对 A3 级攻击者成本提升明显；对 A4（Frida hook native）仍非绝对防线。
- 决策建议：P3 的 Java 版哨兵先行落地；NDK 版视被破解的实际态势决定是否追加，不作为首发内容。

### 5.5 P3 验收标准

- 用 debug key 重签名的 APK：启动与 Online 功能静默降级（排位置灰），无崩溃、无提示文案。
- 正常 release 包全功能无感。
- jadx 搜索不到明文证书指纹；检测相关代码在混淆后不可按名字定位。

## 6. 算法与密钥管理细则

| 用途 | 算法 | 提供 API | 备注 |
|------|------|----------|------|
| 凭据加密 | AES-256-GCM | AndroidKeyStore + javax.crypto.Cipher | 随机 12B IV 随密文存储，`setRandomizedEncryptionRequired(true)` 防重用 |
| 经济数据完整性 | HmacSHA-256 | AndroidKeyStore（Keystore 内 HMAC 密钥） | 输入含版本 tag，防跨字段重排 |
| 证书指纹 | SHA-256 | java.security.MessageDigest | 唯一"指纹类"用途 |
| 密钥派生 | 不需要 | — | 用途隔离靠两把独立 Keystore 密钥实现，不做 HKDF，减少自研密码学代码面 |

- 全部为 API 21 起平台自带（HMAC/GCM 的 Keystore 密钥自 API 23；21/22 走 §4.1 兜底），不引入 BouncyCastle/SpongyCastle/Tink。
- **零硬编码主密钥**：所有密钥或生于 Keystore、或由 Keystore 密钥包裹；BuildConfig 中只有混淆后的证书指纹（校验用途，泄露损失 = 重打包检测失效，可接受）。
- 密文格式统一 `[version(1B)][ivLen(1B)][iv][ciphertext+tag]`，版本字段为未来算法轮换预留，IV 长度随密文存储（Keystore 路径的 IV 由系统生成，软件路径自选 12 字节；见 §9.4 审查修正 #2）。

## 7. 实施路线图

| 阶段 | 内容 | 工作量 | 依赖 |
|------|------|--------|------|
| P1 | R8 混淆 + 资源收缩 + 日志剥离；allowBackup=false；release 忽略 endpoint override；登记证书指纹 | 1-2 天 + 全量回归 | 无 |
| P2 | CryptoVault + SecurePrefs 迁移 online_identity；LocalUser 列级 MAC（DB v17） | 3-5 天 + 真机回归 | 无（与 P1 并行可） |
| P3 | IntegritySentinel（Java 版）+ 降级策略 | 3-5 天 | P1 的证书指纹 |
| P3+ | NDK 原生哨兵 | 1 周（含构建链配置） | P3 落地后按需决策 |

回归基线：每阶段出包后执行 `docs/online-pk-full-regression-*.md` 双设备流程 + 单机核心玩法冒烟（关卡、难度、挑战、图表）。

## 8. 明确不做与已知局限

- **不做传输安全**：`ws://` 明文链路、证书锁定、请求签名等属 API/服务端安全范畴，按本次范围划分另行处理。此处仅备注：当前方案中 token 的本地加密不解决传输被嗅探问题。
- **不采用 SQLCipher**：整库加密需替换 SQLite 实现，与 vendored ActiveAndroid 深度耦合冲突，且引入第三方原生 so 违背"无第三方加固组件"约束；列级 MAC + 服务端对账的收益/成本比更优。
- **不做 dex 自哈希校验**：APK 内嵌自身哈希存在"鸡生蛋"问题（产物先于哈希存在），只能对间接对象（如 resources.arsc）做哈希，绕过成本低、误报维护成本高，收益不如签名哨兵。
- **不承诺对抗 A4**：root + Frida 的专业攻击者可绕过本方案所有客户端检测。方案的定价是：让攻击从"一下午"变成"需要专业逆向能力 + 每次版本更新重做"，并让可疑客户端可被识别与降级。
- **不引入任何第三方加固/保护组件**：无壳、无 ollvm、无商业 SDK；全部能力来自 Android Gradle Plugin 自带 R8、平台 AndroidKeyStore/javax.crypto、（可选）NDK 工具链。

---

## 9. 实施记录（2026-10-13）

### 9.1 落地清单

**P1 构建期**
- `build.gradle`：release 开启 `minifyEnabled` + `shrinkResources`，接入 `proguard-rules.pro`；新增 `writeIntegrityAnchor` / `printReleaseCertSha256` task（P3 用）。
- `proguard-rules.pro`（新建）+ `proguard-dict.txt`（新建，3000 条非顺序短标识符）：全量 keep 规则覆盖 ActiveAndroid / Gson DTO（`api.local.*`、`model.*`、`OnlineMessages`）/ ButterKnife / vendored 库；`-repackageclasses` + 字典 + `-allowaccessmodification`；v/d/i 日志剥离。
- `AndroidManifest.xml`：`allowBackup="false"`。
- `OnlineConfig.getEndpoint`：release 恒返回 `DEFAULT_ENDPOINT`，忽略存储的 override。

**P2 数据层**（新增 `com.cube.memorygames.security` 包）
- `CryptoVault`：API 23+ Keystore AES-256-GCM + HmacSHA256 双密钥（StrongBox 尽力开启）；API 21/22 RSA 包裹兜底；全部静默降级。
- `SecurePrefs`：AES-GCM 密文（`[ver][12B IV][ct+tag]`）存取 + 明文惰性迁移（`seal`）+ 旧版 boolean 类型兼容读取；解密失败按不存在处理。
- `SecurityMac` + `LocalUser`（新增 `syncTag` 列，DB v16→17，`assets/migrations/17.sql`）：经济字段（money/gamesUnlocked/adsRemoved/vip/unlimitedOnline/unlockedContent/challengeRating/onlineRank）HMAC 防篡改；盖章一次标志（存 SecurePrefs）防"改值后清空 tag"重置攻击；不匹配 → 会话级 taint。
- `OnlineIdentity`：uuid/token/userId/nickname/welcomeGift 全部经 SecurePrefs 加密，明文旧值读时自动迁移。
- `Model.java`（vendored）：新增 `onBeforePersist()` / `onAfterLoad()` 钩子。
- taint 消费：`OnlineSession.chargeEntryFee / refundEntryFeeIfNeeded / applySettlement` 跳过本地钱包镜像（服务端权威）；大厅入场费门槛将污染钱包视为 0。

**P3 运行时**
- `IntegrityAnchor`（生成式源文件，占位未锚定）+ `IntegritySentinel`（签名 SHA-256 vs XOR 拆分锚点、API 28+ SigningInfo 兼容、调试器信号、结果缓存）。
- 消费点（分散、静默、无 kill switch）：`MemoryApplicationModel.onCreate` 预热；大厅排位按钮置灰（`runtimeClear`：签名+调试器）；`OnlineSession.startMatchmaking` 签名门控；大厅点击监听签名门控。

### 9.2 已执行的验证

- `assembleDebug` / `assembleRelease` 双绿（最终交付态，锚点为占位）。
- R8 mapping 抽查：业务类重命名为字典短名（87708 条映射）；security 包整体混淆，`IntegrityAnchor` 被内联进 `IntegritySentinel`。
- 指纹注入链路端到端：task 写入 → 从生成的 Java 数组独立 XOR 重建 = 输入指纹；带锚点构建的 APK 全量扫描（raw 字节 / hex 文本 / 冒号 hex）**零命中**；还原占位后无残留。
- 产物检查：`allowBackup=0` 生效；`assets/migrations/17.sql` 已打包。

### 9.3 待办（移交）

1. **登记指纹锚点**后出正式签名包——未登记则签名哨兵不生效。现成路径：填好 `keystore.properties` 后跑 `build-release.bat`（文首"一键出包"），或按文首两条 gradlew 命令手动执行。
2. **真机回归**（本机无设备）：重点 R8 首开全量回归（在线 PK 双端对局、关卡/难度/挑战/图表/字体），DB v16→17 升级安装 + API 21/22 真机各一台，重签包排位入口置灰确认。回归基线：`docs/online-pk-full-regression-*.md`。
3. `proguard-dict.txt` 建议随大版本重生成（防字典被针对性还原工具沉淀）。

### 9.4 代码审查修正（2026-10-14）

对 P1–P3 全部改动做了逐文件审查，并用产物级手段（dex 字符串池扫描、apkanalyzer 反汇编、gradle task 实测）验证。发现并修复 4 个真实缺陷（前两个在上线包中必然触发）：

1. **[严重] release 泛型签名被剥离 → Gson 运行时必崩**。R8 未保留 `Signature`，`LocalDataManager` 的 `new TypeToken<List<DbLevelInfo>>(){}.getType()` 会抛 `Missing type parameter.`（workout/冲刺/挑战数据加载路径）。修复：`proguard-rules.pro` 增加单行 `-keepattributes Signature,*Annotation*,AnnotationDefault,InnerClasses,EnclosingMethod`，并按 Gson 2.10+ 官方规则补 `-keep class com.google.gson.reflect.TypeToken` 与 `-keep class * extends ...TypeToken`（防改名/类合并）。验证：apkanalyzer 反汇编确认修复后匿名类带完整签名 `Lcom/google/gson/reflect/TypeToken<Ljava/util/List<...DbLevelInfo;>;>;`（dex 按规范分块存储）；修复前同探测为 0。
2. **[严重] SecurePrefs 加密路径与 Keystore IV 语义冲突 → 加密静默失效**。Keystore 密钥（`setRandomizedEncryptionRequired(true)`）拒绝调用方自带 IV，原实现给加密传入显式 IV 会抛异常并被 `catch(Throwable)` 兜底为**明文写入**，整个 §4.2 形同虚设。修复：Keystore 路径改为系统生成 IV（`init(ENCRYPT_MODE, key)` + `getIV()`），软件路径保留显式随机 IV；密文格式升级为 `[ver][ivLen][iv][ct+tag]`；`isBlob` 阈值修正为 40 字符（旧值 44 会把 1 字符值如 `rowStamp` 的 "1" 误判为明文）。
3. **[高] SecurityMac 用默认 provider 操作 Keystore HMAC 密钥 → MAC 静默失效**。Keystore 的 HMAC 密钥不能被默认 JCE provider 使用（`InvalidKeyException`），被 catch 后返回 null → 防篡改标记永不生效。修复：默认 provider 失败时回退 `AndroidKeyStore` provider。
4. **[高] IntegritySentinel 的 debug 豁免可被 manifest 绕过**。豁免原基于运行时 `FLAG_DEBUGGABLE`：重打包者只需在 manifest 里写 `android:debuggable="true"` 并重签名，即可整体关闭签名校验（无需改任何代码）。修复：改用编译期常量 `BuildConfig.DEBUG`（release 恒 false，且被 R8 常量折叠）。

次要修正：`OnlineSession.getLocalCoins()` 在 taint 时返回 0（防止被污染的本地钱包数值进入 register 包）；`SecurityMac.rememberStamped()` 增加内存短路（避免每次存档都写 pref）；`build.gradle` 锚点 task 增加对 `android.injected.signing.*` 的支持并修正日志（原先打印的是指纹的哈希值而非指纹本身）。

审查中核实为**正常/无需改动**的项目：`-repackageclasses ''` 按预期工作（702 个类扁平化到根包，被 keep 规则固定名字的类保持原包；映射证据与 ProGuard 语义一致）；ButterKnife 8.5.1 自带消费规则已覆盖 `_ViewBinding` 反射查找（`-keep public class * implements butterknife.Unbinder`），目标类与绑定类名字完整保留；Calligraphy 的 `Class.forName("android.support.v7.widget.AppCompatTextView")` 探测在改名后会失败，但本 app 字体全部走布局/样式的显式 `fontPath` 属性，不依赖 classStyles 映射与默认字体，**无实际影响**；日志剥离生效（release dex 中 "OnlinePK" 字符串为 0）；`LocalUser.token` 为遗留空列（无写入方）；无 `getIdentifier`/`new Update(`/`Log` 返回值依赖等 shrink/裁剪风险（`shrinkResources` 与 `-assumenosideeffects` 安全）；ActiveAndroid 模型为直接类引用注册，不存在按名加载的隐藏路径；排队入口核查确认 `T_MATCH_JOIN` 仅一个发送方（`startMatchmaking`，已门控），断线重连的 `pendingQueueRejoin` 也走同一门控。

遗留观察（超出本次改动范围，未修改）：`OnlineDebug.freeEconomy/roundMs` 仍以运行时 `FLAG_DEBUGGABLE` 为门控——重打包 + manifest debuggable 可关闭本地入场费/本地镜像门槛（服务端权威余额不受影响，仅影响本地 UX 与本地镜像）。`OnlineConfig.getEndpoint` 的 debug override 门控同理。两者单独修的价值低（此类攻击者已能任意改包），真正的检测路径（签名哨兵）已不再受 manifest debuggable 影响。

### 9.5 真机测试记录（2026-10-14，Huawei BKL-AL20 / Android 9 / API 28）

设备接入后对本次加固的全部关键路径做了真机实测：

1. **加密落盘（debug 包）**：`online_identity.xml` 中 uuid/token/userId/nickname 全部为 `AQ…` 密文 blob（0x01 版本 + 0x0C IV 长度）；`vault_state.xml` 只有 rowStamp 标记、无 s0/s1 包裹密钥 ⇒ 走 API 28 Keystore 主路径（StrongBox 尝试后回退 TEE）；DB `user_version=17`、`User.syncTag` 为 44 字符 Base64（32 字节 HMAC-SHA256，Keystore HMAC 真实工作）。
2. **R8 混淆包功能（真 release 包，非 debuggable）**：冲刺 / 训练 / 挑战三个 Tab 与挑战列表（`TypeToken<List<ChallengeJsonGame>>` Gson 解析）全部正常渲染、实际开局可玩、零崩溃 ⇒ §9.4 审查修复 #1（泛型签名）在真实混淆产物上验证通过。
3. **P2 篡改检测（三条路径全过）**：改金币保留旧标记 → 排位入口被“金币不足”弹窗拦截；清空 `syncTag`（盖章一次标记命中）→ 同样拦截；恢复合法数据（money=60 + 有效标记）→ 正常放行（打开教程页）。
4. **P3 防重打包**：锚点=测试密钥 A 且同密钥签名 → 排位按钮 `enabled=true`、全流程可用；同一 APK 用密钥 B 重签（模拟重打包）→ 大厅连接 / 人机按钮 / 单机全正常，排位按钮 `enabled=false` 且点击完全无反应、零崩溃。降级精确限定在排位入口。
5. **DB v16→v17 升级**：发现并修复了一个 P0 缺陷，见 §9.6。

测试夹具说明：为在非 root 设备上用 `run-as` 构造篡改场景，测试包临时加了 `debuggable true` 并把 `OnlineDebug.freeEconomy` 临时硬编码为 false（否则 debuggable 会打开免费经济），测后全部还原并重建。注意陷阱：**`BuildConfig.DEBUG` 跟随 `debuggable` 属性而非变体名**，debuggable 的 release 测试包里它是 true——首轮篡改实验因此被免费经济分支跳过，属夹具问题而非产品缺陷。

### 9.6 真机测试发现的 P0 缺陷（已修复）：vendor 迁移引擎是反编译存根

**现象**：模拟 v16→v17 升级（设备 DB 的 user_version 置 16、删掉 syncTag 列后启动）时，应用既不崩溃也不迁移：版本保持 16、列未加回、WAL 零写入。

**根因**：`com.activeandroid.DatabaseHelper.executeSqlScript` 是 JADX 反编译失败留下的存根，方法体只有 `throw new UnsupportedOperationException("Method not decompiled")`。升级链路 `onUpgrade → executeMigrations → executeSqlScript` 抛出的异常被 `executeMigrations` 的 `catch (Throwable)` 吞掉，内层事务标记失败使外层事务（含 `setVersion`）被静默回滚——**任何迁移都永远不会生效**。

**影响**：本方案的 DB v17（`User.syncTag` 新列）依赖该迁移。修复前，所有已有 v16 库的老用户升级安装后：列不会被添加，**第一次存档（金币结算等任意 `LocalUser.save()`）会以 `no such column: syncTag` 崩溃**。

**修复**：按存根内保留的字节码注释忠实还原原实现——打开 `assets/migrations/<file>`，按 `mSqlParser` 分派到已完好反编译的 `executeDelimitedSqlScript` / `executeLegacySqlScript`；`17.sql` 精简为单条 ALTER（避免 legacy 解析器逐行执行的注释行怪癖）。修复后真机复测：**user_version 升到 17、syncTag 列已添加、money 数据无损（哨兵值 44444 保留）、WAL 有写入、零崩溃**；`pm clear` 新装路径不受影响（v17 + 列 + 标记）。

**为何此前从未暴露**：该存根自代码库恢复以来就在，但 v17 之前没有任何真正需要执行的迁移 SQL，所以从未被触发；本次加固第一次依赖迁移能力，真机测试把它挖了出来（静态审查时按代码逻辑推断“迁移会执行”，而实际执行引擎是死的——凡涉及恢复代码库中 JADX 存根的关键路径，都应安排一次真机验证）。

### 9.7 本轮机测未覆盖、建议后续补测

- 完整在线 PK 回归（`tools/pk-test-1010/regression.py`：需管理 token + 服务端热配置 + WS 探针）：已验证大厅连接/注册/排位门槛行为，双端对局全流程未跑。
- API 21/22 真机的 RSA 包裹兜底路径（本机为 API 28，走的 Keystore 主路径）。
- 与本次改动无关的旧功能面回归（多语言、提醒、图表等）。
