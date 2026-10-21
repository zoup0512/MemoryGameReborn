# 多人亲友赛协议与实现契约

## 范围
Android + 服务端，新增 FRIEND_PARTY，不改变双人 FRIEND/RANKED/PRACTICE。3–6 人，同题 3/5 轮，无金币与排位分变化。大厅持续存在，结束后返回原大厅再开局。Harmony 不在本期。

## WebSocket
复用 v=1, t,d,rid 信封与现有认证连接。所有新增类型以 party_ 开头。服务端对状态变更命令返回 party_state；状态广播也用 party_state。所有命令直接回复均回传原 rid，包括 party_state/error/party_left/party_history；主动广播不需要 rid。party_left 为离房/被移除确认（d 含 lobbyId, reason；sync无房时lobbyId=null）；不存在的重复离房也确认且不能影响其他房间。

命令：
- party_create {roundCount:3|5} 默认5。
- party_join {code:string} 六位数字。
- party_config {lobbyId,roundCount:3|5} 仅房主，大厅阶段，变更清空准备。
- party_ready {lobbyId,ready:boolean} 大厅阶段。
- party_start {lobbyId} 仅房主，至少3人，所有非房主在线并准备，锁定名单。
- party_kick {lobbyId,playerId} 仅房主，大厅阶段，不能踢自己。
- party_transfer {lobbyId,playerId} 仅房主，大厅阶段，目标须为在线成员；转让清空全员准备。
- party_leave {lobbyId,matchId?} 大厅中离房；比赛中弃赛并离房。matchId 若提供必须匹配，避免旧操作影响下一场。
- party_sync {} 获取当前所在大厅快照（auth 后自动补发亦可）；无大厅回 party_left {reason:"not_in_lobby"}。
- party_round_ready {lobbyId,matchId,roundIndex} 客户端完成加载确认，必须校验所有身份字段。
- party_level {lobbyId,matchId,roundIndex,level:int,success:boolean} 服务端按 GameCatalog 重算，忽略客户端分数。success必须显式提供；成功和失败均消费真实level，严格递增（可跳关，不可重复补成功），范围1..maxLevel，并校验实际间隔与elapsed/floor+3进度上限。六款联网游戏失败均推进题目，不使用独立attempt替代真实level。
- party_complete {lobbyId,matchId,roundIndex} 本轮客户端结束。
- party_return {lobbyId,matchId} 结算阶段返回大厅（首个有效返回将大厅置回LOBBY并清准备；客户端不得因收到LOBBY便自动跳过自己未看完的结果）。
- party_history {} 返回 party_history {matches:[...]}, 每条含 matchId,ts,status,roundCount,participantCount,rank,points2,firstPlaces，独立于双人统计。

## party_state d
- lobbyId:string, code:string, hostId:long, roundCount:int, capacity:6
- stateVersion:long 单大厅单调递增；serverTime:long 当前epoch毫秒
- phase: LOBBY | LOADING | COUNTDOWN | PLAYING | ROUND_RESULT | FINISHED
- matchId:string|null, rulesVersion:1, roundIndex:int（未开赛-1）
- startAt:long, deadlineAt:long, phaseDeadlineAt:long（加载/轮间截止时间）
- status:string（正常完成COMPLETED，人数不足INTERRUPTED等）, reason:string|null
- rounds:[{gameId:string,durationMs:int,seed:long}] 使用现有Java RNG
- members:[{playerId:long,nickname:string,avatarValue:string,ready:boolean,connected:boolean}] 当前大厅成员，按加入顺序
- participants:[{playerId:long,nickname:string,avatarValue:string,connected:boolean,withdrawn:boolean,roundEligible:boolean,score:int,points2:int,firstPlaces:int,rank:int}] 本场冻结名单；score为当前轮原始分；points2为累计积分×2。LOBBY可保留上场结果直至下一场启动。
- results:[{roundIndex:int,gameId:string,entries:[{playerId:long,score:int,rank:int,points2:int,eligible:boolean}]}] 已完成轮次。

## 状态与规则
每轮 LOADING 最多15秒，全体活跃玩家ready则提前进入COUNTDOWN；至少2人加载完成后统一3秒倒计时，否则本场INTERRUPTED。仅已加载者当轮可得分，不允许超时玩家插入本轮。COUNTDOWN开始正式计时前不接收得分。PLAYING至deadline或全部有资格玩家complete结束，支持合理网络容差但不可额外生成游戏时间。ROUND_RESULT约6秒，然后下一轮。最后FINISHED。

开局人数N固定，名次r基础积分N-r；同分平分占用名次积分，用points2精确存整数。原始分0或本轮未加载/弃赛无积分；仅有资格者按分排序，弃赛者不抢占正常玩家名次。firstPlaces仅在本轮有资格、score>0且rank=1时加一（并列第一每人加一，零分并列不加）。总分按points2降序再firstPlaces降序，仍相同并列；弃赛单列末尾。仅剩一个未弃赛者提前INTERRUPTED，无正式冠军。退出当前未完成轮不计分，保留已完成轮成绩。

大厅房主离开转给最早在线成员；掉线保留席位90秒，超过后移除/弃赛；大厅短暂掉线30秒可转让房主。比赛掉线不中止时间，重连恢复权威快照。服务端重启不恢复内存比赛，不生成正式战绩。房间空时删除，空闲房间30分钟失效（比赛中不失效），加入需限流，容量及所有状态变更原子化。PlayerLifecycle用独立PARTY_LOBBY占用，只有离房/超时移除释放，旧任务必须不能释放后继占用。

## Android接入原则
OnlineSession继续拥有唯一认证连接，通过新增PartySession接收/发送party消息，不注册第二条同账户socket。多人Activity接管大厅/加载/轮间/结算；复用六个游戏Activity与EXTRA_ONLINE_GAME/SEED/ROUND，OnlineSession的游戏报告、轮次时钟、退出与HUD方法在多人赛期间转发PartySession，不触发双人结算/扣币。逐轮加载时先显示游戏完成初始化并发送ready，再按统一时间倒计时，不能等PLAYING后才开始加载。回到前台/重连同步，旧round/match回调不影响新局；不可靠的杀进程当前轮恢复禁用重刷，可看排行并等下一轮。所有关键页面滚动兼容六人，明确本轮原始分与累计积分。多人历史单独入口。新文案放入资源。
