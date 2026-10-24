# HarmonyOS 手机 App 迁移验收（2026-10-24）

## 备份与变更范围

- 修改前工作区干净，当前分支为 `main`，基准提交为 `46aa3f1de4aea68572c3d7096a8eb76dfa3aef83`。
- 已创建本地分支 `backup/main-atomic-service-20261024-46aa3f1` 指向该提交，保留迁移前完整仓库状态；未推送远端。
- `AppScope/app.json5`：`bundleType` 从 `atomicService` 改为 `app`，包名改为 `com.razor.memory.brain.training.games`。
- `entry/src/main/module.json5`：`installationFree: false`，保留 `deliveryWithInstall: true`，设备类型收敛为 `phone`。
- 保留 Stage `EntryAbility`、`entity.system.home` / `ohos.want.action.home` 桌面入口、前后台暂停及本地记录机制，不新增网络权限或运行时依赖。
- `Index.ets`：移除根布局的 36vp 元服务胶囊占位，不添加其他固定状态栏高度。
- 同步包属性校验、设备测试脚本、两级 README 和需求对应文档；历史元服务视觉记录保留并加注适用范围。
- 新增 6 项手机 App 配置回归测试。修复 MemoryBoard 测试源码提取对 Windows CRLF 的兼容性，不改变玩法代码。

## 应用身份与存档边界

旧元服务包名为 `com.atomicservice.6917619386167723098`。新 App 使用独立包名和沙箱，不自动读取旧元服务 Preferences 中的最高关卡、历史和音效偏好。本次未卸载旧元服务，也未清除设备数据；未验证元服务到普通 App 的原位升级或跨沙箱数据迁移。

## 构建与制品

使用本机 DevEco Studio 6.1.1 内置 Node / Hvigor / SDK，通过 `node tools/build.mjs` 和 `node tools/build.mjs --release` 分别完成真实 ArkTS 编译。

| 项目 | 结果 |
| --- | --- |
| Debug | BUILD SUCCESSFUL，未签名 HAP 2,520,736 字节 |
| Release | BUILD SUCCESSFUL，未签名 HAP 1,937,670 字节 |
| 源码及 HAP 元数据 | 普通 App、phone、需安装、随应用安装、桌面入口、包名、离线权限、API 版本全部通过 |
| 最低 SDK | HarmonyOS 5.1.1 / API 19 |
| 编译 / 目标 SDK | HarmonyOS 6.1.1 / API 24 |
| 签名 | 未配置，构建提示 `No signingConfig found for product default` |

Debug / Release 使用相同输出路径，当前最后一次构建的产物是 Release：

`entry/build/default/outputs/default/entry-default-unsigned.hap`

SHA-256：`70cda412aaf6166e037078420f151a6f6e4026ed8ac6452e77987f32fff5561d`

本地生成的验证材料位于构建目录，不纳入 Git：

- `entry/build/phone-app-debug-build.log`
- `entry/build/phone-app-release-build.log`
- `entry/build/package-check.json`
- `entry/build/phone-app-baseline-quick-board.log`
- `entry/build/phone-app-baseline-resources.log`

真实设备和正式分发仍需配置新 bundleName 对应的开发/发布签名；未签名 HAP 不是可直接发布的商店发行包。

## 自动测试结果

| 检查 | 结果 |
| --- | --- |
| Python 应用配置及图标测试 | 11 项通过（App 配置 6 项，图标 5 项） |
| `tools/test-core.cjs` | 1834 场景通过 |
| `memory-games.test.mjs` | 30882 断言通过 |
| `memory-board.test.mjs` | 修复 CRLF 提取后，331498 断言通过 |
| `choice-board.test.cjs` | 18 项通过，含官方 SDK 语义检查 |
| `quick-games.test.cjs` | 11 项通过，含 SDK ArkTS lint，无跳过 |
| `quick-board.test.cjs` | 16 项通过、1 项失败，无跳过 |
| `spatial.test.cjs` | 38 组通过，严格类型检查通过 |
| `spatial-arkts.cjs` | 3 个真实 ETS 文件通过 SDK lint / 语义检查 |
| `session.test.cjs` | 14 项通过 |
| `prepare_resources.py --icons-only --check` | 通过 |
| Python 修改文件语法检查 | 通过 |
| `git diff --check` | 通过 |
| `prepare_resources.py --check` | 未通过，缺少 Android drawable XML |

### 两项既有失败

1. QuickBoard 的舒尔特调色板测试要求 Android `Game23Colors` 包含 `nextInt(256)`，而当前 Android 源码已使用固定十色调色板，旧断言与其不一致。本次未修改 Android 源码或 Harmony 玩法配色。
2. 完整资源重现脚本报错：`RuntimeError: Missing Android drawable XML: background_game12_white`。本次未重新生成或替换游戏资源；已有打包资源引用检查通过。

两项均已直接运行备份分支中的原始脚本复现相同失败，不是迁移引入。资源重现失败和单项测试失败仍须如实保留，不能称为全量测试通过。

## 设备验证范围

本次 `hdc list targets` 返回 `[Empty]`，因此没有执行迁移后 App 的安装、桌面启动、23 款游戏冒烟、后台恢复或实际画面检查。旧元服务的历史截图和冒烟结果不作为本次 App 运行证据。

设备脚本已从配置读取包名，并移除旧元服务下固定的暂停坐标；记忆棋盘采样中心改从可见无障碍节点取得。脚本语法检查通过，但仍未在设备执行。`device-smoke.py` 的列表滑动等其他逻辑仍使用历史 1320×2856 模拟器假设，不声称适用于任意手机分辨率。
