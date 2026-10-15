# 服务端昵称审核与预设头像

## 默认行为与边界

**默认配置不支持客户端自定义昵称，直到配置真实机器审核服务。** `mode=disabled` 不是放行模式：合法自定义昵称返回 HTTP 503 `NICKNAME_MODERATION_UNAVAILABLE`。本地词库只能提前拒绝，不能在外审不可用时替代生产审核。没有 `allow`、`mock`、任意 URL 回调或可配置通过接口。

服务端自行生成的昵称从固定的中文前后词库各选一词，例如“晴空小鹿”“星河企鹅”；不再生成 Player 数字。144 种组合允许重名，用户身份永远使用 userId。仅 `NicknameService.generate` 内部入口免外审，客户端提交相同文字仍需审核，没有按前缀或命中词库免审的逻辑。匿名化使用固定“已注销棋友”。机器人昵称是既有服务端配置，与用户写入分离。

本次不调用公网审核、不部署、不自动清洗历史生产数据。测试假 provider 只在 `src/test`，不会打入 bootJar；现有回归显式导入 `PassingNicknameTestConfig`，不能用生产环境变量启用。

## 统一规范及接口契约

- 先拒绝原文中的控制、格式、未分配、孤立代理、私有区字符，U+034F/U+2800、Hangul fillers U+115F/U+1160/U+3164/U+FFA0、variation selectors U+FE00–FE0F/U+E0100–E01EF，以及 NBSP 等非普通空白；因此首尾换行、零宽连接符也不允许。仅 strip 边界普通 U+0020 空格（内部 U+0020 允许），NFC 规范化，按 Unicode **码点**计数 2–16。emoji 代理对计一个，组合字符经 NFC 后计数；不是按字素簇计数。
- 格式不合法：400 `INVALID_NICKNAME`。头像仅 `avatar_1`–`avatar_12`，无图片 URL、文件上传、自定义头像。非法头像先于任何外部审核返回 400 `INVALID_AVATAR`。
- `POST /api/v2/auth/guest`：首次创建时 nickname 缺失/null 为明确的服务端生成入口；显式空字符串/空白为 400，不是默认名。显式合法字符串必须审核。恢复身份时 nickname 完全忽略，校验 secret、身份状态后恢复原名。
- 旧 WS `register`：新 UUID 同样 null/缺失生成、显式自定义审核；已有 UUID 只走原有身份恢复和 token 轮换，不验证或覆盖所传 nickname，不消耗昵称冷却。被禁用、升级、封禁、待注销的旧身份依然不能恢复。UUID 的既有弱恢复模型没有在本次扩展为强凭证。
- `PATCH /api/v2/me/profile`：nickname 缺失/null 为不改名；规范化后等于当前名不算新昵称，无需外审、不启动冷却。avatarValue 可独立改变；昵称拒绝/待复核/异常时，联合头像更新全部不落库。
- 首次改名无等待，成功后 7 天才可再次改；429 `NICKNAME_COOLDOWN`。审核失败、待复核、不配置、头像更新、创建默认名都不消耗冷却。
- `GET /api/v2/me`、新增 `GET /api/v2/me/profile`、PATCH profile 及所有 AuthResponse.user 均使用相同 UserView，新增 **`nextNicknameChangeAt: long`**，毫秒 Unix epoch。`nicknameChangedAt=0` 时返回 0；其他情况返回 `nicknameChangedAt + 604800000`，即使已过期也保留该真实时间。客户端以 0 或 <=当前时间判可改；此字段只供展示，服务端最终判定。
- 原客户端通过 Gson 反序列化 UserView，新增 JSON 字段是兼容扩展，不改变已有字段和响应嵌套；服务端不要求客户端回传该字段。

### 外审结果

| 腾讯 TMS Suggestion / 情况 | 结果 | 是否公开或提交 |
|---|---|---|
| Pass | 正常响应 | 提交时再次校验后才写入 |
| Block / 本地拒绝 | 422 `NICKNAME_REJECTED` | 否 |
| Review | 422 `NICKNAME_PENDING` | 否，不存待公开昵称 |
| Error、未知/缺失 Suggestion、缺失/非法 RequestId、非200、异常、超时 | 503 `NICKNAME_MODERATION_UNAVAILABLE` | 否 |
| disabled、未知mode、缺凭据/BizType、限流/并发耗尽 | 同上503 | 否 |

Review 不提供后台自动审批；用户可以重新选择昵称。若需要人工复核工单/异步转正，应另行设计，不能把 Review 当 Pass。

## 腾讯云 TMS provider 配置

使用 Java 17 HttpClient，固定 HTTPS `https://tms.tencentcloudapi.com/`，不跟随重定向；API `TextModeration` / `2020-12-29`，Content 为 UTF-8 后 Base64，BizType 为昵称专属策略，DataId 是随机请求号。使用 TC3-HMAC-SHA256（service=tms、scope=date/tms/tc3_request、签名 content-type/host）；可传临时凭证 Token。未新增 SDK 依赖。凭据只由环境变量注入，不写入 Git、命令行参数或日志。

| 环境变量 | 默认/用途 |
|---|---|
| `PK_NICKNAME_MODERATION_MODE` | `disabled`；配置后设 `tencent` |
| `PK_TENCENT_SECRET_ID` | 空；最小权限审核凭证 ID |
| `PK_TENCENT_SECRET_KEY` | 空；凭证密钥 |
| `PK_TENCENT_SESSION_TOKEN` | 空；使用临时凭证时必填 |
| `PK_TENCENT_TMS_REGION` | `ap-guangzhou`，按 TMS 实际可用地域设置 |
| `PK_TENCENT_TMS_BIZ_TYPE` | 空；须创建并验证用于公开昵称的审核策略，空时不请求、不放行 |
| `PK_NICKNAME_AUDIT_HMAC_KEY` | 空时生成进程随机密钥；生产应注入至少32字节高熵独立秘密以稳定审计摘要 |
| `PK_NICKNAME_TIMEOUT_MS` | 3000；钳制在200–10000毫秒；连接及单次请求超时 |
| `PK_NICKNAME_MAX_CONCURRENT` | 8；钳制1–64；满额立即503，不排队 |
| `PK_NICKNAME_GLOBAL_PER_MINUTE` | 60；每进程全局外审调用上限 |
| `PK_NICKNAME_SUBJECT_PER_MINUTE` | 5；每用户/新建身份每分钟外审调用上限 |

管理员应在腾讯云开通 TMS、确认地域与计费、建立适合昵称的策略和自定义词库、授予仅 TextModeration 所需权限后注入配置。程序默认不会发外部请求。此交付未进行真实服务调用或公网核验；发布前需用隔离测试凭据验证真实签名、策略 Pass/Block/Review、权限和额度，不可直接依赖离线测试认定已验收供应商。

## 基础防线、事务与请求治理

本地检查经 NFKC 折叠的少量明确敏感词、辱骂、色情博彩词、官方/系统/游戏客服及管理员冒充，以及网址、邮箱、完整手机号、明确微信/QQ等联系前缀+长账号。不会仅因“客服”“官方”单词或任意短数字一概封禁。规则不是完整内容安全词库，需与腾讯策略一起维护；修改规则或供应商策略时增加 `POLICY_VERSION`，当前为 `nickname-v1`。

profile 先做参数检查，在短事务中查身份与冷却，事务结束后执行本地/外部审核，再开启短提交事务。提交重新锁用户和 session，校验撤销、过期、封禁、注销及审核前昵称/冷却快照。审批是不可由客户端构造的短时对象，绑定 user/installation、session/上下文、规范化原文、HMAC 摘要和策略版本；提交再次校验，避免审甲写乙和并发绕过。并发改名冲突返回409 `PROFILE_CHANGED`，应刷新后重试。游客创建也是先审后短事务、重新解析 installation，唯一键冲突整笔回滚，不产生孤儿 welcome ledger。

旧 WS 新建自定义的外审在 handler 内同步等待（默认最多3秒），但不持数据库事务/行锁；总并发受限。默认生成与旧身份恢复不外调。未来如连接规模显著增加，可改为有界异步注册队列；不能移除限流或改成失败放行。

全部 review 请求（包括本地拒绝和缓存命中）另有固定每主体30次/分钟、全进程600次/分钟的轻量有界入口限流；超限503且不逐条写审计，避免本地拒绝刷库。以下供应商限额则只计算真实外审调用。相同 subject+上下文+策略+HMAC 摘要的请求，60秒内共享 in-flight future 和结果；最多4096项，只存摘要和决策、不缓存昵称全文，不跨用户共享审批。UNAVAILABLE 不缓存（恢复配置后可重试，但仍受限流）；拒绝/待复核/通过缓存只减少供应商请求，不绕过提交校验。限流窗口、缓存和信号量是**单进程**，多实例应按实例数配置供应商总配额，或另行接入共享限流；进程重启会清空缓存/窗口。

## 隐私审计与运维

Flyway `V8__nickname_moderation.sql` 新增 `pk_nickname_audit`：user_id、created_at、result、request_id、digest、policy_version，**不存全文**。未创建账户的游客/旧注册审核 user_id=0；由随机 provider DataId/RequestId 排查调用，成功后身份记录另由既有账户审计追踪。digest 为策略域隔离的 HMAC-SHA256，不是容易字典反查的无盐散列。空HMAC环境变量时随机进程密钥不写日志，但跨重启无法关联，历史扫描 digest 也会过期，生产需稳定密钥；轮换密钥后必须重新扫描。

不记录 HTTP 请求/响应体、Authorization、凭据、昵称或异常内容。不要启用生产 HTTP wire/body/debug日志，也不要把 nickname 填到运营 reason 后再记录。profile 失败审核记录通过独立短事务保留，不会随联合更新回滚。数据库不可写时请求不会成功，运维需排查数据库健康。

审计不是业务昵称副本；用户表/既有比赛历史本身仍保存公开昵称。数据库/备份需按现有隐私访问控制管理。建议保留审计30天，根据合规要求调整，由管理员受控执行清理，例如删除 `created_at < 当前毫秒时间-2592000000` 的记录；本次不自动删除生产审计、不设置无人知晓的计划任务。监控503、422比例、供应商 RequestId 和全局调用量，但不要将昵称作指标标签。

## 管理员历史扫描及安全替名（显式执行）

所有入口复用现有 `/api/admin` 身份、浏览器CSRF和限流保护。上线不会自动扫描或改名。操作前备份、安排低峰、确认供应商审核配置与配额；历史明显违规时可先按既有封禁流程阻止其对外活动。

1. 使用已授权管理员调用 `POST /api/admin/nicknames/scan?after=0&limit=10`（limit钳制1–20），输出 users 数组，每行 userId/digest/result/policyVersion，不回传昵称全文、不修改用户。按最后一行 userId 继续 after；顺序扫描避免并发供应商洪峰。单页最坏耗时 limit×timeout，代理超时紧张时使用limit=1。只扫描非deleted用户。异常503结果明确保留，不能当作PASS。
2. 人工复核 `INVALID_NICKNAME`、`NICKNAME_REJECTED`、`NICKNAME_PENDING`；503先修配置/配额后重扫，不把服务不可用当违规。如需核对原文，使用既有受限用户详情页面，不复制进日志。
3. 决定安全替名时，调用 `POST /api/admin/nicknames/{id}/safe-reset`，JSON `{"expectedDigest":"扫描返回的64位摘要","reason":"历史内容安全整改"}`。必须显式授权；reason校验但不记录自由文本，避免误粘贴敏感名。expectedDigest不匹配返回409，须重新扫描，防止覆盖扫描后用户已改好的名字。
4. 替名仅生成受控中文名，不允许管理员任意提交新名、无外审绕过参数。保持头像、资产、统计和原昵称冷却；需要空闲玩家（409 ACCOUNT_IN_MATCH时稍后重试），同步擦除该user的比赛历史快照昵称，并记录ADMIN_SAFE_RESET。通知在线用户刷新/重连以更新旧缓存。
5. 再次读取用户/扫描确认，分批记录处理userId与RequestId。误处理可按正常昵称申请流程改回并接受审核，不能直接SQL恢复违规名。

历史扫描只覆盖当前用户昵称；替名同步 scrub 该用户全部比赛历史。单独存在于旧比赛记录但已不在当前用户昵称中的历史违规名，不会被扫描发现，应在受控备份副本做专项核查后按userId安全替名，或追加独立历史快照审核批次。不要对生产表直接做无条件批量覆盖。

## 离线验证

Java17，Gradle `--offline test bootJar`。针对性测试覆盖 Pass/Block/Review/异常/未知/未配置，TC3独立签名fixture，模拟HTTP超时且不访问网络，联合回滚、头像不审、冷却、外审时无事务、审核期间撤销/封禁/注销/冷却变化、重复请求去重与身份绑定、单用户/全局限流及并发上限、中文默认名、Unicode码点与禁止字符、真实本地WS旧注册绕过、新游客拒绝无身份落库、历史扫描/安全替名及其并发保护。供应商真实联网验收与PostgreSQL容器并发属于另行验证项，本次不连接生产或部署。
