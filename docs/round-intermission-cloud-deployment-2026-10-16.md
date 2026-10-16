# 人机换轮等待优化云端部署记录（2026-10-16）

## 已部署

- 目标：既有 `124.220.35.87`，`memorygames-pk.service`，`/root/memorygames-pk/app.jar`，端口 8091；未改动 nginx、H2 数据目录结构、systemd drop-in 或其他业务。
- 源码：`764028d`（`feat/account-settings-navigation` 快进合并进 `main` 后推送，`main` 与远端一致）。
- JAR SHA256：`302d699056ac3538160561b8e55253bd3d00112e3919b80ea68264279a283297`，本地构建、上传暂存与线上安装三处一致；归档副本 `app.roundwait-302d6990.jar`。
- 停服后备份旧 JAR 与 H2：`/root/memorygames-pk/backup-pre-roundwait-20261016-112744/`（旧 jar sha256 `9aee7992…`，升级前生产版本）。未在运行时复制 H2 作为备份。
- 本轮无新增 Flyway 迁移，未改数据库结构；11:27:44 停服（SIGTERM 优雅关库），11:27:54 `Started PkServerApplication`（8.35 秒）。
- 部署前确认无进行中对局（最近一场 11:20:28 已结算），停服仅影响当时的两个大厅空闲连接，客户端自动重连。

## 线上验证

- 本机与公网 `8091 /actuator/health`、`/actuator/health/readiness` 均 200 UP；`/ws` WebSocket 升级返回 101（本机与公网）。
- 端到端探测（`tools/intermission_probe.py`，验证游客 userId=46，PRACTICE/BRONZE，经 `wss://www.razor123.site/ws`），三次运行：

| 运行 | 首轮小游戏 | `round_end` 相对正式截止 | 公布间隔 | 观测到的 `round_end`→`round_start` |
|---|---|---|---|---|
| 1 | game 0，80 秒 | −0.00 秒 | 2000 ms | 2.00 秒 |
| 2 | game 22，45 秒 | +0.11 秒 | 2000 ms | 1.42 秒 |
| 3 | game 4，60 秒 | +0.00 秒 | 2000 ms | 2.24 秒 |

  - 旧引擎要等 `round-grace-ms` 才结束回合（约 +10 秒）；现在回合在正式截止瞬间结束，玩家提前完成也不再延长。
  - `round_end` 每次都携带 `nextRoundStartsAt`、`serverNow`、`nextGameId`，公布间隔与 `pk.timing.practice-round-intermission-ms` 默认值（2000 ms）一致。
  - 观测到的帧到帧间隔有 ±0.6 秒的广域网抖动，这是单帧投递差异，不是调度偏差：客户端按公布的绝对时间显示倒计时，并且仍在收到 `round_start` 后才开局，不会提前或重复启动。
  - 主动 `resign` 正常结算，无异常日志（仅测试连接关闭产生的既有 `WS_TRANSPORT_ERROR` 记录）。
- 验证游客 userId=46 及若干场零 ELO 练习对局保留在库中，未撤销会话；未操作其他用户、对局或资产。
- 本轮未跑 Docker/PostgreSQL `pgTest`；未做真机回归。

## 生效范围

- 服务端改动**对所有客户端立即生效**：人机换轮不再等 10 秒宽限、间歇缩短为 2 秒；旧客户端忽略新增字段，不受影响。
- 结束页的“确认成绩/下一轮准备倒计时/最终结算”文案与倒计时需要安装新 APK 才可见。
- 真人对战与 RANKED 自动补位 Bot 保持 `round-intermission-ms`（默认 6 秒）；开局 3 秒倒计时与单题反馈节奏未调整。

## 回退

```bash
ssh root@124.220.35.87
cd /root/memorygames-pk
systemctl stop memorygames-pk.service
cp -a backup-pre-roundwait-20261016-112744/app.jar app.jar
systemctl start memorygames-pk.service
```

仅回退 JAR 即可；本轮无数据库迁移，无需回滚数据（部署后数据库已接受新写入，禁止用备份覆盖 `data/`）。
