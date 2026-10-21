# 舒尔特表公平性云端部署（2026-10-16）

## 发布结果

- 已部署源码：`main` 的 `3ea6ea8`（修复提交 `27935fd`）。服务端仅更新 JAR；Android 随机 RGB 属于客户端改动，不会通过此次云端发布下发。
- 目标：`124.220.35.87`，`memorygames-pk.service`，端口 `8091`，运行 `/root/memorygames-pk/app.jar`。
- 本地构建：Java 17，`gradlew -p server_memory-game-reborn bootJar` 成功；构建时间 `2026-10-16T07:33:00.735735800Z`。
- 新 JAR SHA256：`bd34c1c30609da27da5610913f8c778f1ab5efd463e9b5bc4a823c9b5ec2f152`。本地、上传暂存、安装后 JAR 一致。
- 暂存/归档包：`/root/memorygames-pk/app.schulte-bd34c1c3.jar`。
- 备份目录：`/root/memorygames-pk/backup-pre-schulte-20261016-153429/`，包含停服后的旧 `app.jar` 与完整 `data/`。
- 旧 JAR SHA256：`302d699056ac3538160561b8e55253bd3d00112e3919b80ea68264279a283297`。
- 备份 H2 `data/pk.mv.db` SHA256：`f1bbe0d16e4575f21d9f86123db4a9b34b9421e0bbafdaf9bed7e758e700ba08`。备份在优雅关库后创建，未复制运行中的 H2 作为备份。

## 执行与影响

- 使用已有 SSH 主机信任与密钥；未更改 SSH、管理员凭据、昵称审核环境、nginx、TLS、端口或 systemd 配置。
- 停服前实时确认 `pk_rooms_active`、`pk_queue_depth`、`pk_db_queue_depth` 全为 0；当时两个大厅空闲 WS 连接在重启时断开。无进行中对局被中断。
- `15:34:29 +08:00` 优雅停服；`15:34:38` 应用启动完成（7.571 秒），`15:34:40` readiness 为 UP。就绪轮询启动初期的连接拒绝属于预期短暂停服，随后恢复。
- H2 schema 仍为 8，Flyway 确认无需迁移。未回滚/替换现有数据。
- 部署脚本对活动对局、匹配队列、DB 队列、原包哈希、新包哈希和服务 PID 进行校验；启动 90 秒内未恢复会恢复本次备份旧 JAR 并重新启动。实际发布成功，未触发回滚。
- 进程环境与启动参数未发现加速 override；线上回合实际为 80/30/30/45/60 秒。

## 线上验收

1. 本机、公网 `8091` 与正式域名 `https://www.razor123.site` 的 health/readiness 均为 `200 UP`。
2. `tools/schulte_fairness_probe.py` 经正式 `wss://www.razor123.site/ws` 实际运行通过，复用既有验证游客 `userId=46`。未打印令牌或密码。
3. 新协议完整练习局 `2369c92dcab2`：五轮都收到准备帧，首轮静默等待 8 秒、后续轮各 3 秒，期间无 AI 得分/正式开始/结束帧；ready 后才获得完整正式时长。首轮准备期间重连、错误 match/round ready、正式开始后重复 ready 均通过。

| 轮次 | gameId | 正式时长 | 接收侧观测时长 | AI 单轮得分 |
|---|---|---:|---:|---:|
| 1 | 0 | 80 秒 | 80.01 秒 | 94 |
| 2 | 19 | 30 秒 | 30.00 秒 | 24 |
| 3 | 15 | 30 秒 | 30.17 秒 | 32 |
| 4 | 22（舒尔特表） | 45 秒 | 44.72 秒 | 48 |
| 5 | 10 | 60 秒 | 60.03 秒 | 92 |

4. 服务端舒尔特正式起点为 `15:38:43.769`，下一轮准备为 `15:39:30.769`（45 秒正式时长 + 2 秒换轮）；接收侧的亚秒差异来自网络投递。五轮公布换轮间隔均为 2000ms。
5. 完整局以玩家闲置落败正常结算，0 ELO、0 金币；另建旧协议验证局 `aa5bc08f9ed7`，不发送 `prepareRounds` 也能直接收到正式开始帧（恰为 game 22），随后正常弃赛结算。
6. `15:40:53` 最终检查：服务 active，新 JAR 哈希不变，readiness UP，2 场验证局均已结算，活动房间/匹配队列/DB 队列均为 0；发布后无 ERROR/Exception 日志。保留已有 Flyway/H2 版本兼容警告，未做依赖升级。

探针不是只读：保留上述两场验证练习记录及会话，不删除验证账号或改动其他用户资产。没有为了测高序号发送伪造得分流。48 题上限、额外 2 秒失误成本由本地回归和部署包一致性验证，单场线上分数不能单独证明边界或真人胜率。

## 生效范围与未验收项

- AI 舒尔特基础间隔 1800ms、失误额外 2 秒、最多 48 个目标：所有云端 AI 舒尔特回合生效。
- 公平准备握手：仅新版 Android 显式 PRACTICE 对局启用。旧客户端保留旧时序，不会凭空获得准备暂停。
- 客户端随机 RGB、黑白对比文字、完整教程/倒计时衔接仍需安装新版 APK。未安装设备、未做真人/设备 UI 回归，也未发布 APK 下载更新。
- 不涉及 NAS 服务或 Docker/PostgreSQL。

## 回滚

仅恢复本次备份旧 JAR，禁止用备份数据库覆盖部署后写入：

```bash
ssh root@124.220.35.87
cd /root/memorygames-pk
systemctl stop memorygames-pk.service
cp -a backup-pre-schulte-20261016-153429/app.jar app.jar
systemctl start memorygames-pk.service
curl -fsS http://127.0.0.1:8091/actuator/health/readiness
```
