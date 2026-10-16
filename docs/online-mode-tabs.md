# 在线模式标签页与独立战绩

## 页面结构

“在线”保留与“冲刺”共用的金币、奖杯顶栏，内容区仅包含三个模式标签：

1. 排位赛
2. 练习场
3. 与好友对战

标签与 support-v4 `ViewPager` 双向联动，支持点击与左右滑动。每个模式页独立纵向滚动，展示本模式战绩与对应操作。原大厅介绍、个人资料、混合总战绩、账号入口和通用规则卡不再占用在线内容区；账号与个人资料仍可从“我的”进入。

标签选择不会随资料刷新而重置，离开在线页再返回、Activity 重建后也会恢复。普通宽度下三个标签等宽显示；窄屏或大字体下可横向滚动标签，战绩按可用宽度换行，不缩小字体隐藏数字。

## 统计口径

- 数据来源是服务端持久化的全部有效 `MatchRecord`，不截取最近 20 条对局历史。
- 以记录中的 `mode` 区分 `RANKED`、`PRACTICE`、`FRIEND`，不以对手是否机器人区分。
- 排位匹配的机器人兜底仍计入排位赛；明确发起的人机练习计入练习场；房间码对局计入好友对战。
- 玩家 B 的胜负结果按自身视角反转；平局正常计入；作废对局、未知模式不归入任何已知模式。
- 胜率 = 胜场 ÷（胜场 + 负场 + 平场）× 100%，四舍五入为整数。已确认无对局时为 0%。
- 沿用当前历史累计口径，不额外引入赛季过滤，不改变 ELO、金币、练习奖励或连胜规则。

按记录实时汇总无需新增数据库迁移或历史回填任务。能够归类的历史范围取决于数据库实际保留的对局记录，混合总数不能用于猜测缺失的分模式历史。

## 增量协议

WebSocket `auth_ok`、`match_end` 与 REST 用户资料新增可选 `modeStats`：

```json
{
  "modeStats": {
    "RANKED": {"wins": 8, "losses": 1, "draws": 1},
    "PRACTICE": {"wins": 2, "losses": 6, "draws": 2},
    "FRIEND": {"wins": 1, "losses": 2, "draws": 1}
  }
}
```

`match_end` 同时新增 `mode`。三个统计字段均为 64 位计数。原 `wins/losses/draws`、`myWins/myLosses/myDraws` 等总战绩字段及其含义保持不变，旧客户端仍可读取。

结算在事务内持久化对局并读取分模式快照，重放结算也返回完整快照。客户端只替换服务端快照，不按结果自行累加，避免重复帧或重连重复计数。

**兼容性边界：** 未返回 `modeStats` 的旧服务端或旧缓存表示“缺少独立统计”，页面显示“—”及暂不可用提示，而不是把混合战绩当作排位战绩或伪造为 0。真实分模式数据需要配套更新服务端；仅安装新客户端不会让旧服务端获得该能力。

## 验证入口

```bash
JAVA_HOME=F:/jdk17 ./android_memory-game-reborn/gradlew \
  -p android_memory-game-reborn testDebugUnitTest assembleDebug --offline

JAVA_HOME=F:/jdk17 ./server_memory-game-reborn/gradlew \
  -p server_memory-game-reborn test bootJar --offline
```

设备 UI 回归使用隔离的 `.accounttest` 调试包，不覆盖正常包，也不发起真实排队、房间、对局、购买、登录或日志上传：

```bash
JAVA_HOME=F:/jdk17 ./android_memory-game-reborn/gradlew \
  -p android_memory-game-reborn -PaccountUiTest=true assembleDebug --offline

python android_memory-game-reborn/tools/smoke-online-lobby.py \
  --adb E:/AndroidSdk/platform-tools/adb.exe --serial <设备序列号>
```

回归包含分模式示例数据、点击与滑动切页、资料刷新与标签恢复、Activity 重建、底部导航返回、旧协议、空战绩、中英文本、窄屏大字体及纵向滚动。页面截图、UI 树和结果写入 `android_memory-game-reborn/build/online-ui-evidence/`。

PostgreSQL 并发测试另用 `pgTest`，需要 Docker；不能用 H2 测试结果替代 PostgreSQL 验证。
