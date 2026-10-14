# 游客与邮箱账号实施验收报告

日期：2026-10-13。基线分支 `dev/20261013`，实施前 HEAD `2524d2b`。实现保留在工作区，未 commit/push，未部署生产服务。

## 结论

第一期核心账号闭环已实现并通过服务端回归、本地真实 HTTP/SMTP/WebSocket、旧数据库升级及隔离 Android 真机验收。游客与邮箱用户均使用现有永久 userId；绑定邮箱原地升级，不相加或搬迁在线资产。

正常 debug 与 release 构建通过；release 没有注入外部签名，产物是 unsigned APK，不能当作已签名发行包。生产域名、TLS、SMTP 与 PostgreSQL 实测仍是上线条件，不能把本地测试通过理解为云端账号服务已经上线。

## 实施内容

### 服务端

- 增量 Flyway V4 账号/身份/设备/会话/验证码/审计，V5 昵称修改冷却。
- 安全游客创建与凭 secret 恢复、持有效旧 token 的游客迁移。
- 邮箱验证码注册，原 userId 升级；已迁移/升级账号禁止旧 UUID 恢复。
- BCrypt 密码、access/refresh 哈希存储、15 分钟 access、30 天 refresh、轮换与重放检测。
- 五设备会话上限、当前/全部退出、设备撤销、找回密码。
- 七天注销冷静期、取消申请、到期定时匿名化。
- 昵称首次立即修改，之后七天冷却；十二种内置头像编号。
- PK 生命周期原子认证门，防止对局与切号并发；同账号仅一个 PK 控制连接。
- WS 按 session 检查撤销，access 刷新/到期不误伤已建立的合法连接；旧连接真正通知并关闭。
- HTTP 默认拒绝、请求体实际 16 KiB 上限、限流、不信任任意 forwarding headers。
- 排行榜 userId、历史 matchId/opponentUserId，删除用户不进入排行榜。

### Android

- 账号中心：游客提示、绑定、登录、昵称/头像、设备、退出、密码重置、注销/撤销申请。
- Keystore RSA 包装 AES-256/GCM 身份存储，凭证备份排除。
- 后台 REST、超时、不跟随带凭证重定向、来源隔离、请求 epoch 校验。
- 401 刷新一次，撤销/失效保留原资料并要求显式重新登录或本机失效身份移除，不静默新建替代账号。
- 旧游客迁移成功安全落盘后才移除旧凭证；迁移目标必须明确授权。
- 在线余额准入与结算只更新服务端快照，不再写单机 LocalUser.money。
- 排队/好友房/练习启动/对局中禁止切换，切号清在线缓存但不改离线数据库。
- 密码显式遮罩，不写 savedInstanceState；设备时间使用可读本机日期。
- debug 专用 `.accounttest` 隔离包和账号页调试导出；release 账号页非 exported。

## 服务端回归

命令：`JAVA_HOME=F:/jdk17 ./gradlew -p server_memory-game-reborn test bootJar`。

最终结果：12 个测试套件，39 个测试，0 failures、0 errors、0 skipped。新增账号测试 11 项，覆盖：

1. 游客 secret 所有权、同游客恢复、hash-only 存储与欢迎礼只发一次。
2. 旧 token 迁移、余额保留、旧 token/UUID 恢复禁止。
3. 邮箱升级保留 userId/ledger/history，错误验证码不升级、重复邮箱拒绝。
4. 昵称冷却、头像修改、已撤销 Principal 不能继续修改。
5. 并发 refresh 只有一个成功，重放撤销 family。
6. 验证码失败次数持久化，不能通过事务回滚重置尝试数。
7. WS 刷新后继续有效，access 到期不误伤连接，重放后撤销。
8. 多用户删除不触发 token 唯一约束冲突，删除用户过滤榜单。
9. 设备数量、对局中登录阻止、注销取消与到期匿名化。
10. 失败迁移/资料修改回滚、密码重置撤销会话。
11. 游客与邮箱用户同局游玩；旧 socket 关闭不清除新 socket 控制者。

原有对局、Bot、退款恢复、热配置、管理限流与关闭回归同时通过。

`pgTest` 未执行：本机无 Docker。H2 通过不等于 PostgreSQL Testcontainers 通过。

## 真实 REST / SMTP / WebSocket

脚本：`docs/tools/account_smoke.py`。

最终 jar 在独立 loopback 服务执行真实邮件投递（内存 SMTP 收件器，不返回或输出验证码），通过：

- 游客创建与错误 secret 拒绝。
- 验证码投递、错误验证码不升级、正确验证码原地升级。
- userId 与余额不变，升级后游客 secret 失效。
- WS auth_ok 回显当前 access，而不是旧永久 token；游客/正式用户可请求线上资料。
- 错误密码拒绝、正确登录。
- refresh 轮换，旧 refresh 重放导致新 access 也失效。
- 昵称、头像、设备列表。
- logout-all 全会话撤销。
- 密码重置、申请删除、取消删除保留 userId/余额。
- 单设备退出后 REST 失效，WS 收到会话结束通知并关闭。

另启动未开启 HTTP 测试开关的服务，HTTP 和伪造 `X-Forwarded-Proto: https` 请求均返回 `403 HTTPS_REQUIRED`；17,000 字节请求体被拒绝。

## 旧数据库迁移

`LegacyAccountFixture.java` 创建隔离 V1～V3 H2 文件库，预置：

- userId 91001。
- balance 321，ELO 1450。
- 胜 7、负 3、平 1，risk suspicion 2，practiceStreak 4。
- 一条历史战绩和一条金币流水。

使用新服务真实启动增量迁移并执行 `legacy_account_smoke.py`。验证旧 userId、资产、统计、matchId 与 opponentUserId 保留；旧 token 401，UUID-only 恢复拒绝；响应丢失后的同 installation+secret 恢复保持同 userId。

停止隔离服务后以 JDBC 再读数据库，确认历史仍为一条、流水仍为一条，总额321，无重复欢迎礼。最终 V5 jar 再启动成功，Hibernate validate/健康检查通过，再次读取核心数据未变化。

## Android 真机

设备：Lenovo TB-Q706F，Android 13 / API33，中文环境。仅安装独立 `com.memory.brain.training.games.accounttest`，未覆盖原游戏，未清除原应用数据。

Android MCP 由于 adb 路径尾部 CR 导致 ENOENT，安装、UI Automator 与截图使用已验证的 ADB fallback；没有修改 SDK、接受 license 或变更系统配置。

最终 `account_ui_smoke.py` 七项检查通过：

1. GUI 邮件验证并注册，游客 userId 不变。
2. Android HttpURLConnection PATCH 昵称成功。
3. 设备会话页正常显示。
4. 错误登录不丢身份，表单可继续操作。
5. 同一表单正确密码重试成功。
6. 退出先撤销，再显式创建不同 userId 的游客。
7. 进程 force-stop/restart 后 Keystore 身份仍为同一游客。

额外验收发现并修复：

- 已失效 access 的 `/me` 401未引导登录：现在刷新一次，失败持久 requiresLogin并展示原 profile及“仅移除本机已失效身份”。实际点击恢复成功。
- 恢复代码中 inputType 单独设置不保证密码遮罩：显式 PasswordTransformationMethod 后截图确认密码为圆点。
- 设备活跃时间原为毫秒：修改为本机日期时间，截图确认。

截图与结果在 `android_memory-game-reborn/build/account-ui-evidence/`（build 产物不进源码）。最终截图包括游客、正式账号、资料、设备、密码遮罩的登录错误、登录成功、退出与重启。检查加密 SharedPreferences，仅有 encrypted identity blob，没有 plaintext accessToken 字段。

API21 的编译兼容已保留，但未在 API21 实机/模拟器执行 Keystore 测试；不能将 API33 结果外推为所有版本的实测。

## 产物

正常配置构建：`assembleDebug assembleRelease`，BUILD SUCCESSFUL。

- debug：`android_memory-game-reborn/build/outputs/apk/debug/memory-game-3.6.3-74-debug-20261013-2222.apk`。
- release unsigned：`android_memory-game-reborn/build/outputs/apk/release/memory-game-3.6.3-74-release-20261013-2222-unsigned.apk`。
- 服务端：`server_memory-game-reborn/build/libs/memorygames-pk-server-0.1.0.jar`。

默认配置恢复为现有生产游客地址，没有把本地测试 endpoint 编进正常产物。旧 `release/` 文件未删除或覆盖。

## 未上线及未覆盖的边界

- 未部署腾讯云，未修改生产数据库、服务、代理或证书。
- 新账号正式使用需要真实 HTTPS/WSS 与 SMTP；默认明文游客地址不能发送账号密码。
- 第三方 OAuth、多提供者绑定、跨设备单机云进度、好友图谱、在线账号资产合并、显式对局接管、多实例实时共享不属于本次第一期。
- 当前 identity 是 EMAIL/GUEST 每用户一行，不是已经完成的多 OAuth provider 表。
- 既有 secure identity 禁止直接变更来源；debug 环境切换保守限制，需独立测试包，不能为了方便调试泄漏凭证。
- 未重新进行23款单机游戏全量人工试玩；没有修改 ActiveAndroid 数据模型或离线货币写入逻辑。
- 生产 TLS/SMTP投递、PostgreSQL Docker测试和 API21 Keystore 实测仍需对应环境验收。

部署步骤与参数见 [账号部署与验收](account-system-deployment.md)。
