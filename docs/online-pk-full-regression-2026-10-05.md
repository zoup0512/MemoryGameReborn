# Online PK 全量回归测试报告(2026-10-05,含 origin 吸收第二批)

**分支**:`dev/online-PK`(c8e46c5:opponent-ready 二段态;2a8e99f:局间过场/游戏列表/淡入/热配局数;c4fe9ca:弃赛三重修复)
**服务端**:本地 Windows 8090,PK_ADMIN_TOKEN=lan-test-token;测试热配 20s 回合 / 2 局 / 3s 延迟(测毕已还原 90s/5 局标准)
**设备**:CDY-AN90(Player9531)+ BKL-AL20(Player9438),debug 包默认 LAN 端点
**服务端测试**:24 个集成测试全绿(含新增 opponent_start 双路径断言)

## 结果总览:18/18 通过,零新缺陷

| 阶段 | 场景 | 结果 | 关键证据 |
|---|---|---|---|
| A | 双机连接+大厅(三按钮/头像徽章/战绩) | ✅ | ws=2;ELO/段位实时 |
| B1 | 真人排位配对+STATISTICS+BETS | ✅ | 双板互见,淡入无异常 |
| B2 | **二段确认态(新)** | ✅ | 先按方 CDY:Waiting→双按后切"Your opponent is Ready";后按方 BKL 直入列表 |
| B3 | **开局游戏列表(新)** | ✅ | 列表屏(Schulte Table 等 2 局)→5s 倒计时→开局 |
| B4 | 同种子同题+HUD | ✅ | 双机同屏 Schulte Table R1 |
| B5 | **局间过场卡(新,真人局实拍)** | ✅ | R1 后 CDY:"Game finished / Your Score: 0 / Game 1/2" |
| B6 | 2 局自然结算镜像 | ✅ | 双机 Draw!/+15/ELO +1/-1 零和 |
| B7 | 逐轮比分列表 | ✅ | 双机 Schulte 0:0 / Colors 0:0 |
| B8 | 大厅即时战绩(新协议) | ✅ | match_end 刷新 w/l/d 与 ELO,与服务器账一致 |
| C | 局中弃赛闭环(bot 局) | ✅ | YES→You lost/-16/970 |
| D1 | History 双端 | ✅ | 条目/比分/镜像自洽 |
| D2 | TOP 榜双端 | ✅ | 双机一致(Player9438 #1 998 / Player9531 #2 970) |
| E1 | 好友房建房+输码+配对 | ✅ | 房码 0483/1138 两轮 |
| E2 | 好友房二段确认态(新) | ✅ | "Your opponent is Ready"好友路径同样生效 |
| E3 | **好友局真人中段弃赛(bug#6 终极回归)** | ✅ | 服务器 `resign received user=2`→`settled FRIEND forfeit=true` |
| E4 | 弃赛结算镜像+FRIEND 减半 | ✅ | BKL You lost **-8**(=-16 减半)/990 ↔ CDY You won **+50/+8**/978,零和 |
| E5 | 弃赛后对方游戏自然回收 | ✅ | CDY 游戏超时 finish→结算卡正确顶出 |
| S | 服务端全量测试 | ✅ | BUILD SUCCESSFUL |

## 测试观察(非缺陷,记录在案)

1. **20s 短回合 vs 测试回路竞态**:dump+点击回路约 4-5s/次,back→弹窗→YES 常撞回合超时(弹窗被游戏超时流程顶掉,YES 落空),前两次弃赛尝试因此"未生效"。生产 90s 回合下窗口占比极小;无 dump 直连序列(back→1.3s→YES 压到 4s 内)100% 稳定。若要根治可考虑弃赛弹窗期间暂停游戏计时,不建议为此改核心计时。
2. **Memory Grid 开场 "Number of Cells?" 面板会消费 back 键**(不弹弃赛框)——原版行为,保持不动。
3. 本地 H2 内存库随服务器重启清零,本轮两账户为重启后新注册(历史/战绩从零),验证了注册赠送与 ELO 起步 1000。

## 结论

origin 吸收第二批(局间过场卡/游戏列表屏/STATISTICS 淡入/热配局数)+ opponent-ready 二段态,与既有全部功能(排位/好友房/弃赛/断线看门狗/即时战绩/History/TOP)在双机真人对局下全链路回归通过,无回归缺陷,未发现新 bug。可继续收敛的仅剩:首跑 5 页教程 pager、每游戏局前教程、真实头像、系统通知(均有外部依赖或独立决策)。
