# Memory Games PK Server

《Memory Games / Brain Training》多人在线 PK 服务端（需求见 `../docs/online-pk-prd.md`）。

## 模块

- `ws/PkWebSocketHandler` — WebSocket 网关（`/ws`）：游客注册/Token 鉴权、单会话踢出、消息路由
- `match/Matchmaker` — 匹配队列：ELO 分桶（±100 起步，等待中逐步放宽），15s 无对手触发 Bot 兜底
- `match/MatchRoom` — 对局状态机：5 轮种子化出题下发、服务端权威计分（按过关流重算，客户端分值表对齐）、回合截止/弃赛/断线判负
- `game/GameCatalog` — 首发游戏池（6 个）与计分表，从客户端 `updateOnlineScore()` 移植
- `bot` — Bot 由房间内驱动：按段位参数化的作答间隔与正确率
- `rating/Rating` — ELO（K=32）与段位
- `user` — JPA 持久化（H2 默认 / Postgres 生产 profile）

## 构建与测试

```
./gradlew test        # 含双端全量对局集成测试（两个真实 WS 客户端打满 5 轮）
./gradlew bootJar     # 产物 build/libs/*-boot.jar
```

## 部署（海外单机，PRD D-2）

```
docker compose up -d   # app + postgres 16；环境变量 PK_DB_PASSWORD
```

客户端默认连 `wss://pk.memorygame.example.com/ws`（`OnlineConfig`），debug 构建可在 Online
大厅长按标题改为内网地址（如 `ws://192.168.x.x:8080/ws`）真机联调。

## 与 PRD 的偏差（M1）

- Redis 未引入：房间态与匹配队列在内存（单实例），重启即判负，客户端 90s 重连窗口可容忍；M2 换 Redis 注册表
- 战绩历史表未持久化（M2）
- 断线重连为"重挂监听 + 当前轮补发"简化实现，不含局中状态恢复
