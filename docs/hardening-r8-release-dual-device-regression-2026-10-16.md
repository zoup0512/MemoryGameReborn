# 加固 R8 签名包双机全量回归（2026-10-16/17 夜间）

对象：`main` @ `c42d7ba` 的**签名 R8 release 包**在两台真机上的全量回归 + 生产云服务同步部署。
即加固设计 `docs/android-client-hardening-design.md` §9.3 待办 #2「R8 首开全量回归（在线 PK 双端对局、关卡/难度/挑战/图表/字体）」中**双端对局未跑**部分的补齐（§9.7 明列）。

## 结论

**自动化与产物层全绿；双机在线对局全流程通过（排位 / 亲友赛 / 练习场三模式），零崩溃。** 本轮同时把 2026-10-16 的分模式战绩服务端（`fc38937`）与「亲友赛」改名（`c42d7ba`）同步到线上云服务，并在真机上验证 `modeStats` 全链路生效（含三模式独立记账）。
回归中发现 1 个**构建脚本缺陷**（`build-release.bat` 拒绝 `-P` 参数，与自身文档不符）、1 个**产品观察**（接受超时后无限自动重排），以及若干**测试工具/文档层面**的适配问题与建议（非产品缺陷，见第四节）。

- Android 单元测试 **59/59**（8 套件）；服务端 **138/138**（23 套件，0 failures / 0 errors / 0 skipped）。
- 签名 APK 产物级校验通过：v1+v2 签名、证书指纹与 `keystore.properties` 记录一致、生产端点已固化、**锚点指纹 4 种形态在全球面 0 命中**（未以字面量泄漏）。
- 线上云服务升级到当前 main：Flyway 校验通过（无新迁移）、旧数据无损（同 installationId 复登返回同一 userId）、`auth_ok` 与 REST 均携带 `RANKED/PRACTICE/FRIEND` 分模式统计。
- 双机（CDY-AN90 + BKL-AL20）真机：R8 包冷启动、离线玩法（含真实游戏开局）、在线三模式对局全流程、三模式独立记账、弃赛/断网重连边界全部通过。整轮共 4 场真实对局（排位平局、亲友平局、练习负、排位弃赛），服务端与双机 UI 全部对账一致：ELO 零和（−16/+16）、金币收支逐笔吻合、分模式战绩逐场落位正确，**零崩溃**。

## 环境与版本

| 项 | 值 |
|---|---|
| 客户端源码 | `main` @ `c42d7ba`（工作区仅新增 2 个测试脚本，未提交） |
| 客户端产物 | `android_memory-game-reborn/release/memory-game-3.6.3-74-release-20261016-2318.apk`，sha256 `02275b13e9e6f2ed1b5c8938edef82fd4488f171071e8aeac8b72bdc7d83a6df`，8.70 MB（debug 14.72 MB） |
| 签名证书 | SHA-256 `f04258e220395e8ad239603466ed18c3f71150b6879b649b21a9ed8a69a31ed7`（`L=Hang Zhou, CN=NS`，与 `keystore.properties` 备注一致） |
| 生产端点 | `wss://www.razor123.site/ws`（构建期固化，`classes.dex` 内 2 处命中） |
| 服务端产物 | `memorygames-pk-server-0.1.0.jar`，sha256 `7945d33611aea70b0a7f4b8f29e1490b6077815a5196dff1fe2e5eb7fee2e4bf` |
| 服务端运行 | 腾讯云 `124.220.35.87`，systemd `memorygames-pk.service`（:8091，H2 文件库，Java 21） |
| 设备 1 | CDY-AN90（25HUT20515012964），Android 10 / API 29，1080×2400，zh-Hans-CN |
| 设备 2 | BKL-AL20（P7CDU17B22004602），Android 9 / API 28，1080×2160，zh-Hans-CN |
| 双机账号 | D1 `彩虹水獭`（userId 51）、D2 `星河飞鱼`（userId 52）；均 1000 ELO / 欢迎金币 60 / 排位青铜 |
| 服务端时序 | 生产默认（5 回合/局；回合 30–80s；换轮 6s（练习场 2s）；接受窗口 30s；重连窗口 90s）；**未**做热配置、**未**保留管理 token |

## 一、构建与产物层

1. **Android 单元测试**：`testDebugUnitTest` → **59 项全过**（8 套件）。需 `JAVA_HOME=C:\Program Files\Java\jdk-17`（文档里的 `F:/jdk17` 已不存在）。
2. **服务端全量测试 + 打包**：`gradlew test bootJar` → **138 项全过**（23 套件，0 failures / 0 errors / 0 skipped），耗时约 10 分钟。注意：本机 `--offline` 无法解析 Spring Boot 插件（`org.springframework.boot:3.3.4`）而直接失败，需联网跑——属本机缓存限制，不是测试失败。
3. **工作区状态**：仅新增未跟踪脚本（`tools/pk-test-1010/dual.py`、`dual_round.py`）；`IntegrityAnchor.java` 出包后已还原为占位（`ANCHORED=false`）。
4. **APK 产物级校验**（`build/test-round-20261016/verify_apk.py`，扫描**解压后**的全部条目；直接扫 APK 文件字节会因 deflate 压缩得到假阴性）：
   - `apksigner verify`：v1(JAR) + v2 签名有效；证书 SHA-256 与 `keystore.properties` 记录一致。
   - 端点固化：`www.razor123.site` 在 `classes.dex` 出现 2 次 → release 包按设计指向生产 WSS。
   - **锚点卫生**：证书指纹的原始字节 / 小写 hex / 大写 hex / 冒号 hex 四种形态 **0 命中** → XOR 拆分锚点未被 R8 折叠为字面量。
   - 清单与资源：`allowBackup=false`、`usesCleartextTraffic=true`（遗留兼容）、`assets/migrations/17.sql` 已打包、单一 `classes.dex`。
   - R8 映射归档：`build/outputs/mapping/release/{mapping,seeds,usage,resources,configuration}.txt`。

## 二、云端部署（分模式战绩服务端同步）

- **目标**：既有 `124.220.35.87` / `memorygames-pk.service` / `/root/memorygames-pk/app.jar` / :8091。未改 nginx、H2 数据目录、systemd drop-in 内容、管理面配置。
- **停服前检查**：`pk_rooms_active=0`，最近一场 21:59 已结算，无进行中对局。
- **停服**：SIGTERM 优雅关停 —— `Graceful shutdown complete`、`match manager shut down; 0 live rooms were voided`、HikariPool 关闭完成（H2 干净落盘）；systemd 记录 exit 143（SIGTERM 的正常记账，会显示 `failed` 状态但无副作用）。
- **备份**：`/root/memorygames-pk/backup-pre-modestats-20261016-232350/`（旧 `app.jar` sha256 `bd34c1c3…` + `data/` 目录），停服后复制。
- **上传与校验**：新 jar 本地 = SFTP 上传后 = 线上安装位三处 sha256 一致（`7945d336…`）；另存归档 `app.modestats-7945d336.jar`。
- **启动与线上验证**：`Started PkServerApplication in 8.055 seconds`；Flyway `Successfully validated 8 migrations`、`Schema "PUBLIC" is up to date. No migration necessary`（V1–V8 与部署前一致 → 无 schema 变更）；`/actuator/health` UP、readiness 200、`https://www.razor123.site/ws` 升级 101。
- **分模式统计生效**：探针 guest 的 `auth_ok` 与 REST `UserView` 均新增 `modeStats`（三模式齐全）；同一 `installationId` 重新注册返回**同一 userId=50** → 升级未丢数据。
- 部署后另有两次**计划内重启**：为做服务端身份核对临时启用管理 token（drop-in `admin-token.conf`），核对完成后**已删除 drop-in 并再次重启**，`/api/admin/users` 恢复 404。三次重启期间所有在线客户端均自动重连（23:50:54 三路 `AUTH_OK` 全部恢复）。
- **回滚**（如需）：

  ```bash
  ssh root@124.220.35.87
  systemctl stop memorygames-pk.service
  cp -a /root/memorygames-pk/backup-pre-modestats-20261016-232350/app.jar /root/memorygames-pk/app.jar
  systemctl start memorygames-pk.service
  ```

  本轮无数据库迁移，仅回退 jar 即可；部署后已接受新写入，禁止用备份覆盖 `data/`。

## 三、双机真机回归（签名 R8 release 包）

安装方式：两机先卸载 debug 包（签名不同无法覆盖升级），安装 release 包（`flags` 无 `DEBUGGABLE`）。此前 2026-10-14 已在 BKL-AL20 验证过 DB v16→v17 升级安装，本轮为新装路径。

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| 1 | 安装与冷启动、在线页三标签 | ✅ | 主菜单渲染（记忆网格/六边形/等级）；在线页 `排位赛/练习场/亲友赛` + 分模式战绩 `0 / 0 / 0 / 0%`（旧服务端此处应为“—”+暂不可用提示） |
| 2 | 离线玩法（R8 反混淆面，双机） | ✅ | 冲刺 tiles → 打开「记忆网格」显示 `记住格子的位置 / 下一步`（真实游戏 Activity 加载）；挑战列表 `第 1 关 0 / 9`；我的（资料/奖杯/排名/设置）；零崩溃 |
| 3 | 真人排位配对 | ✅ | 双机同时排队 → 服务端 `PAIRED a=51 b=52`；双方卡片互见昵称（`彩虹水獭`↔`星河飞鱼`）。另观察到：接受卡停留超过 30s → `MATCH_NOT_ACCEPTED reason=accept_timeout`（未扣费）→ **双端自动重排并再次配对**（见发现 5） |
| 4 | 二级 START 门 | ✅ | D1 先按 → `正在等待\n你的对手`；D2 按后服务端 `MATCH_START accepted=true` ×2，对局立即开始 |
| 5 | 同种子同题 + HUD | ✅ | 第 1 轮两机同屏 `和之前一样吗？ / 第 2 关`；计划第 4 轮（game 0）两机同屏 `记忆网格 / 第 1 关`；HUD 双侧 `OPP 0 : 0 ME` 镜像；练习局机器人得分后 HUD 出现非零同步 `OPP 122 : 0 ME` |
| 6 | 排位结算（闲置 0:0 平局） | ✅ | 双机卡片对称：`对局结束 / 平局！ / 金币 +15 → 45 / ELO +0 / 1000`；服务端 `SETTLED mode=RANKED forfeit=false scores=0/0 round=5` |
| 7 | 回合结果列表 | ✅ | 双机同序 5 行：`和之前一样吗？ / 正确吗？ / 颜色 / 记忆网格 / 数一数`，与 `MATCH_CREATED` 计划 gameId `15/10/19/0/4` 一一对应 |
| 8 | 分模式记账 | ✅ | 两机：排位 `0胜0负1平` + 亲友 `0胜0负1平`；D1 另有 练习 `0胜1负0平`。与服务端 admin 视图（user51/52 w/l/d=0/0/1）一致，无跨模式串记 |
| 9 | 亲友赛建房/输码/开局/结算 | ✅ | D1 建房得房码 `4541` → D2 输码加入 → 配对 `mode=FRIEND` → 5 回合 → 结算 `好友` 标签、`金币 +15 → 30`；服务端 `SETTLED mode=FRIEND scores=0/0 round=5` |
| 10 | 练习场（人机） | ✅ | 难度选择 `青铜 800 ELO·胜利+8 / 白银 1000 / 黄金 1250`；对局 5 回合（机器人得分 350）；退出对话框 `退出将按认输结束本次练习，不会扣除金币`；结算 `你输了 / 练习 / 金币 +0 / ELO +0`；服务端 `mode=PRACTICE scores=0/350` |
| 11 | 排位入口完整性门控（真实证书） | ✅（正向） | 真实证书签名包：排位按钮可点、可排队、可开局、可结算；负向「换密钥重签 → 置灰」见 §9.5 已验证，本轮未重复 |
| 12 | 对局中断网重连 + 弃赛镜像 | ✅（见备注） | 断网 12–18 s → D1 自动重连并继续对局（双方随后同屏进入下一轮同一游戏 `和之前一样吗？ 第 2 关`）；随后 D1 退出对战 → 服务端 `RESIGN user=51 round=4` → `SETTLED mode=RANKED forfeit=true reason=resign`；双机卡片 `你输了 −16 ELO → 984 / 金币 0` ↔ `恭喜！你赢了！ +16 ELO → 1016 / 金币 50`（**零和**）。备注：对手侧的「重新连接」提示在采样时刻未捕获（当时同屏显示 `本局结束 / 你的得分: 0 / 游戏 1/5`），已在采样窗口内确认卡片为回合结束卡而非重连卡 |
| 13 | 模式标签页点击 + 左右滑动 | ✅ | 点击三标签逐页显示各自战绩；从亲友赛向右滑动正确回到练习场（`免费练习 · 不影响 ELO` 卡片）；离开在线页再返回后选择保留 |

## 四、发现

1. **`build-release.bat` 拒绝 `-P` 参数（构建脚本缺陷）**。脚本头部写明 `build-release.bat [--no-pause] [--keep-anchor] [extra gradle -P args]`，但参数解析把任何以 `-` 开头的参数判为 `unknown option` 并以 exit 2 退出 → 文档要求的生产端点构建 `-PonlineEndpoint=wss://…` **无法经该脚本传入**。本轮改用等价步骤出包：`gradlew writeIntegrityAnchor -PonlineEndpoint=…` → `gradlew assembleRelease -PonlineEndpoint=…` → `gradlew printReleaseCertSha256` → `git checkout -- IntegrityAnchor.java`。建议放行 `-P` 前缀参数或改写文档为「直接调用 gradlew 两步」。
2. **UI Automator 坐标空间与物理分辨率不一致（工具适配，非缺陷）**。CDY-AN90 dump 空间 `720×1448`、物理 `1080×2400`；BKL-AL20 dump `1080×2040`、物理 `1080×2160`。实测 `input tap` 直接接受 **dump 空间坐标**（用 (504,1432) 命中第 4 个底部 Tab）——不要按物理像素换算，否则点击全部落空。
3. **并发 uiautomator dump 会打崩 dump 进程自身（工具适配）**。两进程同时对同一设备 dump 时，`DumpCommand` 注册自动化服务失败（RemoteException）并返回空 dump；易被误读为“应用空白/崩溃”（排查确认对应时刻应用 Activity 正常）。设备脚本必须单进程串行 dump。
4. **二级 START 的「对手已就绪」提示窗口 < 4 s**。对手按下后 4 s 内已进入对局（3 s 开局倒计时 + 准备列表），首次采样已在对局内，故未采到 ready 文案；对局开始的权威证据用服务端 `MATCH_START accepted=true` ×2。
   **同一类采样窗口问题的第二例（测试工具标签缺口，非缺陷）**：客户端实际文案为回合结束卡 `本局结束 / 你的得分: N / 游戏 x/5`、对局内退出对话框 `退出本局对战？ / 退出将按认输结束本局。若为排位对战，将失去本局入场押注。 / 退出对战 / 继续对战`，均不在本次脚本预置的标签表内（脚本原按 `正在确认本轮成绩… / 下一轮：X / N 秒后进入下一轮准备…` 匹配），导致逐轮卡片与对局内退出对话框多次漏检。三处文案均已核对为产品现有实现。
5. **接受超时后的无限自动重排（产品观察，非缺陷）**。双机停留在接受卡超过生产 `accept-timeout=30s` 时，服务端以 `MATCH_NOT_ACCEPTED reason=accept_timeout` 取消（**未收费用**），客户端自动重新排队并再次配对（`PAIRED waited=6ms`）；实测连续两轮超时后第三次成功。行为与 PRD「Ranked 自动重排」一致，但挂机玩家会被无限重排，建议产品确认是否需要提示/上限。
6. **测试方法陷阱：`.accounttest` 克隆包的任务栈会顶上来**。D2 上 `.accounttest` 包（上午安装）仍在后台任务栈中；两次 BACK 弹掉主包任务后，其任务（同样渲染在线页/我的）成为前台，导致后续读数全部来自**另一个账号**（user 49 `星河小鹿`，其练习场 1 胜记录）。本次通过与服务端 admin 视图交叉核对（user 49/51/52 记录各自一致）排除产品缺陷，确认是测试读数错包。工具已加**前台包校验**（`ensure_main_app`：断言 `mResumedActivity` 属于主包）后再操作任何 UI。**该现象提醒：凡「同一设备装有多个包名克隆」的真机回归，必须先断言前台包名。**
7. **文档路径失效**：`docs/*.md` 中的 `JAVA_HOME=F:/jdk17`、adb `E:/AndroidSdk` 在本机均不存在（实际为 `C:\Program Files\Java\jdk-17`、`C:\Users\24790\AppData\Local\Android\Sdk`）；建议统一改为环境变量/占位符写法。
8. **`--offline` 服务端构建失败**：`org.springframework.boot:3.3.4` 插件在市场缓存缺失时无法离线解析；文档中的离线构建示例在本机不可复现（需联网一次预热）。

## 五、证据目录

- 产物与工具：`android_memory-game-reborn/build/test-round-20261016/`（`verify_apk.py`、`profile_probe.py`）
- 真机证据（截图 + UI XML，按 flow 分目录）：`android_memory-game-reborn/build/test-round-20261016/evidence/`
- 服务端事件：`journalctl -u memorygames-pk.service`（`PAIRED` / `MATCH_CREATED`（含 plan 与种子）/ `MATCH_START` / `SETTLED`（含 mode/forfeit/比分）/ `MATCH_NOT_ACCEPTED`）
- 测试脚本（未跟踪，可复用）：`android_memory-game-reborn/tools/pk-test-1010/dual.py`（双设备原语 + 前台包校验）、`dual_round.py`（双机流程：ranked/friend/practice/edge/tabs/stats/offline）

## 六、未覆盖 / 限制

- **API 21/22 真机**：本轮两机为 API 28/29；P2 的 RSA 包裹兜底路径仍未在 API 21/22 验证（§9.7 遗留）。
- **DB v16→v17 升级安装**：本轮为全新安装；升级路径已在 2026-10-14（§9.5/§9.6）于 BKL-AL20 验证。
- **负向完整性验证**：本轮仅验证真实证书签名包**正向**可用；「换密钥重签 → 排位置灰」在 §9.5 用测试密钥已验证，未重复。
- **生产邮件/昵称审核**：线上未配置 SMTP 与昵称审核服务；账号注册邮件流与自定义昵称不在本轮范围。
- **Docker/PostgreSQL `pgTest`**：未跑；H2 集成测试不能替代 PostgreSQL 并发验证。
- **回合间过场卡文案**：客户端换轮卡片（`本局结束 / 你的得分 / 游戏 x/5`）在断网采样窗口内捕获到一次；带倒计时的换轮文案（`正在确认本轮成绩… / 下一轮：X / N 秒后进入下一轮准备…`）未在采样窗口内捕获（卡片窗口短、dump 慢）。换轮时序与倒计时已在 2026-10-16 用 `tools/intermission_probe.py` 对线上验证过，本轮未重复。
- 本轮未提交、未推送；新增测试脚本与本文档均为未跟踪/未提交状态。
