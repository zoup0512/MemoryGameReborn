# Count'em All release-only crash fix (2026-10-15)

线上用户报告“进入第 N 关有一方自动退出到‘准备好下一场对局了吗’页面”。
2026-10-15 在真机上完整复现并定位：这是 release 构建特有的 **R8 混淆破坏反射**导致的应用崩溃，
与“第几关”无关——崩溃只取决于该回合抽到的游戏是不是 Count'em All（`gameId "4"`）。

## 复现证据

第一局人机对战（云服 match `6f7974abae2a`，PRACTICE 5 回合）：

- 09:46:12 服务端 `ROUND_START round=4 game=4`。
- 09:46:17 手机端 `Game5CountAllActivity` 启动即 FATAL EXCEPTION；进程被系统终止，
  WebSocket 断开（服务端记录 `WS_TRANSPORT_ERROR ... EOFException` + `closeCode=1006`）。
- 应用被系统重启后回到在线大厅（用户看到的“自动退出”）。服务端因画面已无人操作，
  第 4、5 回合按 deadline 结束并正常结算。

logcat 崩溃行（R8 混淆，行号经 mapping.txt 反查）：

```
java.lang.RuntimeException: Unable to start activity Game5CountAllActivity:
  java.lang.IllegalArgumentException: bound must be positive
    at java.util.Random.nextInt(Random.java:388)
    at Game5CountAllActivity.mS(SourceFile:172)   // = startLevel() 第 261 行
    at Game1MemoryGridActivity.hv(SourceFile:140)
    at Game1MemoryGridActivity.onCreate(SourceFile:530)
```

## 根因

`MemoryApplicationModel.getAllCellDrawableIds()` 用反射枚举 `R.drawable` 中名字含 `"icg_"`
的字段来收集格子图片。签名 release 构建里 R8 把这些字段名全部重命名（改名后不再含
`"icg_"`），反射列表变为空；`Game5CountAllActivity.startLevel()` 随即执行
`GameRandom.nextInt(allCellDrawableIds.size() - 1)` = `nextInt(-1)` 并抛异常。

同一代码的 debug 构建（不混淆）字段名保留，因此历来单机与回归（回归套件使用 debug APK）
都看不出问题——故障只在 release 包出现，且只发生在 Count'em All 这一款游戏上。

## 修复（4 处）

1. `android_memory-game-reborn/proguard-rules.pro`：`-keepclassmembers R$drawable` 保留
   反射读取的字段名（构建后 dex 中 `icg_` 出现数从 1 恢复到 95，与 debug 一致）。
2. `Game5CountAllActivity.startLevel()`：防御空/单元素列表——不再对非正数 bound 调
   `nextInt`，列表为空时退回默认格子样式而非崩溃。
3. `android_memory-game-reborn/src/main/res/raw/keep.xml`：`tools:keep="@drawable/icg_*"`
   防止资源收缩器删除这些图片。
4. `build.gradle` 新增 `verifyReflectionKeep` 校验：`assembleRelease` 结束后检查签名 APK，
   若 dex 中反射字段名（`< 50`）或资源表（`< 50`）异常则让构建失败，防止同类回归再次上线。

## 验证（真机 + 云服）

- 构建校验：`verifyReflectionKeep: ... dex=95 arsc=94` 通过。
- 单机：进入“数一数”第 1 关，进程 PID 不变，正常开局（修复前该入口必崩）。
- 人机对战第二局（match `d8b0367e64d4`，5 回合，第 3 回合恰为 Count'em All、第 5 回合为
  Correctly?）：五个回合全部正常完成，客户端全程在线（PID 不变），正常结算
  （`SETTLED ... forfeit=false scores=0/588`），期间无任何断线或崩溃记录。

## 影响与遗留

- 受影响的只有 release（R8 混淆）构建的客户端；服务端无需改动。
- 该 bug 与线上用户报告的“进入某关自动退出”一致：崩溃回合=抽到 Count'em All 的回合，
  因此看起来像“固定发生在第 N 关”。
- 同族隐患（其它依赖反射字段名/资源名的代码）目前无；如需新增反射用法，请同时更新
  `verifyReflectionKeep` 覆盖范围。
