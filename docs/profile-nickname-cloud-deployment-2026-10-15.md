# 个人资料与昵称审核云端部署记录（2026-10-15）

## 已部署

- 目标：既有 `124.220.35.87`，`memorygames-pk.service`，`/root/memorygames-pk/app.jar`，端口8091；不改动nginx和其他业务。
- 账号API：`https://www.razor123.site/api/v2/`，WebSocket：`wss://www.razor123.site/ws`。
- JAR SHA256：`9aee7992013f008326bd5fa165b7c93a4157f04adc23487ca7c271b272e2d8a9`，本地构建、上传暂存及生产安装三处一致。
- 停服后完整备份旧JAR及H2：`/root/memorygames-pk/backup-pre-nickname-20261015-115105/`。没有在运行时复制H2作为备份。
- 在该备份的独立 `migration-check/data` 副本启动仅监听loopback的18083实例，验证Flyway、Hibernate和readiness成功，随后关闭验证实例。
- 生产V7→V8迁移成功，11:51:44应用新迁移，11:51:49启动完成（服务器时区+08:00）。服务状态active，公网health/readiness均200 UP。

## 安全配置和当前可用性

服务器尚未配置腾讯TMS审核凭据及BizType。本轮没有猜测凭据、读取其他业务秘密或调用外部审核。

新增仅root可读的 `/root/memorygames-pk/nickname-moderation.env`（0600），包含显式 `PK_NICKNAME_MODERATION_MODE=disabled` 及服务器本地生成的稳定256位HMAC审计密钥。密钥未输出、未传回开发机、未入库。新增systemd drop-in `nickname-moderation.conf` 通过EnvironmentFile加载，保留原管理密码及可信TLS代理配置。

**当前线上默认昵称及预设头像可用；自定义昵称在真实审核配置完成前返回503，不会放行。** 启用前按 [昵称机器审核](nickname-moderation.md) 在服务器受限配置中设置真实TMS凭据和策略，完成供应商权限、地域、计费、Pass/Block/Review验收。仅将mode改为tencent不会自动获得审核能力。

## 线上验收

- 公网HTTPS `/actuator/health` 和 `/actuator/health/readiness`：200 UP。
- 未认证PATCH `/api/v2/me/profile`：401 `INVALID_CREDENTIALS`。
- 经公网HTTPS创建唯一部署验证游客userId=43，安全中文默认昵称成功，nextNicknameChangeAt=0。
- 联合提交 `DeployProbe` 与avatar_2：503 `NICKNAME_MODERATION_UNAVAILABLE`；GET确认昵称、头像、冷却全部不变。
- 只提交avatar_2成功，随后恢复原头像。
- 验收后撤销该游客会话；保留此独立验证账号及审计，不操作现有用户、对局、资产和历史，不运行破坏性完整账号smoke。
- 未执行生产历史昵称批量扫描或安全替名。

## Android签名包

使用已有仓库外签名配置，先生成签名锚点再构建：

```bash
JAVA_HOME=F:/jdk17 ANDROID_HOME=E:/AndroidSdk bash android_memory-game-reborn/gradlew \
  -p android_memory-game-reborn --offline writeIntegrityAnchor \
  -PonlineEndpoint=wss://www.razor123.site/ws
JAVA_HOME=F:/jdk17 ANDROID_HOME=E:/AndroidSdk bash android_memory-game-reborn/gradlew \
  -p android_memory-game-reborn --offline assembleRelease printReleaseCertSha256 \
  -PonlineEndpoint=wss://www.razor123.site/ws
```

执行后已恢复本轮生成前的 `IntegrityAnchor.java`，未将实际锚点数组提交。`verifyReflectionKeep`通过，dex icg_计数95、资源计数94；apksigner verify通过。

- 本地安装包：`android_memory-game-reborn/release/memory-game-3.6.3-74-release-20261015-1152.apk`。
- APK SHA256：`bee6f67937d54a6353c9806a06c8a4867197ccd5985f5d5aec414da3f0e61b63`。
- 签名证书SHA256：`F0:42:58:E2:20:39:5E:8A:D2:39:60:34:66:ED:18:C3:F7:11:50:B6:87:9B:64:9B:21:A9:ED:8A:69:A3:1E:D7`。

安装包已生成但未安装到用户设备、未上传应用商店或额外公共下载地址。Git只提交代码及文档，不提交APK、JAR、keystore和环境文件。版本沿用3.6.3/74，构建时间区分本包；无应用商店发行或版本递增操作。

## 后续运维边界

本轮服务部署已完成，不因审核缺配置回退到无审核旧接口。当前自定义改名安全关闭，不代表真实机器审核已经开通。上线后的数据库已接受新写入，禁止直接用备份回滚数据库，否则会丢弃部署后的账号、金币和战绩；如需回退需单独评估迁移兼容性及数据保全。
