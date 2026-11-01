# 设计：闯关地图模式

需求见 #[[file:.kiro/specs/level-map/requirements.md]]

## 总体结构

```
pages/Index.ets            screen 状态机：新增 'map' / 'training'，以及关卡局的开始、结束和结算
core/StageCatalog.ets      新增：章节和关卡数据表、本局成绩计算、星级计算
core/Economy.ets           23 款游戏都需要解锁；补上 100 金币的默认价；新增 STAR_COINS
services/ProgressStore.ets 存档 v3：stages 星级数组、发现判定、可玩判定、关卡结算写入
components/StageMap.ets    新增：地图组件
components/AndroidScreens.ets
                           AndroidHome 支持问号卡片；新增 StageDetail、StageResult、
                           AndroidDiscovery 对话框（复用文件内的 AndroidDialogFrame 和 AndroidDialogAction）
```

导航仍沿用 `Index` 里的 `screen` 字符串状态机，不引入 Navigation。底部标签改为三个：

| 标签 | screen | 内容 |
| --- | --- | --- |
| 闯关 | `map` | `StageMap` |
| 训练 | `training` | `AndroidHome`（自由训练） |
| 成长 | `growth` | `AbilityGrowth`（不变） |

`this.tab` 记录玩家从哪个标签页进入游戏，退出游戏后返回该页。原来的 `'home'` 改为 `'map'`，作为初始页。

## 关卡数据（core/StageCatalog.ets）

```ts
export class StageInfo {
  index: number = 0;        // 0 起，全局顺序
  chapter: number = 0;      // 0..2
  gameId: number = 1;       // GameInfo.id
  stars: number[] = [];     // 1/2/3 星目标，严格递增，均 >= 1
  intro: boolean = false;   // 首发关
  challenge: boolean = false; // 章末挑战关
}
export class ChapterInfo { title: string = ''; first: number = 0; count: number = 0; }
export const CHAPTERS: ChapterInfo[];
export const STAGES: StageInfo[];
export function introStageOf(gameId: number): number;      // 首发关 index
export function stageScore(gameId: number, level: number, score: number, found: number): number;
export function starsFor(stage: StageInfo, value: number): number; // 0..3
```

每个关卡的星级目标直接以字面量写在表里，方便试玩后调整。初始数值由各游戏的 `trophies` 阈值 T0、T1、T2 按以下规则推算，结果四舍五入，最小为 1，三档必须严格递增：

| 关卡类型 | 1 星 | 2 星 | 3 星 |
| --- | --- | --- | --- |
| 首发关 | 0.35·T0 | 0.6·T0 | 0.85·T0 |
| 第 1、2 章的复习关 | 0.6·T0 | T0 | T1 |
| 第 3 章的复习关、章末挑战关 | T0 | T1 | T2 |

248（id 17）的本局成绩按得分计算，不适用上表。它的首发关暂定为 [256, 512, 1024]，复习关暂定为 [512, 1024, 2048]，都需要试玩后校准。

本局成绩 `stageScore` 的计算方式：

- id 23（舒尔特方格）：`puzzle.answers.length`，即找到的数字个数；
- id 17（248）：`score`；
- 其他游戏：`level - 1`。

### 关卡顺序（★ 为首发关）

**第 1 章 · 初识脑力（12 关，新游戏 7 款）**

1 记忆网格★ · 2 谁是新来的★ · 3 记忆网格 · 4 和之前一样吗？★ · 5 颜色★ · 6 谁是新来的 · 7 数一数★ · 8 全部相同★ · 9 和之前一样吗？ · 10 正确吗？★ · 11 颜色 · 12 挑战：记忆网格

**第 2 章 · 专注进阶（15 关，新游戏 8 款）**

13 六边形★ · 14 跟随路径★ · 15 数一数 · 16 旋转网格★ · 17 图像漩涡★ · 18 正确吗？ · 19 纸飞机★ · 20 跟随路径 · 21 对称★ · 22 全部相同 · 23 抓住它们★ · 24 旋转网格 · 25 独一无二★ · 26 纸飞机 · 27 挑战：六边形

**第 3 章 · 全能大师（15 关，新游戏 8 款）**

28 找到图片★ · 29 多与少★ · 30 图像漩涡 · 31 数字排序★ · 32 找出全部★ · 33 抓住它们 · 34 舒尔特方格★ · 35 激光★ · 36 独一无二 · 37 248★ · 38 对称 · 39 多与少 · 40 金字塔★ · 41 舒尔特方格 · 42 挑战：跟随路径

排序原则：第 1 章放原来免费、上手简单的游戏；原来收费的 10 款游戏主要放在第 2、3 章，与原有的付费梯度一致。

## 经济（core/Economy.ets）

- `requiresUnlock(id)`：所有合法 id 都返回 true。
- `unlockCost(id)`：原有的 10 个价格不变，其他 id 返回 100。金字塔（id 18）保持 0 金币，仍需玩家确认。
- 新增 `STAR_COINS = 10`。

## 存档（services/ProgressStore.ets）

```ts
class SaveData { version: number = 3; ...; stages: number[] = []; }  // stages[i] = 第 i 关最佳星级
```

- `validSave` 接受 version 2 或 3。读到 version 2 时补上 `stages = []` 并把 version 改为 3，然后照常校验。`stages` 的校验规则：必须是数组，长度不超过 `STAGES.length`，每项是 0 到 3 的整数。
- `unlocked` 的校验规则不变（必须满足 `requiresUnlock` 且不重复），因为现在所有 id 都满足 `requiresUnlock`。
- 新增以下方法：
  - `stageStars(i)`：返回第 i 关的最佳星级。
  - `stageOpen(i)`：`i === 0 || stageStars(i - 1) >= 1`。
  - `currentStage()`：返回第一个 0 星且已开放的关卡；如果全部通关，返回最后一关。
  - `discovered(id)`：`stageOpen(introStageOf(id))`。
  - `isUnlocked(id)`：改为 `validId(id) && (discovered(id) || unlocked.includes(id))`。`unlockedGames()` 跟着变化，调用方式不变。
  - `completeStage(i, stars): number`：如果星级高于历史最佳就更新，并在同一次写入中加上 `新增星数 × STAR_COINS` 金币，返回新增的金币数。写入方式沿用 `earnCoins`：先同步修改内存，再排队保存。
- `purchase`：已发现的游戏返回 `'owned'`，不扣金币。现有逻辑已经通过 `isUnlocked` 实现这一点。

## 游戏流程（pages/Index.ets）

新增状态：

```ts
@State stageIndex: number = -1;   // -1 表示自由训练
@State stageDetail: number = -1;  // 打开中的关卡详情对话框
@State stageStarsEarned: number = 0;
@State stageCoins: number = 0;
@State discoveryIndex: number = -1; // 待展示的"发现新训练"（GAMES 下标）
```

- `openStage(i)`：检查关卡是否开放，然后设置 `stageDetail = i`。
- `playStage(i)`：设置 `stageIndex`、`gameIndex`，然后按 `selectGame` 的教程逻辑进入游戏。关卡已开放就等于游戏已发现，所以不会弹出购买对话框。
- `start()`：当 `stageIndex >= 0` 时 `level = 1`，否则仍用 `startingLevel`。
- 自由训练的入口（训练页、成长页的 `onPractice`、结算页推荐）都会先把 `stageIndex` 置为 -1。
- 新增 `checkStageGoal()`，在 `nextRound` 让 level 增加之后、id 23 找到数字之后、id 17 得分之后调用。本局成绩达到 3 星目标时调用 `finish('满星通关')`。
- `tick` 中生命用完的分支：关卡模式下调用 `finish('本局结束')`，不进入 `'failure'`。
- `finish()`：先照常 `saveSession()`，然后在关卡模式下计算 `starsFor`，调用 `store.completeStage`，刷新金币，再设置 `screen = 'result'`。
- 结算界面：关卡模式下渲染 `StageResult`，自由训练下仍渲染 `AndroidResult`。
  - "下一关"：如果下一关是首发关，先设置 `discoveryIndex` 展示发现提示，关闭后打开下一关的详情。
  - "返回地图"：`screen = 'map'`。
- 暂停菜单的"游戏列表"按钮在关卡模式下改为"返回地图"。`AndroidPause` 新增 `homeLabel` 属性。
- `onBackPress`：
  - 依次关闭 `discoveryIndex` 和 `stageDetail` 对话框；
  - 在 `training` 或 `growth` 页按返回，先回到 `map`；
  - 在 `map` 页按返回，退出应用。

游戏顶部原来的"第 N 关"徽标改为"第 N 级"，避免和地图上的"关"混淆。`AndroidResult` 里的文案同步修改。关卡模式下，徽标下方增加一行目标进度，例如"★ 目标 5 · 已完成 3"。

## 地图组件（components/StageMap.ets）

```ts
@Component export struct StageMap {
  @Prop stars: number[] = [];        // 每关最佳星级，长度 = STAGES.length
  @Prop coins: number = 0;
  @Prop current: number = 0;
  onOpen: (stage: number) => void;
}
```

- 布局使用 `Scroll` 包裹 `Column`：每章先放一个章节横幅，然后放一个 `Stack`，其中的节点用 `position` 绝对定位。
- 节点之间的纵向间距固定为 96vp。横向位置按 5 个位置循环（20%、50%、80%、50%、20% ……），形成蛇形路线。相邻节点之间用 `Line` 组件连线：已通关的段用品牌色，未开放的段用灰色。
- 节点有以下几种：
  - 普通关：圆形，显示关卡序号。
  - 首发关：开放后显示游戏缩略图，未开放时显示问号。
  - 挑战关：尺寸更大，带皇冠图标。
  - 当前关：带呼吸缩放动画。
  - 已通关：节点下方显示 3 颗星，获得的星点亮。
- 地图首次出现和从游戏返回时，`Scroller.scrollTo` 把当前关滚到视口中部。
- 顶部栏显示标题和金币余额，复用 `AndroidHome` 顶部栏的样式。

## 自由训练（AndroidHome）

- 新增 `discoveredIds: number[]` 属性。
- 未发现且未购买的游戏显示问号卡片：缩略图位置显示问号，名称位置显示"第 X 章解锁"，分类标签保留。
- 点击问号卡片仍然调用 `onSelect`。`Index.selectGame` 判断游戏不可玩后会弹出 `AndroidUnlock`。
- 顶部计数改为"已开放 x / 23"，其中 x 是可玩的游戏数量。

## 关键权衡

- **复用现有关卡生成，不做专门的谜题设计。** 现有的 `createPuzzle(id, level)` 已经有成熟的难度曲线，星级目标只是在这条曲线上取几个截断点。这样内容成本最低，代价是同一款游戏在不同关卡的体验差别只在于目标高低。
- **发现状态不单独存储，由星级数据推导。** 关卡开放了就等于游戏已发现，这样存档里只需要增加一个数组，也不会出现两份数据不一致。
- **自由训练和关卡共用游戏记录。** 关卡局也会写入 `highest` 和 `sessions`，"成长"页的数据保持连续。缺点是自适应游戏的 `startingLevel` 会受关卡局（都从第 1 级开始）影响，这一点可以接受。

## 验证方式

- 用 hvigor 构建确认可以编译。
- 在设备或模拟器上按照 tasks.md 最后的手动检查清单逐项验证。按约定本期不写自动化测试。
