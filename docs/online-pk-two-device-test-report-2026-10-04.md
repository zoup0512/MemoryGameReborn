# Online PK 双机全量测试报告(2026-10-04)

**分支**:`dev/online-PK`(commit c91e5b3 吸收原版流程 + c4fe9ca 四项修复)
**服务端**:本地 Windows,`java -Dserver.port=8090 -jar`(PK_ADMIN_TOKEN=lan-test-token),热配 20s 回合 / 3s 间隔 / 8s 宽限
**设备**:CDY-AN90(Player9531,720×1600 @2.0)+ BKL-AL20(Player9438,1080×2160 @3.0)
**方法**:uiautomator 视图树断言 + 服务器事件环/dashboard 对照;dump 前先删 `/data/local/tmp/ui.xml` 防陈旧文件误判

## 测试结果总览

| # | 场景 | 结果 | 证据 |
|---|---|---|---|
| 1 | 双机连接+注册 | ✅ | dashboard wsConnections=2 / authenticatedUsers=2,欢迎金币发放 |
| 2 | 大厅(原版三按钮+实时战绩) | ✅ | Play Online/History/Online Ranks;ELO/段位/赛季行真实数据 |
| 3 | 真人排位配对 | ✅ | 事件环 PAIRED a=1 b=2 waited≈300ms;双方 STATS 板互见昵称 |
| 4 | STATISTICS+BETS 对比板 | ✅ | wins/losses/draws/ratio 三列(对手实时战绩)+ 双 30 金币 BETS |
| 5 | 双 START 接受门 | ✅ | 先按方见"Waiting for your opponent"倒计时;双按后 round_start;超时退费路径 MATCH_NOT_ACCEPTED 亦验证 |
| 6 | 同种子同题 | ✅ | 两机同轮同游戏同题(Correctly? 8-2=5 / Colors / Memory Grid) |
| 7 | 跨机 HUD 分数同步 | ✅ | BKL 得分后服务端推帧,CDY 视角 OPP 0:8;BKL 本地侧刷新滞后记为已知小缺口 |
| 8 | 弃赛结算镜像 | ✅(修复后) | 服务端 `resign received`→`settled forfeit=true`;CDY "You lost"/-16 ELO ↔ 对手 "You won"/+16/+50 |
| 9 | 结算→逐轮比分列表 | ✅ | item_list_online_game 行(游戏名+比分+合计),win circle;BKL 大屏滚动可达 |
| 10 | Next Match / 退出 | ✅ | 底栏按钮就位;back 回大厅 |
| 11 | History 双端 | ✅ | 平局+弃赛局双端镜像,Today 日期头,绿环/crown 正确 |
| 12 | TOP Online 榜 | ✅ | top_request→top20 排名,双用户在榜 |
| 13 | 好友房全流程 | ✅(流程) | 建房(房码 6833/8050/0206)→跨机输码→配对→双 Start→同题开局 |
| 14 | 好友局中弃赛 | ⚠️→✅ | 三次复现弃赛丢失(bug#6),根因修复后 bot 局闭环验证(见下) |

## 发现并修复的缺陷(commit c4fe9ca)

1. **bug#6 根因:弃赛命令从未发出**(17 个游戏文件)——弃赛框 OK 的副本各自调 `confirmBackPress()`,大多数直接 `super.onBackPressed()` 退出,从不经过唯一含 resign 的 `openMainMenu()`;Game1 的 back 白名单还让白名单外游戏无框静默退出。修复:所有 OK 统一路由 `openMainMenu()`,白名单移除。
2. **半死 TCP 无感知**——手机 WiFi 断流时连接"能收不能发",帧静默丢失。修复:OnlineClient ping/pong 看门狗(一轮未回即判死重连)+ `resign()` 每 3s 重发(6 次)直到 match_end 确认。
3. **高密度屏结算卡裁剪**(BKL @3.0)——结算卡/逐轮列表底部按钮超出可视区且不在视图树。修复:fill-viewport ScrollView 包裹。
4. **大厅战绩陈旧**——结算后 w/l/d 仍是上次 auth_ok 快照。修复:`match_end` 携带结算后战绩,session 即时并入缓存 profile。

## 弃赛修复的闭环证据(23:13,bot 局)

```
客户端 logcat: sendResign matchId=f537ea0d1ad6 connected=true retriesLeft=6
服务端日志:   resign received user=1 room=f537ea0d1ad6 (+2.5s)
              settled forfeit=true scores 0/106
结算卡:       You lost / +0 / 240 / -16 / 984 → Yes → 逐轮列表(bot 34+72)
大厅:         ELO 984、1L 4D 即时刷新
```

## 遗留观察(非阻塞)

- **测试环境 WiFi 断流频发**:每轮重装后偶发"上行静默死亡"(能收帧不能发帧,服务端零日志)。看门狗+重发可自愈,但排队态断线仍会卡"Searching"(P2:非对局态 send 失败无自动重连,当前靠 ping 超时 20s 后判死,用户再点 Play 触发重连)。建议稳定网络或 USB 网络共享下复测。
- matchmaker 对"客户端 join 重试 + 对局未清理"的竞态会偶发 409/兜底错位(23:09-23:10 事件环),建议后续给 join 幂等。
- HUD 本地分侧(BKL 视角 ME)依赖对手帧触发刷新,自己得分后本地侧不即时更新(服务端权威分正确)。
- 本地服务器为 H2 内存库,重启清数据;热配 20s 回合仍是测试参数。

## 复现环境

- 服务端:`cd server_memory-game-reborn/deploy && PK_ADMIN_TOKEN=lan-test-token java -Dserver.port=8090 -jar memorygames-pk-server-0.1.0.jar`
- 客户端:debug 包默认端点 `ws://192.168.110.176:8090/ws`(IP 变更需同步 `OnlineConfig.LAN_TEST_ENDPOINT`)
- 测试热配:`PUT /api/admin/config {"roundDurationOverrideMs":20000,"roundIntermissionMs":3000,"roundGraceMs":8000}`
