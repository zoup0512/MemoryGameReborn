# Android Dialog 统一改造与验收（2026-10-15）

## 范围

在 `feat/android-dialog-design` 分支统一当前实际使用的 Dialog。保留 support-v7 26.1.0、Java 8 和 ButterKnife；没有迁移 AndroidX、改服务器协议、金币价格或分数规则。`383246b` 的 release 反射资源修复保持不变。未提交或推送。

- 公共 `ui/DialogUi`、显式 AlertDialog 主题、不透明圆角卡片、滚动和宽高约束、52dp 主按钮、主次/禁用状态及普通文字对比度。
- 在线退出集中到游戏基类，16 个子类保留自己的离线返回与保存逻辑；外层对局和游戏内部一致区分练习与对战后果。
- 暂停、失败续关、解锁、结束、挑战开始/结果、奖杯、评分、训练邀请/帮助同步改造；已存在 API/平板变体同步。
- 失败不再 5 秒自动退出；明确续关金额、余额、次数和不可用原因。解锁显示成本并复核余额和已解锁状态。
- 结束优先本局到达关卡、历史最佳与主要操作；历史最佳读取到达关卡统计，不复用奖杯的已完成关卡口径。
- 挑战结果显示真实三星目标和现有 50%/75%/100% 星级规则，不改变判定。
- 房码复制、创建时间基准倒计时、真实 Button 和可变高度难度行。
- 账号永久标签、明确动作、验证码顺序、友好错误和技术码分层；保留敏感字段不保存与请求中提交/取消防重入。
- 诊断上传仍只在显式确认后发生，保留完整隐私说明和快照清理；成功编号可复制。
- 修复评分对话框先绑定后加载布局；数一数答案四位置均可达、正数且无重复，增加声音入口和文字反馈。

## 构建与自动检查

在工程根执行，使用本机 JDK 17：

```bash
JAVA_HOME=F:/jdk17 PATH=/f/jdk17/bin:$PATH \
  ./android_memory-game-reborn/gradlew -p android_memory-game-reborn \
  assembleDebug assembleRelease lintDebug -PaccountUiTest=true --offline
python android_memory-game-reborn/tools/check-dialog-design.py
JAVA_HOME=F:/jdk17 PATH=/f/jdk17/bin:$PATH \
  bash android_memory-game-reborn/tools/test-count-answers.sh
JAVA_HOME=F:/jdk17 PATH=/f/jdk17/bin:$PATH \
  bash android_memory-game-reborn/tools/test-diagnostics.sh
```

结果：

- Debug 与签名 release 构建通过；release R8/资源收缩通过。`apksigner verify --verbose` 通过 v1/v2 签名验证；工具另报告两项 AGP 的 META-INF 元数据未受 JAR entry 签名保护告警。
- `verifyReflectionKeep` 通过：`dex=95`、`arsc=94`。
- 数一数生成器：323,751 项断言通过，包括四位置、小值、边界和无重复正数。
- 诊断核心：45 项断言通过。
- XML、所有弹窗布局变体 ID/类型契约、退出归并、评分绑定顺序、续关无自动计时、关键文字对比度检查通过。
- `git diff --check` 通过（DiagnosticUploadUi 有 LF/CRLF 转换提示，不是空白错误）。

`lintDebug` 任务完成，但不是无告警：报告仍有 529 个错误级诊断及 1707 个警告，包含历史反编译源码常量、旧资源缺失/命名空间等。已逐项将报告行号与本轮新增/修改行区间比对，没有错误级诊断位于本轮新增/修改行；新增文件无错误级诊断。未通过调整 `abortOnError` 或新增全局 baseline 隐藏历史错误。新增中英文文案明确使用其他语言英文回退。

最终产物：

- `android_memory-game-reborn/release/memory-game-3.6.3-74-release-20261015-1117.apk`
- `android_memory-game-reborn/build/outputs/apk/debug/memory-game-3.6.3-74-debug-20261015-1117.apk`

Debug 的 `accountUiTest=true` 使用独立 `.accounttest` 包名；release 不受该开关影响。未覆盖/卸载手机正式应用，未清空数据，未重写签名锚点。

## 真机验收

设备：已连接 CDY-AN90。MCP Android 工具返回的 adb 路径包含尾部回车，无法启动；核实实际 `E:/AndroidSdk/platform-tools/adb.exe` 存在后使用该工具做同等 ADB 采集。没有修改 SDK、系统字体、分辨率或其他系统设置。

新增仅 debug 编译的 `DebugDialogPreviewActivity` 提供隔离预览，回调不提交分数、扣费、登录或上传。

```bash
python android_memory-game-reborn/tools/smoke-dialogs.py \
  --adb E:/AndroidSdk/platform-tools/adb.exe --serial 25HUT20515012964
```

15 类预览全部通过：Pause、Retry、Challenge result、Trophy、Feedback、Workout help、Workout invitation、Practice exit、Room code、Room waiting、Difficulty、Coins、Result layout、Answer layout、Unlock layout。

- 输出 `build/dialog-ui-evidence/`：每类 PNG、UI XML 与 `results.json`。预览只证明真实组件/布局能展示及关键文字存在，不冒充真实业务全链路测试；Result/Answer/Unlock 为布局预览。
- 真实离线舒尔特游戏额外验证：暂停卡片、结束本局回调、真实结束弹窗及推荐图片；没有异常。
- 暂停返回键恢复到预览列表通过；六秒后续关仍保持决策状态通过；评分邀请构造/展示无崩溃。
- 房码及账号登录表单键盘避让可用；账号空表单仅触发本地必填校验，没有提交登录。
- 320dp / 2.0 字体使用 debug Activity 内资源配置模拟，文字重排并可滚动到底部操作；没有改全局设置。不是物理平板或系统级字体验收。
- 最后停止独立测试进程，通过公开 SplashActivity 恢复正式应用；正式 MainMenuActivity 不对 adb 导出，直接启动被系统拒绝是预期边界，未绕过。

其他人工截图在 `build/dialog-*.png`，包含真实离线暂停与结束、账号登录/必填、大字体滚动底部。

## 限制

- 没有确认真实金币支付/解锁、注销、会话撤销、验证码发送或日志上传；这些属于外部/不可逆操作，本轮保持安全边界。
- 没有新的完整在线 PK 回归、服务器 TTL 到期联调、真实账号错误响应回归、TalkBack 朗读或物理平板运行。相关逻辑进行了静态检查，不能据此声称完整业务验收通过。
- 新增文案本轮提供中英文，其他既有语言回退英文。
- Debug 预览入口在 release 中不编译；签名 release 没有安装到正式应用上。
