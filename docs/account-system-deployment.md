# 账号服务部署与验收

此文档描述上线条件与可复现验收，不代表已将账号服务部署到云端。现有生产 `ws://124.220.35.87:8091/ws` 仍是明文旧游客地址。不能在该地址发送邮箱密码或新账号凭证。

## 1. 上线前提

- 已备份生产数据库，并在备份副本执行最新 Flyway migration。
- 生产启用持久化 PostgreSQL；默认内存 H2 仅用于测试。
- 有用户可访问的 HTTPS/WSS 域名和有效证书。
- 配置真实 SMTP 投递及发件地址，建议验证 SPF/DKIM/DMARC。
- 服务端 API 默认拒绝明文 HTTP。`PK_AUTH_ALLOW_HTTP=true` 仅用于隔离的本地验收，不能在公网生产开启。
- 不记录 Authorization、refresh token、guestSecret、验证码和密码；反代也不能把这些数据写访问日志。
- 已配置服务端与 SMTP 连接、读写超时。
- 若开放自定义昵称，已按 [昵称机器审核](nickname-moderation.md) 确认同机敏感词服务（默认 `sensitive-word` 模式）可用，完成命中拒绝、超时、服务不可用及异常返回验收；使用腾讯模式时另验需复核与鉴权失败。服务不可用或 `disabled` 时自定义改名必须明确失败；禁止上线临时“全部通过”审核实现。
- 接入第三方审核前完成隐私政策及供应商数据处理评估；只提交必要昵称文本，不提交账号邮箱、令牌或设备秘密。

## 2. TLS

可选择 Spring Boot 直接 TLS，或 Nginx/Caddy 等可信反向代理。不要跳过证书验证，不要在 Android 客户端加信任所有证书代码。

直接 TLS 示例（密码来自环境变量，不写入仓库）：

```properties
server.ssl.enabled=true
server.ssl.key-store=file:/etc/memorygames/tls/server.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=${PK_TLS_KEYSTORE_PASSWORD}
```

反代方案必须满足：

1. 后端监听 loopback 或防火墙限制的私网端口，禁止直接公网访问后端。
2. 80 端口只重定向到 HTTPS，不代理账号 API 或 WebSocket。
3. 443 对 `/api/v2/` 和 `/ws` 代理。
4. 只信任实际代理的 forwarding headers，不开启信任任意客户端 `X-Forwarded-Proto` 的策略。
5. 使用 Tomcat native 可信代理配置或直接 TLS，让 `request.isSecure()` 返回真实的安全状态。账号 guard 不自行解析客户端提供的转发头。
6. 检查外网 HTTP 账号请求被拒绝或重定向，无法用伪造转发头绕过；正确 HTTPS 请求可进入服务。
7. 对 WebSocket 设置 Upgrade/Connection、合适的 idle/read timeout。

没有真实域名、证书和可信代理配置时，不应发布启用正式账号登录的 APK。

## 3. 邮件配置

使用 Spring Boot 标准 SMTP 配置：

```properties
spring.mail.host=${PK_SMTP_HOST}
spring.mail.port=${PK_SMTP_PORT:587}
spring.mail.username=${PK_SMTP_USERNAME}
spring.mail.password=${PK_SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
pk.auth.mail-from=${PK_AUTH_MAIL_FROM}
```

环境变量也可直接使用 `SPRING_MAIL_HOST`、`SPRING_MAIL_PORT`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PASSWORD`，以及 `PK_AUTH_MAIL_FROM`。

无邮件服务时发送验证码应返回 `MAIL_UNAVAILABLE`，不得将验证码直接返回客户端。找回密码响应不能泄漏邮箱是否已注册。

## 4. 数据库迁移

- 使用增量 Flyway V4+；不能修改已部署 V1～V3。
- 旧 `pk_user.id` 不变，默认 `GUEST`。
- 迁移 token、绑定邮箱均不能再发欢迎礼。
- 验证 `pk_match_record`、`pk_match_instance` 和 `pk_coin_ledger` 引用没有变化。
- 不删除旧身份字段来强行让 Hibernate validate 通过。
- 服务启动执行到期删除与旧活动对局退款，验收备份副本不能连接生产客户端。
- 若升级后需要回滚 APK，已经迁移的账号不能再允许旧 UUID 恢复。不能回滚到忽略 `legacy_auth_disabled` 的旧服务端来恢复旧认证，否则会重新开放账号接管漏洞。

## 5. Android 构建

TLS 配置完成后指定实际地址：

```bash
JAVA_HOME=F:/jdk17 ./gradlew -p android_memory-game-reborn assembleDebug \
  -PonlineEndpoint=wss://your-real-domain.example/ws
```

`your-real-domain.example` 是占位符，不是已部署地址。正式 release 构建还需要仓库外签名配置；不能将 unsigned 包称为可发行签名包。

默认旧游客地址保持兼容。客户端正式账号 API 不允许明文 HTTP；debug 可显式使用本地开发地址，并配合服务端开发 HTTP 开关。切换服务端时不得把前一个来源的凭证发送到新来源。

用于验收的独立测试包可以使用 `-PaccountUiTest=true`，包名增加 `.accounttest`，provider 使用对应 applicationId，避免覆盖设备上原游戏与单机数据库。该开关仅用于 debug，不能发布给正常用户。

## 6. 自动化验收

### 服务端回归

```bash
JAVA_HOME=F:/jdk17 ./gradlew -p server_memory-game-reborn test bootJar
```

PostgreSQL Testcontainers：

```bash
JAVA_HOME=F:/jdk17 ./gradlew -p server_memory-game-reborn pgTest
```

`pgTest` 需要 Docker，本机未提供 Docker 时需在 CI 验证，不能将 H2 测试结果当成 PostgreSQL 实测。

### 真实 HTTP 与邮件流程

启动隔离的本地服务，将 SMTP 指向验收脚本的内存收件器：

```bash
F:/jdk17/bin/java -jar server_memory-game-reborn/build/libs/<boot-jar>.jar \
  --server.address=127.0.0.1 --server.port=18081 \
  --pk.auth.allow-http=true --pk.nickname.mode=disabled \
  --spring.mail.host=127.0.0.1 --spring.mail.port=2526 \
  --pk.auth.mail-from=no-reply@example.com \
  --spring.mail.properties.mail.smtp.connectiontimeout=5000 \
  --spring.mail.properties.mail.smtp.timeout=5000 \
  --spring.mail.properties.mail.smtp.writetimeout=5000 \
  --management.health.mail.enabled=false
```

在另一个终端执行：

```bash
# 需要 Python 3 与 websocket-client（现有 PK 工具通常已使用它）。
# 若系统配置 HTTP_PROXY，请设置 NO_PROXY=127.0.0.1,localhost,::1。
python docs/tools/account_smoke.py --base-url http://127.0.0.1:18081
# 在未配置外部审核的隔离服务上，专门验证昵称与旧 WS 防绕过：
python docs/tools/nickname_smoke.py --base-url http://127.0.0.1:18081
```

此脚本仅允许 loopback，启动临时 SMTP 接收验证码，真实验证：

- 游客创建与错误恢复秘密被拒绝。
- 邮件投递、错误验证码不升级、正确验证码原地升级。
- userId 与服务端余额保持不变。
- 升级后原游客秘密不能恢复正式账号。
- 登录密码校验。
- refresh 轮换、旧 token 重放撤销整个会话。
- 默认安全昵称、头像和设备列表。
- 默认未配置审核时改名返回 503、保留原昵称及头像；单独更换头像仍能保存。
- 全设备退出。
- 密码重置。
- 删除申请、即时会话撤销和取消申请。
- 单设备退出。

脚本默认检查审核缺配置的 fail-closed 行为。只有在隔离验收环境配置真实审核服务后，才使用 `--expect-nickname-approval` 验证 `SmokeUpdated` 的实际审核和保存；此模式会将测试昵称提交给配置的审核供应商，不应在未经数据处理授权的环境运行。拒绝、需复核与超时等结果由服务端自动化测试中的受控审核桩验证，不向生产提供放行模式。

脚本创建的账号和邮件仅属于本地测试。不可将该脚本改成连接真实生产环境来做破坏性账号流程验收。

### 旧库迁移

`docs/tools/LegacyAccountFixture.java` 创建一个仅含 V1～V3 的隔离 H2 数据库，预置用户、历史和金币流水。文件名必须包含 `account-migration-fixture`，数据库已有表时拒绝覆盖。seed 凭证通过 stdin 输入，不写日志；用新服务启动并迁移旧 token 后，check 验证同 userId、余额、ELO、胜负、风险状态、历史和金币流水保留且无重复欢迎礼。

### 真机检查

可执行隔离 GUI 验收脚本（仅操作 `.accounttest` 包，不安装或清除原应用）：

```bash
python docs/tools/account_ui_smoke.py --adb E:/AndroidSdk/platform-tools/adb.exe \
  --serial YOUR_DEVICE_SERIAL --evidence android_memory-game-reborn/build/account-ui-evidence
```

需要先安装通过 `-PaccountUiTest=true -PonlineEndpoint=ws://127.0.0.1:18081/ws` 构建的 debug 测试包，并为该设备设置本地 `adb reverse tcp:18081 tcp:18081`。本地服务应使用独立文件 H2 保存验收账号，SMTP 端口 2526 由脚本占用。脚本不输出密码、验证码或 token。多语言 GUI 脚本当前匹配中文按钮。

在独立测试包中检查：

- 默认旧游客入口可用，不强制注册。
- 账号页明确显示游客风险与 TLS 未配置提示。
- 绑定、登录、找回密码、设备管理和注销界面可操作。
- 网络错误不会永久锁住按钮或静默新建账户。
- 排队/好友房/对局时禁止切换账号。
- 登录另一个账号后在线昵称与余额切换，单机数据不变。
- 原应用未覆盖、未清除数据、未修改端点。

## 7. 首期之外

第三方 OAuth、云端单机进度、好友图谱、奖励资产合并、显式跨设备对局接管和多实例实时状态不在本次核心账号闭环内。继续保留单账号单 PK 控制权与服务端权威结算。这些能力需要独立提供者配置、冲突策略和回归验收。
