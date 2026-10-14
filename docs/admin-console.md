# 服务端管理控制台

控制台与 PK 服务端一起打包，不需要安装 Node.js、独立部署前端或配置跨域。

## 入口与登录

- 页面入口：`/api/admin/console`，静态登录页也可从 `/admin/index.html` 访问。
- 网页使用固定管理员密码，服务端通过 `PK_ADMIN_PASSWORD_HASH` 读取 bcrypt 哈希，不保存明文密码。未配置或哈希无效时网页登录关闭（503），没有默认密码。
- 登录成功后，服务器签发随机、不透明会话，使用 `PK_ADMIN_SESSION` Cookie：`HttpOnly`、`SameSite=Strict`、`Path=/api/admin`、host-only；HTTPS 时设置 `Secure`。
- 默认有效期 8 小时，配置 `PK_ADMIN_SESSION_TTL_SECONDS` 可调整。有效期固定、不无限续期；刷新页面通过会话检查恢复登录，无需再输入密码。
- 会话存于服务端内存，只保存会话标识的摘要，支持过期清理、容量限制及主动撤销。服务器重启后会话失效，但密码哈希配置不变，重新输入同一密码即可。
- 退出管理会请求服务器撤销当前会话并清除 Cookie；网页离开或刷新不会主动注销。退出请求网络失败时页面清空视图并提示撤销未确认，不能宣称服务端已退出。
- 密码输入提交后清空，不写入 URL 或 Web Storage。CSRF 凭证仅在页面内存，写操作使用 `X-Admin-CSRF`；浏览器不能读取 HttpOnly 会话 Cookie。
- 玩家账号或邮箱登录不能代替管理员密码；第一期仍是单管理员，没有新建管理员或找回密码功能。
- `PK_ADMIN_TOKEN` / `X-Admin-Token` 保留给自动化工具，与网页登录独立。未配置 Token 不妨碍已配置密码的网页登录；没有密码则仍可使用已配置的自动化 Token。请求显式带 Token 时优先校验，错误 Token 不回退到 Cookie。

生产必须使用 HTTPS，并通过反向代理配合 IP 白名单、VPN 或内网隔离管理路径。反向代理需要放行 `/api/admin/console`、`/admin/**`、`/api/admin/auth/**` 和授权访问的管理 API；保持同源 Host，不开放宽泛跨域策略。响应限制脚本、样式和请求来源为同源，禁止被嵌入框架。

远程明文 HTTP 禁止密码登录和会话访问；本机 HTTP 仅在客户端地址与 Host 均为 loopback 时例外。若由可信 HTTPS 反代终结 TLS，显式设置 `pk.admin.trust-forwarded-headers=true`（环境变量 `PK_ADMIN_TRUST_FORWARDED_HEADERS=true`），并确保只有可信代理能直连服务，代理清理客户端伪造的转发头并设置 `X-Forwarded-Proto: https`。默认不接受任意转发头；不要以该开关代替网络隔离。

### 一次性设置固定密码

在交互终端从服务端目录运行：

```bash
JAVA_HOME=F:/jdk17 python tools/admin-password-hash.py
```

工具通过隐藏输入要求输入两次密码，建议至少 12 个字符，bcrypt 最多接受 72 个 UTF-8 字节；不会接受命令行密码参数或将明文写入文件。输出仅为 bcrypt 哈希。Windows 可在真正的 PowerShell/CMD 交互终端运行；没有 Console 的自动化执行器会被安全拒绝。

将生成的哈希放到服务的受限环境配置里：

```bash
# 单引号保护 bcrypt 中的 $，这里是占位值而非可用哈希。
export PK_ADMIN_PASSWORD_HASH='<工具生成的 bcrypt 哈希>'
```

对于 systemd，使用权限受限的 `EnvironmentFile` 并保持相同值；对于 Compose，使用受限 `.env` 或外部环境，bcrypt 值必须避免 `$` 插值（`.env` 使用单引号包裹），不要把密码/哈希提交仓库。提供哈希配置后正常重启即可使用固定密码。修改密码需要重新生成哈希并重启，会撤销全部内存会话；这不要求更换自动化 Token。

## 第一期页面

### 运行概览

展示构建版本、JAR MD5、运行时长、WebSocket 与认证连接数、匹配队列、活动对局、当前赛季、经济参数、风控统计和近期事件。

统计计数器为本次进程启动以来累计，重启清零；连接数不是去重玩家人数，也不能据此推算日活。

### 对局与匹配

- 查看当前活动对局、双方用户或 Bot、轮次、剩余时间、当前及累计得分和断线状态。
- 详情展示服务端当前快照和相关近期事件，不冒充完整历史回放。
- **按当前分数结算**：提前执行正常结算；可以额外选择退还入场费。
- **作废并退入场费**：不计本局 ELO 和胜负，退还开局时扣除的入场费。
- 操作前显示目标和后果，要求理由与确认；不自动重试写请求。

结束的对局不在活动列表；用户详情可以查看最近战绩。等待好友房和结构化队列管理、完整历史检索不属于本期。

### 用户管理

昵称模糊搜索或完整 UUID 精确搜索，最多返回 20 条；详情显示余额、ELO、战绩、风控限制、封禁、删除标记及最近 20 局。

可调币、设置 ELO、设置或解除仅 Bot 限制、按小时封禁及解封。封禁不能承诺即时踢出或自动结束活动对局；解封不等于解除其他风控限制。用户删除按钮不开放，现有管理删除语义不能当作完整账号合规注销。

邮箱检索、账号类型、设备会话撤销、注销冷静期和金币流水展示需要进一步补管理接口。

### 运行配置

按经济、对局节奏、Bot、练习奖励及难度分组展示现有配置字段与原始单位。提交前预览差异，只发送修改字段和理由；保存后重新读取真实值，避免客户端值与服务端钳制结果不同。

练习每日奖励上限单位是次/日；难度 `accuracy` 是概率，`pace` 越大越慢。当前接口一次只更新一个练习难度，多个难度修改会分次提交，失败时可能已部分生效；页面必须回读并显示实际结果。回合时长覆盖为 0 表示使用默认时长，但现有 PUT 将 0 钳制为 10000 毫秒，不能在线恢复为 0；页面明确提示，不提供误导性的“关闭覆盖”操作。

修改仅在内存中保留，重启恢复启动配置。已开局规则与经济采用 `MatchTerms` 快照，不追溯修改；其他字段遵循既有后端读取时机。页面不提供预设、持久化或一键回滚。

### 事件与运维

- 业务事件：进程内最近 1000 条的尾部，重启丢失，不是完整日志平台。
- 管理审计：数据库中最近管理变更；审计写入目前为尽力记录，失败会告警，不保证与业务变更强一致。
- 维护模式：读取服务端真实状态，开启后阻止新匹配、练习和好友房创建/加入；已有对局继续。
- 日志级别：查询配置级别与实际生效级别，支持指定 logger 的运行时调整。查询未知 logger 返回 404，不创建 logger；设置可以创建指定 logger。DEBUG 会增加日志量。

维护状态未知时不得显示“正常”；操作成功后重新查询确认。日志、审计、昵称和理由均作为纯文本渲染。

## API 变更与兼容说明

网页认证接口：

```text
POST /api/admin/auth/login   {"password":"..."}
GET  /api/admin/auth/session
  -> {"authenticated":true,"csrfToken":"...","expiresAt":"ISO-8601 时间"}

POST /api/admin/auth/logout  Header: X-Admin-CSRF
  -> {"authenticated":false}
```

登录与会话访问要求同源；提供 Origin 或 Fetch-Metadata 时严格校验。登录成功设置 HttpOnly Cookie，会话检查不返回密码或自动化 Token。密码错误与会话失效为 401，密码未配置为 503；CSRF/跨源/不安全传输拒绝为 403。密码输入不得超过 72 个 UTF-8 字节，登录按 IP 独立限流（默认 5 次/分钟），超限返回 429。

Cookie 会话的管理写操作必须同时带 `X-Admin-CSRF`；旧 `X-Admin-Token` 自动化调用不要求会话 CSRF。缺少认证的旧管理 API 继续 404 隐藏；失效 Cookie 为 401。管理写操作理由与数值校验不变。

新增只读接口：

```text
GET /api/admin/maintenance
  -> {"enabled": false, "message": "..."}

GET /api/admin/loglevel?logger=ROOT
  -> {"logger": "ROOT", "configuredLevel": "INFO", "effectiveLevel": "INFO"}
```

`configuredLevel` 可为 `null`，表示继承父 logger；未知 logger 为 404，空白 logger 为 400。维护写接口仍返回原有 `maintenance` 字段，页面不依赖写响应推测当前状态。

**所有管理写接口现在都要求 JSON 中的 `reason` 为 1–500 字符的非空白字符串。** 原有 curl、回归工具或外部调用需要补充理由；缺理由、超长理由、非法 JSON、JSON `null` 返回 400。只读接口不需要理由。封禁小时数必须大于 0；解除封禁改用 `unban`，限制局数不能为负。

新增 Flyway `V6` 仅扩大管理审计参数列容量，使长理由和配置差异能够留档，不修改已执行的迁移。部署升级前仍需备份数据库并验收增量迁移。

示例：

```json
{"roundDurationOverrideMs": 20000, "reason": "本地快速回归"}
{"enabled": true, "message": "维护中，请稍后重试", "reason": "计划维护"}
{"logger": "com.memorygames.pk", "level": "DEBUG", "reason": "排查匹配异常"}
{"matches": 10, "reason": "人工复核异常得分"}
{"hours": 24, "reason": "违规处理"}
{"reason": "人工确认解除封禁"}
```

未配置或错误 Token 返回 404；这与不存在用户/对局/logger 的 404 需区分。默认每 IP 60 次/分钟，429 时页面暂停刷新并冷却，避免自动重试风暴。

## 刷新与错误处理

只刷新当前页面，默认间隔 15 秒；切换到后台、编辑配置或操作确认期间暂停自动刷新。用户查询由管理员主动发起，不轮询搜索结果。退出后清除凭证和业务视图，并取消未完成请求；旧请求不能覆盖后来打开的页面。

网络或超时错误不代表写操作未生效。页面不重发写请求，管理员应刷新状态及审计后判断。失败时保留错误提示，而不是将空列表或默认值展示为真实数据。

## 本地构建与验收

```bash
# 从服务端目录运行；本机 Java 17 在 F:/jdk17。
JAVA_HOME=F:/jdk17 ./gradlew test bootJar
node --test src/test/js/admin-console.test.cjs

# 先在交互终端用上述工具生成哈希并 export PK_ADMIN_PASSWORD_HASH。
# 再启动本机临时验收实例，使用内存数据库，不连接线上。
JAVA_HOME=F:/jdk17 ./gradlew bootRun \
  --args='--server.address=127.0.0.1 --server.port=18091'
```

打开 `http://127.0.0.1:18091/api/admin/console`，输入自己设置的固定管理员密码。loopback HTTP 仅用于本机验收；非 loopback 的 HTTP 页面不允许提交密码。真实管理员密码不会由开发脚本自动生成、打印或写入仓库。

验收重点：错误密码、未配置密码、同源与 CSRF 拒绝、登录后刷新自动恢复、会话过期与退出撤销、服务重启后使用同一密码重新登录、Token 自动化兼容、空列表、真实用户详情、理由必填、取消操作、回读、限流冷却、背景暂停刷新、文本注入安全和窄屏布局。

`pgTest` 需要 Docker，本机无 Docker 时单独说明未执行，不用 H2 结果替代 PostgreSQL 并发验收。

基础页面验证见 [2026-10-13 管理控制台验收](admin-console-test-report-2026-10-13.md)，当前密码登录与会话验证见 [2026-10-14 管理认证验收](admin-password-session-test-report-2026-10-14.md)。
