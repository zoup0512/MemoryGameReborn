# 单机金币与永久解锁验收

## 需求范围

本次以仓库 Android 正式版的离线 Sprint 为规则来源，用户确认：

- 只对齐赚币及永久解锁，不加入金币续关，保留免费重开和无限暂停。
- Harmony 不考虑旧档兼容，使用新的 `memory_training_v2` Preferences；不读取、迁移或删除 `memory_training_v1`。
- 金字塔保留“初始有锁、0 金币确认后永久开放”。
- 不增加在线钱包、服务端、充值、广告、签到、VIP 或 Debug 全解锁。

## 规则与实现位置

- `core/Economy.ets`：稳定游戏 ID、默认开放、价格、关卡奖励和 248 得分累计金币函数。
- `services/ProgressStore.ets`：首次 60 金币、余额、永久解锁、串行持久化和读写错误处理。
- `pages/Index.ets`：真实成功事件奖励、舒尔特逐数字奖励、248 收取连线得分奖励、全部启动入口校验及购买状态。
- `components/AndroidScreens.ets`：顶部余额、锁定遮罩、价格、确认和余额不足弹窗；购买后留在列表，再次点击进入。
- `tools/prepare_resources.py --ui-bitmap ic_coin`：从仓库 Android xxxhdpi 128×128 PNG 单独同步原版金币图标，不重新生成其他游戏资源。

### 默认开放与价格

默认开放 13 款：记忆网格、旋转网格、六边形、谁是新来的、数一数、跟随路径、图像漩涡、正确吗？、全部相同、纸飞机、和之前一样吗？、颜色、对称。

| 游戏 | Harmony ID | 金币 |
| --- | --- | --- |
| 抓住它们 | 8 | 150 |
| 找到图片 | 9 | 300 |
| 独一无二 | 10 | 400 |
| 多与少 | 12 | 500 |
| 数字排序 | 14 | 600 |
| 248 | 17 | 1000 |
| 金字塔 | 18 | 0，仍需确认 |
| 找出全部 | 19 | 600 |
| 激光 | 22 | 300 |
| 舒尔特方格 | 23 | 300 |

Harmony ID 是 Android ID + 1，不是首页数组下标。零价格不等于默认开放。

### 奖励事件

- 一般游戏成功第 1–3 关为 0、第 4–10 关为 1、第 11–14 关为 2、第 15 关起为 3。
- 谁是新来的、纸飞机、和之前一样吗？、颜色在第 4 关后每次成功固定 1。
- 舒尔特成功找到编号 3、6、9……48 时各 1；整盘共 16。错误、已点过的数字均不奖励。
- 248 只按收取连线后累计分数对应金币的差值奖励。1024/1536/2048/2560/3072 分累计为 1/3/6/10/15，此后每增加 512 分多 5，跨多个阈值一次补齐。
- 不把 `finish()`、保存历史、后台恢复或通用成功反馈本身作为金币入口，避免放弃、重开、重复回调及自动教学样本发奖。
- 允许重复游玩赚币；免费重开不收回已赚金币。

## 验证方法与结果

自动验证执行真实 ETS 方法，经本机 TypeScript/官方 SDK 检查；不是复制一份平行经济实现。真实 ArkTS 构建用于最终语义验证。

| 检查 | 结果 |
| --- | --- |
| 初始钱包、价格、奖励公式、永久购买、并发及共享缓存回滚 | `tools/tests/economy.test.cjs` 28 项通过 |
| 首页锁态、余额不足、0 币确认、忙碌与重试、官方 SDK 语义检查 | 6 项通过 |
| Index 真实会话、奖励与购买流程 | 22 项通过 |
| Python 配置/图标/金币资源检查 | 14 项通过 |
| 核心生成、解法、边界与本地记录 | 1834 场景通过 |
| MemoryGames | 30882 断言通过 |
| MemoryBoard | 331498 断言通过 |
| ChoiceBoard | 18 项通过，含官方 SDK 检查 |
| QuickGames | 11 项通过，无跳过 |
| QuickBoard | 16 项通过、1 项既有失败 |
| Spatial | 38 组通过，严格类型检查通过 |
| Spatial ArkTS | 3 个真实 ETS 文件通过官方 SDK 语义/lint |
| 图标独立重现、金币单资源重现 | 通过 |
| 完整资源重现 | 既有缺失 XML 导致失败 |
| 修改 Python 文件语法、`git diff --check` | 通过 |

### 保存异常验收

购买与奖励、成绩使用同一有序队列；候选钱包和解锁集合一起保存。确认按钮忙碌期间禁用，Store 再检查余额和所有权，只有持久化成功后才向 UI 公布扣款及开放状态。购买失败需要恢复 Preferences 缓存，不能仅恢复页面内余额；回滚异常时阻止继续购买并提供恢复重试。奖励写入失败时保留当前进程内金币，重试保存而不是再次发奖。

本地存储不是服务端钱包。持续磁盘故障、未完成刷盘即强杀或卸载应用等场景不保证未持久化奖励可恢复；界面显示保存错误，不声称金币绝不丢失。

### 构建材料

通过 `node tools/build.mjs`、`node tools/build.mjs --release` 和 `python tools/check-package.py` 验证 Debug / Release，保留 API 19–24、普通手机 App 和离线零权限。

最终 Debug 未签名 HAP 为 2,601,160 字节；Release 未签名 HAP 为 1,972,340 字节，SHA-256 为 `dadb250679e369aa22333170e0900f180b554ad2ce7fa7d07fe05591d87bef2c`。两种构建均 `BUILD SUCCESSFUL`，仅有未配置签名的预期警告。

当前输出路径：`entry/build/default/outputs/default/entry-default-unsigned.hap`。Debug / Release 共用路径，最后构建的是 Release。未配置个人签名，真机安装及正式分发仍需要对应签名。

本地报告不纳入 Git：

- `entry/build/coins-debug-build.log`、`coins-release-build.log`
- `entry/build/coins-debug-package.json`、`coins-release-package.json`
- `entry/build/coins-tests.json`、`coins-test-*.log`

## 未通过项及运行边界

1. QuickBoard 测试仍要求当前 Android `Game23Colors` 包含 `nextInt(256)`，而 Android 现采用固定调色板；这是迁移前已经复现的基线问题，本次未改玩法配色或 Android 代码。
2. 完整资源重现仍报 `Missing Android drawable XML: background_game12_white`。金币图标独立重现通过，本次未用全量资源生成覆盖现有资产。
3. 本次 `hdc list targets` 返回 `[Empty]`，未执行新金币界面的设备画面检查、购买操作或 23 款实际游玩。旧元服务截图和旧 App 迁移记录不作为本次新增界面设备验收证据。

设备脚本默认检查 13 款初始开放项；`--economy` 要求全新经济存档，验证 60 金币、余额不足、取消和金字塔 0 金币解锁，并会真实保存这项免费解锁；`--all-unlocked` 要求已合法解锁全部项目的测试存档。脚本不清空数据、不赠币、不绕过锁，也不提供生产作弊入口，仍使用历史模拟器的滚动坐标假设。
