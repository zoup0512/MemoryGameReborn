# 脑力训练大师 · HarmonyOS 手机 App

以本仓库 Android 客户端为玩法与素材来源的纯本地 ArkTS / ArkUI Stage 手机应用，需要安装并从桌面启动，不再是免安装元服务。需求对应见 [Harmony_README.md](Harmony_README.md)。

## 已实现

- 首页即冲刺列表，保持 Android 的 23 款游戏顺序与六大分类，无底部导航。
- 23 款独立玩法：位置记忆、六边形、增量识别、顺序路径、旋转、移动跟踪、图像位置、数量、形色查找、算术、比较、排序、同异判断、纸飞机方向、248 行列循环连线、金字塔投影、对称、镜子激光及舒尔特。
- 63 页原版教程图文、逐格入场、记忆展示、旋转/洗牌、正确/错误反馈、结算与四类原始音效。记忆1/2/3/5/6/8/9按Android执行800ms扇形准备、700ms入场，路径另含500ms白圆等待；其他游戏不额外插入倒计时。
- 2026-10-18视觉方向修订：图片不为包体降低分辨率；音效为控制包体转为单声道MP3 VBR q7（不再保留原始WAV/320kbps MP3）。首页、教学、游戏顶栏、棋盘、选择卡和白卡弹窗以本仓库当前Android源码及实际截图为基准，不再使用自设计卡片列表。
- 暂停次数不限；暂停、切后台、重看教学时冻结计时。后台保存当前局检查点，进程被回收后将该局纳入历史，重启不恢复进行中的棋盘。
- 本机保存最高到达关卡、最高得分、最近七局、教程状态和音效偏好。适用游戏按最近七局平均关卡减五自适应起始，其余从第一关开始。
- 无网络权限、账号、服务器、HTTP、WebSocket、金币、购买或广告，全部游戏默认开放。

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
python tools/prepare_resources.py --check
python tools/check-package.py
```

纯逻辑测试直接转换项目 `.ets`，检查题目生成、正确/错误判定、边界、旋转/洗牌坐标、排序、48 数字终点、激光有解、248 精确计分、绘制与触摸坐标和本地记录。TypeScript 检查不能替代 ArkTS 构建；`build.mjs` 另外调用真实 Hvigor / ArkTS 编译器。

已有运行中的鸿蒙模拟器并安装本应用后，可运行：

```sh
python tools/device-smoke.py
```

该脚本不清除应用数据，通过真实点击逐个进入游戏、翻教学、暂停/继续、结算与返回；首款游戏额外暂停超过五次。结果及截图位于 `entry/build/device-smoke/`。它是冒烟检查，不代表全部关卡都已在设备端人工通关。

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


- 181张游戏图片中的180张（教程图63、缩略图23、icg图标94）以及74张UI原PNG全部转为无损WebP，仅保留app_icon为PNG；29个原矢量SVG，总284个media。所有WebP保持Android原始像素尺寸且逐像素一致。
- 四个音效统一转为44.1kHz单声道MP3 VBR q7（LAME），保留原始时长，合计19,197字节；四套Roboto字体按ArkTS实际字符集subset为TTF，保留原字形与排版特性。
- 当前资源合计1,556,874字节（含AppScope图标27,721字节），其中Entry图片1,444,956、音效19,197、字体65,000；图片未降采样且保持无损，音效为有损压缩。
- 已移除Harmony端未使用的7张Android评分弹窗狮子图（198,059字节）；Android drawable JSON/XML参考文件不再打包；奖杯图已转为无损WebP保留，待图形奖杯界面使用。
- 每个资源的源路径、尺寸、编码、大小与哈希记录在 `resources-manifest.json`。
- `tools/prepare_resources.py` 可重复生成WebP/SVG/字体子集/MP3，需Python Pillow + fontTools + ffmpeg（`python -m pip install pillow fonttools`，并确保`ffmpeg`在PATH）。
- HAP的最终大小以实际构建产物为准，而不是图片合计；2026-10-24 手机 App Release 未签名 HAP 为 1,937,670 字节，Debug 未签名 HAP 为 2,520,736 字节。本次未配置签名，未生成 signed HAP。

## 与 Android 有意不同的边界

1. 删除金币续关，消耗两次机会后显示原风格失败白卡，可免费重新开始或结束查看成绩；不限暂停次数。
2. 舒尔特始终保留同一张 6×8 棋盘，错误不会洗表或重置已点击进度；完成数字 48 即成功结算，不等待不存在的 49。
3. 修复高关卡资源耗尽、棋盘容量溢出、随机生成死循环。普通难度遵循 Android 分段，高于原版容量的题目采用有界扩容或上限。
4. 暂停恢复剩余时间，不重置完整阶段时长。
5. 字形使用 Canvas 路径，不依赖系统 emoji；所有计时由一个可冻结的时钟统一驱动。

## 代码入口

- `entry/src/main/ets/pages/Index.ets`：首页、教程、训练流程、暂停与结算。
- `core/GameCatalog.ets`：23 款目录、原版教程、静态资源映射。
- `core/MemoryGames.ets` / `QuickGames.ets` / `SpatialGames.ets`：纯玩法逻辑。
- `components/MemoryBoard.ets` / `QuickBoard.ets` / `SpatialBoard.ets`：按Android几何、配色与原素材绘制的专用棋盘。
- `components/AndroidScreens.ets` / `AndroidChoiceBoard.ets` / `ReadyIndicator.ets`：首页、教学、白卡弹窗、算术叠层卡、判断按钮、计数题卡和800ms准备扇形。
- `services/ProgressStore.ets` / `SoundService.ets`：本地记录与音效。

## 验证范围

2026-10-24 手机 App 的 Debug / Release 均已通过本机 API 24 SDK 的真实 Hvigor / ArkTS 构建及包属性校验；应用配置与图标的 11 项 Python 测试、核心 1834 场景、MemoryGames 30882 断言、MemoryBoard 331498 断言，以及 Choice、QuickGames、Spatial、Session 专项均通过。QuickBoard 的 17 项中 16 项通过，1 项失败：既有测试要求 Android `Game23Colors` 使用 `nextInt(256)`，当前 Android 源码已采用固定调色板；本次不修改玩法或 Android 代码。完整资源重现检查也因 Android 缺少 `background_game12_white` XML 未通过，图标独立校验通过。详情见[手机 App 迁移验收记录](docs/phone-app-migration-2026-10-24.md)。

本次 `hdc list targets` 无设备，未进行迁移后的安装、桌面启动或 23 款设备冒烟。此前元服务的模拟器运行、23 款入口/暂停/结算/返回冒烟和代表性截图仅属于历史证据，见[视觉还原记录](docs/android-visual-restoration-2026-10-18.md)，不能替代 App 的设备验收。CJK 回退字体、抗锯齿和随机题目仍有平台差异，未声称全画面逐像素一致。兼容 API 19 的配置及 API 使用由编译器检查，尚未在 API 19 真机运行；正式签名、商店审核与真实设备的声音/触摸体验未验证。
