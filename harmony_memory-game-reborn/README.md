# 脑力训练大师 · HarmonyOS 手机 App

以本仓库 Android 客户端为玩法与素材来源的纯本地 ArkTS / ArkUI Stage 手机应用，需要安装并从桌面启动，不再是免安装元服务。需求对应见 [Harmony_README.md](Harmony_README.md)。

## 已实现

- 首页即冲刺列表，保持 Android 的 23 款游戏顺序与六大分类，无底部导航。
- 23 款独立玩法：位置记忆、六边形、增量识别、顺序路径、旋转、移动跟踪、图像位置、数量、形色查找、算术、比较、排序、同异判断、纸飞机方向、248 行列循环连线、金字塔投影、对称、镜子激光及舒尔特。
- 63 页原版教程图文、逐格入场、记忆展示、旋转/洗牌、正确/错误反馈、结算与四类原始音效。记忆1/2/3/5/6/8/9按Android执行800ms扇形准备、700ms入场，路径另含500ms白圆等待；其他游戏不额外插入倒计时。
- 2026-10-18视觉方向修订：图片不为包体降低分辨率；音效为控制包体转为单声道MP3 VBR q7（不再保留原始WAV/320kbps MP3）。首页、教学、游戏顶栏、棋盘、选择卡和白卡弹窗以本仓库当前Android源码及实际截图为基准，不再使用自设计卡片列表。
- 暂停次数不限；暂停、切后台、重看教学时冻结计时。后台保存当前局检查点，进程被回收后将该局纳入历史，重启不恢复进行中的棋盘。
- 本机保存最高到达关卡、最高得分、最近七局、教程状态和音效偏好。适用游戏按最近七局平均关卡减五自适应起始，其余从第一关开始。
- 对齐 Android 正式版单机金币解锁：首次 60 金币、13 款默认开放，游玩赚币后永久解锁其余游戏；金字塔保留 0 金币确认解锁。无网络权限、账号、服务器、HTTP、WebSocket、充值或广告，不接入在线钱包。

## 单机金币与解锁

对齐仓库 Android 正式版的单机规则，不采用 Debug 自动全解锁。首次建立 `memory_training_v2` 存档赠送 60 金币；重启不重复赠送。按本次需求不读取、不迁移或删除旧 `memory_training_v1` 存档，成绩、教程状态、偏好与金币从新存档开始。

- 默认开放 13 款：记忆网格、旋转网格、六边形、谁是新来的、数一数、跟随路径、图像漩涡、正确吗？、全部相同、纸飞机、和之前一样吗？、颜色、对称。
- 解锁价格：抓住它们 150；找到图片、激光、舒尔特方格各 300；独一无二 400；多与少 500；数字排序、找出全部各 600；248 为 1000；金字塔为 0，但仍须确认解锁。
- 一般游戏完成第 1–3 关不发币，第 4–10 关每关 1、第 11–14 关每关 2、第 15 关起每关 3。谁是新来的、纸飞机、和之前一样吗？、颜色从第 4 关起每次成功固定 1。
- 舒尔特每正确找到 3 个数字发 1 金币，完整 48 个数字共 16；248 根据本局累计分数跨阈值入账，1024/1536/2048/2560/3072 分累计为 1/3/6/10/15，之后每增加 512 分累计多 5。
- 奖励在实际成功或收取连线得分时保存，不依赖最后结算；错误、暂停、重开、返回、后台恢复和重复回调不额外发奖，已经获得的金币不因免费重开收回。
- 点击锁定游戏显示价格、余额及不足差额，余额不足不能确认。购买成功后只刷新余额和卡片，留在列表，再次点击进入；重复点击不重复扣款。余额与永久解锁状态一起落盘后才宣告成功，保存失败可重试。
- 不增加金币续关、充值、商店、广告、在线钱包或每日奖励，继续免费重开与无限暂停；结算页保持 Android 现有离线样式，不额外增加奖励明细。

## 工程配置

| 项目 | 配置 |
| --- | --- |
| 应用名 | 脑力训练大师 |
| bundleName | `com.razor.memory.brain.training.games` |
| 包类型 | `app`，`installationFree: false`，`deliveryWithInstall: true` |
| 设备类型 | `phone`，保留桌面 `EntryAbility` 入口 |
| 最低版本 | HarmonyOS `5.1.1(19)` |
| 编译 / 目标版本 | HarmonyOS `6.1.1(24)` |
| 开发工具 | DevEco Studio 6.1.1，内置 Node / Hvigor / SDK |

使用 DevEco Studio 打开**本目录**，不要将 Android 根工程作为鸿蒙工程导入。命令行脚本默认使用 `C:/Program Files/Huawei/DevEco Studio`，其他位置设置 `DEVECO_HOME`。工程没有第三方运行时依赖。

## 构建

在本目录执行：

```sh
node tools/build.mjs
node tools/build.mjs --release
python tools/check-package.py
```

输出：`entry/build/default/outputs/default/entry-default-unsigned.hap`。Debug / Release 使用相同输出路径；后一次构建会更新制品。`check-package.py` 同时校验源码与 HAP 中的普通 App 类型、包名、手机设备类型、安装属性、桌面入口、API 版本和离线权限，并报告实际 HAP 大小，**不设置大小上限**。

当前不包含个人签名材料，输出是**未签名 HAP**。支持未签名安装的开发模拟器可用于调试；真实设备与正式分发需在 DevEco 的 Signing Configs 配置该 bundleName 对应的开发/发布签名。不要将证书私钥、密码或本机签名配置提交进仓库。

### 元服务迁移（2026-10-24）

- 修改前的完整仓库提交 `46aa3f1` 已备份到本地分支 `backup/main-atomic-service-20261024-46aa3f1`，`main` 改为手机 App；未推送远端。
- 包名从 `com.atomicservice.6917619386167723098` 改为 `com.razor.memory.brain.training.games`。这是独立应用身份和数据沙箱，旧元服务的最高关卡、历史和偏好**不会自动继承**；迁移操作不卸载旧元服务，也不清除其数据。
- 移除根页面为元服务胶囊保留的 36vp 顶部空白。保留普通窗口的系统安全区、Stage UIAbility 生命周期、桌面入口及后台暂停/存档逻辑。
- 面向手机，不再声明 `tablet`。玩法、高清图标、资源、离线零权限策略及 API 19–24 范围不变。
- `device-smoke.py` 从应用配置读取包名；暂停按钮和 `device-play.py` 的棋盘取点改用可见无障碍节点，不再依赖旧胶囊高度下的固定坐标。迁移后设备脚本尚未运行。

## 自动测试

```sh
python -m unittest discover -s tools/tests -p "test_app_*.py"
node tools/test-core.cjs
node tools/tests/memory-games.test.mjs
node tools/tests/memory-board.test.mjs
node tools/tests/choice-board.test.cjs
node tools/tests/quick-games.test.cjs
node tools/tests/quick-board.test.cjs
node tools/tests/spatial.test.cjs
node tools/tests/spatial-arkts.cjs
node tools/tests/session.test.cjs
node tools/tests/economy.test.cjs
node tools/tests/android-unlock.test.cjs
python -m unittest discover -s tools/tests -p "test_coin_resources.py"
python tools/prepare_resources.py --ui-bitmap ic_coin --check
python tools/prepare_resources.py --check
python tools/check-package.py
```

纯逻辑测试直接转换项目 `.ets`，检查题目生成、正确/错误判定、边界、旋转/洗牌坐标、排序、48 数字终点、激光有解、248 精确计分、绘制与触摸坐标和本地记录。TypeScript 检查不能替代 ArkTS 构建；`build.mjs` 另外调用真实 Hvigor / ArkTS 编译器。

已有运行中的鸿蒙模拟器并安装本应用后，可运行：

```sh
python tools/device-smoke.py                 # 13 款默认开放游戏
python tools/device-smoke.py --economy       # 需要全新经济存档；检查 60 金币、余额不足、取消、0 金币金字塔解锁
python tools/device-smoke.py --all-unlocked  # 需要已合法解锁全部游戏的测试存档；不自动赠币或解锁
```

该脚本不清除应用数据；经济检查会确认免费解锁金字塔。玩法检查通过真实点击进入游戏、翻教学、暂停/继续、结算与返回，首款游戏额外暂停超过五次。结果及截图位于 `entry/build/device-smoke/`，仍保留历史 1320×2856 模拟器的滚动坐标假设。未解锁项不会被脚本绕过。它是冒烟检查，不代表全部关卡都已在设备端人工通关。

## 资源与包体

游戏图片与声音来自 `../android_memory-game-reborn/src/main/res`；应用图标使用本工程的高清母版，不再从 Android 的 192px 图标放大。

### 应用图标

- 母版：[assets/app-icon-1024.png](assets/app-icon-1024.png)，1024×1024、不透明 RGB PNG，蓝底几何人头设计；不预裁圆角，由系统处理图标遮罩。
- `AppScope/resources/base/media/game_icon.png` 与 `entry/src/main/resources/base/media/app_icon.png` 均由同一母版复制，应用、Ability 和启动窗口保持一致；Android 图标不受影响。源码资源保持1024×1024，当前SDK在HAP中将图标转换为512×512，这是打包器行为，商店母版不受影响。
- 重新绘制、同步和独立校验（仅依赖 Pillow）：

  ```sh
  python tools/render_app_icon.py
  python tools/prepare_resources.py --icons-only
  python tools/prepare_resources.py --icons-only --check
  python -m unittest discover -s tools/tests -p "test_app_icon.py"
  ```

### 游戏资源


- 181张游戏图片中的180张（教程图63、缩略图23、icg图标94）以及75张UI原PNG（含原版金币图标）全部转为无损WebP，仅保留app_icon为PNG；29个原矢量SVG，总285个media。所有WebP保持Android原始像素尺寸且逐像素一致。
- 四个音效统一转为44.1kHz单声道MP3 VBR q7（LAME），保留原始时长，合计19,197字节；四套Roboto字体按ArkTS实际字符集subset为TTF，保留原字形与排版特性。
- 当前资源合计1,559,382字节（含AppScope图标27,721字节），其中Entry图片1,447,464、音效19,197、字体65,000；图片未降采样且保持无损，音效为有损压缩。
- 已移除Harmony端未使用的7张Android评分弹窗狮子图（198,059字节）；Android drawable JSON/XML参考文件不再打包；奖杯图已转为无损WebP保留，待图形奖杯界面使用。
- 每个资源的源路径、尺寸、编码、大小与哈希记录在 `resources-manifest.json`。
- `tools/prepare_resources.py` 可重复生成WebP/SVG/字体子集/MP3，需Python Pillow + fontTools + ffmpeg（`python -m pip install pillow fonttools`，并确保`ffmpeg`在PATH）。
- 金币图标可单独重复同步：`python tools/prepare_resources.py --ui-bitmap ic_coin`；加 `--check` 只验证，不运行完整资源生成或改写其他资产。
- HAP的最终大小以实际构建产物为准，而不是图片合计；当前金币解锁版 Release 未签名 HAP 为 1,972,340 字节，Debug 未签名 HAP 为 2,601,160 字节。未配置签名，未生成 signed HAP。

## 与 Android 有意不同的边界

1. 删除金币续关，消耗两次机会后显示原风格失败白卡，可免费重新开始或结束查看成绩；不限暂停次数。
2. 舒尔特始终保留同一张 6×8 棋盘，错误不会洗表或重置已点击进度；完成数字 48 即成功结算，不等待不存在的 49。
3. 修复高关卡资源耗尽、棋盘容量溢出、随机生成死循环。普通难度遵循 Android 分段，高于原版容量的题目采用有界扩容或上限。
4. 暂停恢复剩余时间，不重置完整阶段时长。
5. 字形使用 Canvas 路径，不依赖系统 emoji；所有计时由一个可冻结的时钟统一驱动。

## 代码入口

- `entry/src/main/ets/pages/Index.ets`：首页、教程、训练流程、暂停与结算。
- `core/GameCatalog.ets`：23 款目录、原版教程、静态资源映射。
- `core/Economy.ets`：默认开放、永久解锁价格、各游戏通关与248得分金币规则。
- `core/MemoryGames.ets` / `QuickGames.ets` / `SpatialGames.ets`：纯玩法逻辑。
- `components/MemoryBoard.ets` / `QuickBoard.ets` / `SpatialBoard.ets`：按Android几何、配色与原素材绘制的专用棋盘。
- `components/AndroidScreens.ets` / `AndroidChoiceBoard.ets` / `ReadyIndicator.ets`：首页、教学、白卡弹窗、算术叠层卡、判断按钮、计数题卡和800ms准备扇形。
- `services/ProgressStore.ets` / `SoundService.ets`：本地记录与音效。

## 验证范围

当前金币解锁版：经济/持久化 28 项、首页/解锁 UI 与官方 SDK 检查 6 项、会话流程 22 项、Python 图标/配置/金币资源 14 项均通过，Debug / Release 真实 ArkTS 构建及包校验通过。完整结果见[金币解锁验收记录](docs/coins-unlock-validation.md)。当前无连接的鸿蒙设备，未执行金币界面与购买的设备冒烟；QuickBoard 调色板旧断言及完整资源重现缺少 XML 的两项基线问题仍存在。

以下为手机 App 迁移时的历史基线，不替代金币功能验收：

2026-10-24 手机 App 的 Debug / Release 均已通过本机 API 24 SDK 的真实 Hvigor / ArkTS 构建及包属性校验；应用配置与图标的 11 项 Python 测试、核心 1834 场景、MemoryGames 30882 断言、MemoryBoard 331498 断言，以及 Choice、QuickGames、Spatial、Session 专项均通过。QuickBoard 的 17 项中 16 项通过，1 项失败：既有测试要求 Android `Game23Colors` 使用 `nextInt(256)`，当前 Android 源码已采用固定调色板；本次不修改玩法或 Android 代码。完整资源重现检查也因 Android 缺少 `background_game12_white` XML 未通过，图标独立校验通过。详情见[手机 App 迁移验收记录](docs/phone-app-migration-2026-10-24.md)。

本次 `hdc list targets` 无设备，未进行迁移后的安装、桌面启动或 23 款设备冒烟。此前元服务的模拟器运行、23 款入口/暂停/结算/返回冒烟和代表性截图仅属于历史证据，见[视觉还原记录](docs/android-visual-restoration-2026-10-18.md)，不能替代 App 的设备验收。CJK 回退字体、抗锯齿和随机题目仍有平台差异，未声称全画面逐像素一致。兼容 API 19 的配置及 API 使用由编译器检查，尚未在 API 19 真机运行；正式签名、商店审核与真实设备的声音/触摸体验未验证。
