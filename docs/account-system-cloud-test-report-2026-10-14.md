# 账号体系线上（云端）与真机测试报告

日期：2026-10-14。对象：游客/邮箱账号体系在**线上服务 + 真机**上的端到端验收。
服务端来自 `feat/account-admin-integration`（a434b91）构建产物；客户端为同源码的 debug 构建（云端 WSS 端点）。

## 一、结论

账号体系在线上通过完整验收：部署升级未丢数据，PC 端真实 HTTPS/SMTP/WebSocket 冒烟 7/7，旧客户端兼容与旧 token 迁移 5/5，真机（华为 CDY-AN90，Android 10）完成注册、资料、设备、改密、登录、退出、重启持久化及旧游客 TLS 迁移共 10 项 GUI 检查，全部通过。PK 端到端回归（真机 + WS 探针，wss 云端）S1–S4 全通过（见第四节）。

测试期间发现并修复的均是**测试工具**层面的适配问题（管理理由契约、加密存储后的昵称读取、弃赛对话框动画竞态、大厅滚动前置条件）；**未发现服务端账号功能缺陷**。

## 二、环境与部署

### 云服务器（124.220.35.87）

- 单机多服务：nginx（:80/:443，多域名）、Halo、SRS、1Panel 容器等与本项目共存；PK 服务为 `systemd memorygames-pk.service`，监听 :8091（H2 文件库 `/root/memorygames-pk/data/pk.mv.db`）。
- 升级前备份：`data/pk.mv.db` → `data.bak-pre-accounts-20261014/`，旧 jar → `app.jar.pre-accounts-20261014.bak`。
- 部署新 jar（md5 `f6a6e4d1…` 与本地构建一致），重启后 **Flyway V4/V5/V6 迁移成功**（accounts / nickname cooldown / admin audit capacity），Hibernate validate 与健康检查通过。启动脚本内置 90 秒健康轮询，失败自动回滚旧 jar（本次未触发）。
- 数据核对：升级前 18 用户 / 14 战绩 / 34 金币流水；升级后 18/14/34 全部保留，全部账号 `account_type=GUEST` 回填正确。

### TLS 终结（账号功能的前提）

- 腾讯云**大陆** IP 的 **80 端口对未备案域名做拦截**：Let's Encrypt HTTP-01 验证被 DNSPod 拦截页挡下（`dnspod.qcloud.com/static/webblock.html`），故未使用 sslip.io 等动态域名证书。
- 改为复用已备案域名既有 TLS：在 `www.razor123.site` 的 443 vhost 中新增两个 location（与站内既有 avatar/cx1 等多服务路径分发方式一致）：
  - `^~ /api/v2/` → `127.0.0.1:8091`（账号 REST）
  - `= /ws` → `127.0.0.1:8091`（WebSocket，含 Upgrade 头与 3600s 读超时）
  - `X-Forwarded-For` 用 `$remote_addr` 覆写（防伪造），`X-Forwarded-Proto $scheme`。
- 服务端 systemd drop-in 增加 `SERVER_FORWARD_HEADERS_STRATEGY=native`（Tomcat RemoteIpValve 信任 127.0.0.1 代理），账号 API 的 HTTPS fail-closed 判定在 TLS 反代后正确放行，直接 `http://IP:8091` 仍被拒绝。
- 原有 `ws://124.220.35.87:8091/ws` 明文端口保持不变，旧客户端不受影响。

### 邮件（测试窗口）

- 线上未配置正式 SMTP。测试窗口内在服务器 **loopback** 运行收件 sink（127.0.0.1:2526，仅记录收件人与 6 位验证码），systemd drop-in 注入 `SPRING_MAIL_HOST/PORT` 与 `PK_AUTH_MAIL_FROM`。验证码真实经过完整 SMTP 投递链路，测试结束后已随 drop-in 移除（见第六节）。

### 真机

- 华为 CDY-AN90（Android 10，中文 zh-Hans），USB 连接；`svc power stayon true`。
- 安装 debug APK（applicationId `com.memory.brain.training.games`，`-PonlineEndpoint=wss://www.razor123.site/ws`），R8 未开启（debug）；游戏原本已装过、身份为该机历史游客（userId 2）。
- 测试期间 Lenovo 平板重新接入 USB 造成 `adb` 双设备歧义，全部命令改用 `ANDROID_SERIAL` 指定手机。

## 三、功能验收结果

### 1. PC → 线上 HTTPS 冒烟（`docs/tools/account_smoke.py`，7/7）

真实 TLS + 真实 SMTP 投递，全部通过：

| 检查 | 结果 |
|---|---|
| 游客创建与错误 secret 拒绝（hash 存储） | ✅ |
| 邮箱验证码经 SMTP 投递、错误码不升级、正确码**原地升级**（userId/余额不变） | ✅ |
| 登录、refresh 轮换、旧 refresh 重放撤销整个会话 | ✅ |
| 昵称/头像 PATCH、设备列表（含 current 标记） | ✅ |
| logout-all 撤销全部会话 | ✅ |
| 密码重置、申请删除、取消删除保留 userId/余额 | ✅ |
| 单设备退出后 REST 立即失效、WebSocket 收到会话结束通知并关闭 | ✅ |

### 2. 旧客户端兼容与迁移（5/5）

| 检查 | 结果 |
|---|---|
| 老式 `register{uuid}` 明文 ws 注册/鉴权照常 | ✅ |
| 迁移前同 uuid 重注册（旧恢复路径）仍可用 | ✅ |
| `POST /auth/migrate` 用旧 token 迁移，**userId 不变** | ✅ |
| 迁移后旧 token 401、仅凭 uuid 恢复被拒 | ✅ |
| v2 access token 走 wss 鉴权、guest secret 幂等恢复（保号） | ✅ |

### 3. 真机 GUI（10 项全通过）

| 检查 | 结果 |
|---|---|
| 全新游客经 TLS 静默创建（#24，昵称 CloudDev） | ✅ |
| 旧游客显式授权 TLS 迁移：`ws://124.220.35.87:8091/ws` → `wss://www.razor123.site/ws`，确认页展示原环境/目标/原 userId；**迁移后 #2 保号**（ELO 1016、金币 340、5 胜 6 负） | ✅ |
| 绑定邮箱：邮件验证码→原地升级（#22→邮箱账号 #22） | ✅ |
| 修改昵称（PATCH）在账号页即时生效（CloudAcc） | ✅ |
| 设备与会话列表显示当前设备 | ✅ |
| 密码重置：邮件验证码→新密码；会话被服务端撤销后 UI 显示「仅移除本机已失效身份」，不静默新建 | ✅ |
| 登录已有账号：错误密码报 INVALID_CREDENTIALS；正确密码登录成功（#23） | ✅ |
| 退出并创建新游客：服务端撤销成功后才换本机身份，新游客 #24 ≠ #23 | ✅ |
| 进程重启后 Keystore 身份保持（#24 仍为 #24） | ✅ |
| 凭据加密存储：`online_secure_identity` 为 Keystore 密封 blob；`online_identity` 仅存密文值 | ✅ |

### 4. PK 端到端回归（真机 + WS 探针，云端 wss）

套件 `android_memory-game-reborn/tools/pk-test-1010/regression.py`，对手为 PC 上的脚本化 WS 探针，双方均连线上服务（探针走 `wss://`，经 TLS 反代）。热配置按 runbook 设置（2 回合 × 20s、grace 1s、间歇 0.5s、起局延迟按场景、入场费 0、重连窗 10s），管理调用经 SSH 隧道（管理面不暴露公网）。

**最终整轮结果：39/39 通过**（真机 + WS 探针，云端 wss；与 2026-10-13 基线相同的全绿成绩，且本轮针对新服务端构建与当前客户端）。历程：首轮 21/25（S4 11/11，S3 5/6，S2 3/5，S1 因管理 reason 契约中断）→ 修正工具适配并修好大厅滚动前置后，单场景 S1 18/18、S2 5/5、S3 6/7→修复昵称缓存→最终整轮 39/39。具体检出项：

- S1 排位全流程：入队→配对→两段式开始→两回合（同种子同题）→结算→ELO 零和镜像→手机显示败北。
- S2 弃赛镜像：真机在回合计时内弃赛（含「退出将失去押注」确认），探针收到 match_end(W)，服务端 forfeit 结算。
- S3 好友房：真机建房码→探针凭码入座→双方闲置成 0:0 平局→(好友模式 ELO 幅度减半) 手机显示平局。
- S4 对手掉线：探针2 断线→真机收到「对手正在重新连接」→探针重连补发剩余时长→窗口超时判负，服务端 SETTLED forfeit=true。
- 额外手动验证：15s 无对手时服务端 Bot 兜底配对（Bot 名如 SharpEye/ThinkTank）；真机在局内弃赛按钮/对话框链路；Bot 局结算后 ELO/金币变化。

### 5. 测试中修复的测试工具问题（均为套件侧适配，非产品缺陷）

1. `http_base()` 只识别 `ws://`，wss 云端跑法不支持 → 支持 wss→https，并新增 `PK_ADMIN_BASE` 环境变量把管理调用指向 SSH 隧道（管理面不暴露公网）。
2. 管理接口新增「非空 reason」契约后，套件旧 `set_config` 断言 400 → 自动附带 reason。
3. 客户端凭据加密后（`SecurePrefs` 密封），套件从 `shared_prefs` 读昵称拿到密文 blob → 改为「预填昵称优先、否则读大厅 online_profile_line，并把大厅恢复到入口按钮可见的滚动位」。
4. S2 弃赛：BACK 后单次 dump 会错过动画中的对话框，二次 BACK 会把对话框取消掉 → 改为轮询等待对话框出现（最多 5s）再点确认。
5. `LBL_LOBBY` 文案漂移（对战机器人 → 人机对战）→ 兼容两种文案。
6. 前几轮失败均为**前置条件**：大厅滚动位置（套件 `back_to_lobby()` 连按 BACK 在入口不可见时会把应用退回桌面）、运行中双设备 adb 歧义、以及中途手动操作与套件并发干扰——修正前置条件后全部通过。

## 五、线上数据与副作用

- 测试新增账号（均为一次性测试账号，昵称可辨识）：CloudProbe、smoke-*、ui-*（邮箱号）、LegacyProbe、ProbeOne/ProbeTwo/ProbeCheck、CloudDev 等；真机账号 #2（迁移，保号）、#22（邮箱）、#23（邮箱）、#24（CloudDev，当前在用）。测试邮箱全部指向 sink，非真实可达邮箱。
- 未改动生产数据库结构以外的配置；未部署 release 签名包；未接受 Android SDK licenses。

## 六、测试后已执行与仍需人工的事项

已执行（本报告验证）：

- **管理 token**：已删除 `test-window.conf` drop-in 并重启；管理 API 无有效 token 返回 404（恢复禁用）。
- **SMTP sink**：已停止进程；测试窗口注入的邮件环境变量随 drop-in 移除。当前注册/找回返回 503（符合设计）。
- **热配置**：`roundDurationOverrideMs` 无法热清除，已通过重启回落默认——验证 `auth_ok` 经济参数为 entryFee=30 / winCoins=50 / drawCoins=15，对局 5 回合、重连窗 90s。
- **能力保留**：`tls-proxy.conf`（forward-headers）保留——TLS 反代下 `request.isSecure()` 依赖它；明文 `ws://IP:8091` 与旧客户端不受影响（升级 101）；loopback 明文 `/api/v2` 仍 403。
- 临时 sslip 尝试的 HTTP vhost 已删除；账号路由保留在 www vhost（2 个 location）。

仍需人工：

1. **正式 SMTP**：上线前配置真实 `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` 与 `PK_AUTH_MAIL_FROM`，否则注册/找回返回 503（符合设计）。
2. 若希望使用独立子域（如 `pk.razor123.site`）承载账号 API：需在 DNSPod 添加解析并签发证书，随后把两个 location 挪到新的 server 块。
3. 测试账号（CloudProbe、smoke-*、ui-*、ProbeOne/Two、ProbeCheck、DefaultCheck、CloudDev 等）留存在线上库中，可按需用管理面清理。

## 七、产物与证据

- 服务端新 jar：`server_memory-game-reborn/build/libs/memorygames-pk-server-0.1.0.jar`（已在云端运行）
- 真机 APK（云端 WSS）：`android_memory-game-reborn/build/outputs/apk/debug/memory-game-3.6.3-74-debug-20261014-1414.apk`
- 账号 GUI 证据截图：`android_memory-game-reborn/build/account-ui-cloud-evidence/`
- PK 回归日志：`android_memory-game-reborn/build/pk-cloud-regression-final.log`（及 run1/run2 历史日志）
- 客户端状态日志：`android_memory-game-reborn/build/pk-live-logcat.txt`
- 部署日志（云端）：`journalctl -u memorygames-pk`
