# 多人亲友赛本地验收

## 范围与隔离

多人模式为 FRIEND_PARTY，3–6人、3/5轮，独立战绩，不计入旧双人胜率、不扣金币、不改排位分。既有双人 FRIEND/RANKED/PRACTICE 保持原协议。正式端点与正式签名包不用于本地验收，不在云服务器运行造数脚本。

协议与规则见 [friend-party-protocol.md](friend-party-protocol.md)。

## 构建与测试

使用本机 JDK 17（F:/jdk17）和各子项目 Gradle wrapper。

```bash
cd server_memory-game-reborn
JAVA_HOME=F:/jdk17 ./gradlew test bootJar
```

PostgreSQL/Testcontainers `pgTest` 需要Docker；本机无Docker时必须明确记为未验证，不以H2测试替代。

Android单测和独立测试包：

```bash
cd android_memory-game-reborn
JAVA_HOME=F:/jdk17 ANDROID_HOME=D:/AndroidSdk ./gradlew testDebugUnitTest assembleDebug \
  -PpartyUiTest=true -PonlineEndpoint=ws://127.0.0.1:18091/ws
```

`partyUiTest`仅用于隔离真机测试，包名后缀为 `.partytest`；不要安装覆盖已有正式或`.accounttest`应用，不清除其数据。

## 真实HTTP/WebSocket六人自动验收

1. 将最新bootJar复制到独立的本地验收文件，再运行该副本。不要在运行的JAR上执行重新打包。
2. 仅监听127.0.0.1:18091，使用独立内存数据库，不连接云端数据库。测试参数可设置：

```text
--server.address=127.0.0.1
--server.port=18091
--pk.auth.allow-http=true
--pk.round-duration-override-ms=3500
--pk.min-level-interval-override-ms=50
--pk.party.countdown-ms=150
--pk.party.intermission-ms=200
--pk.party.loading-ms=3000
```

`allow-http`仅本地开发使用。脚本使用新式guest HTTP认证，不依赖外部昵称审核成功。

3. 从仓库根目录运行：

```bash
python docs/tools/friend_party_smoke.py --base-url http://127.0.0.1:18091
```

脚本仅允许loopback，需`websocket-client`。创建6个一次性访客，覆盖：

- 六位码、六人持续大厅；
- 非房主配置被拒、全员准备门槛；
- 三轮加载与统一开始、服务端计分；
- 六人双并列第一每轮points2=9、零分不给积分，三轮总points2=27；
- 多人独立持久历史，余额/排位分/双人胜负不变化；
- 同房连玩与准备重置；
- 旧matchId离房不能退出新局；
- 弃赛只影响自己，剩一人整场中断；
- 离开后可立即建新房，占用已释放。

## Android真机联调

使用较长的本地正式轮时，例如15–30秒，不要沿用150ms倒计时来判断UI观感。仅为隔离测试包设置本地端点，通过设备端口转发接入。安装前确认目标包不存在或确实属于本次验收。

启动两个本地协议参赛者：

```bash
python docs/tools/friend_party_ui_peers.py --base-url http://127.0.0.1:18091 --seconds 600
```

会输出6位房码，不输出凭据。Android从正常亲友赛入口加入房码并准备；两个参赛者自动准备、开赛及加载，Android实际操作游戏。检查：

- 大厅可看到三人、房主和准备状态，规则说明明确免金币/不影响排位；
- 加载时不能操作或偷跑，统一倒计时后可以操作；
- 六款联网游戏均能成功/失败上报，关卡编号不误判；
- HUD为多人本轮排名，而非伪装成一个对手；
- 本轮成绩、总积分、最终并列与弃赛标记可区分；
- 返回原房间继续玩，不进入随机匹配；
- 显式离房、短暂断网恢复、退到后台、旧Activity回调不污染后继局；
- 历史只展示本模式，不改变原双人统计。

真机不足6台时，协议六人验收不能声称“六台真机回归”；单真机加两个协议参赛者也不能代替六款小游戏的完整手动操作验证。记录实际覆盖和未覆盖项目。

## 本次已执行（2026-10-21）

- Android：94项单元/接缝测试通过，assembleDebug、compileReleaseJavaWithJavac、assembleRelease（R8混淆/资源压缩）通过；verifyReflectionKeep通过（dex=95、arsc=94），mapping确认PartyState及Participant保持JSON字段名。其中部分回归为源码断言，不等同instrumentation。生成的release仅供构建验证，未安装正式包或自动发布，也未重新登记线上完整性锚点。
- 服务端：最终完整无Docker回归30个测试套件、185项测试通过，失败/错误/跳过均为0；新增真实六socket、Flyway V1–V9、独立历史等测试。关闭池饱和用例实际占满2个worker和256项队列，验证拒绝后重试、逻辑掉线、入站隔离与恢复后的最终清理；修复经过独立定点复核。最终bootJar副本再次通过六组真实HTTP/WebSocket验收。
- 独立JAR、127.0.0.1、H2内存库：`friend_party_smoke.py`六组验收全部通过，包括六人三轮、正分并列、零分、历史、余额/排位分与旧双人历史隔离、连玩、旧matchId、弃赛和占用释放。
- USB真机使用全新`.partytest`包，未覆盖正式和`.accounttest`应用，未清其数据；配合两个本地协议客户端连续完成两场3轮，验证正常入口、6位码加入、三人准备、游戏自动切换、实际答题8/12原始分入结果、并列积分、最终明细、返回同房再开、两条独立历史及离房。
- 真机两场游戏覆盖了与前一个相同、颜色、算式判断、数一数、位置记忆的启动/结束路径；没有完成六款每种题型与所有失败分支的手工回归，舒尔特表没有在此次随机两场中出现。不是六台真机验收。
- 未手工验证真实网络切换、强杀进程恢复、跨设备恢复及六人全真机布局；协议/单测覆盖不能替代这些测试。
- 未连接云端、未自动部署、未commit/push。

### 持久化与运行边界

FINISHED结果即时可查，历史接口会合并尚未落盘的结果；后台写入失败会重试，积压触发创建/开局背压。正常关闭会尽力写完已完成历史，数据库持续故障且进程被强杀时，尚未落盘的结果仍可能丢失。服务器重启不恢复正在进行的比赛，也不为未完成比赛生成正式冠军。

## 发布前额外门槛

- V9新增表需在PostgreSQL执行迁移验证；不要改动已发布V1–V8。
- 用发布混淆构建检查新增JSON字段保持规则与反射图片资源检查。
- 旧Android应用仍只使用双人协议；新多人客户端需要新服务端先部署。
- 本次实现不自动部署云端、不推送提交；上线需独立发布验收。
