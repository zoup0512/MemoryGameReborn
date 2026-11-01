# 任务：闯关地图模式

设计见 #[[file:.kiro/specs/level-map/design.md]]

- [x] 1. 关卡数据
  - [x] 1.1 新建 `core/StageCatalog.ets`：包含 `StageInfo`、`ChapterInfo`、`CHAPTERS`，以及 42 关的 `STAGES`。星级目标按设计中的规则算好后写成字面量。
  - [x] 1.2 实现 `introStageOf`、`stageScore`、`starsFor`。加载时检查以下条件，不满足就抛错：每款游戏恰好有一个首发关；所有目标严格递增。
- [x] 2. 经济与存档
  - [x] 2.1 `Economy.ets`：`requiresUnlock` 对所有合法 id 返回 true；默认价格设为 100；新增 `STAR_COINS`。
  - [x] 2.2 `ProgressStore.ets`：
    - 存档升到 v3，新增 `stages` 字段，version 2 的存档自动补全；
    - `validSave` 增加对 `stages` 的校验；
    - 新增 `stageStars`、`stageOpen`、`currentStage`、`discovered`、`completeStage`；
    - 修改 `isUnlocked`。
- [x] 3. 游戏流程（`Index.ets`）
  - [x] 3.1 新增关卡相关状态，实现 `openStage`、`playStage`；在 `start()` 中区分关卡局和自由训练的起始等级；所有自由训练入口都重置 `stageIndex`。
  - [x] 3.2 新增 `stageGoalReached`，在三处调用（level 增加后、id 23 找到数字后、id 17 得分后）。关卡模式下生命用完时直接结算。`finish()` 中写入星级并发放金币。
  - [x] 3.3 游戏顶部徽标改为"第 N 级"，`AndroidResult` 的文案同步修改；关卡模式下显示目标进度；暂停菜单增加 `homeLabel` 属性。
  - [x] 3.4 底部改为三个标签（闯关、训练、成长），初始 screen 改为 `map`，同步更新 `onBackPress`。
- [x] 4. 界面
  - [x] 4.1 新建 `components/StageMap.ets`：章节横幅、蛇形节点和连线、各种节点状态、自动滚动到当前关。
  - [x] 4.2 在 `AndroidScreens.ets` 中新增 `StageDetail`、`StageResult`、`AndroidDiscovery` 对话框。
  - [x] 4.3 `AndroidHome` 支持问号卡片，并修改已开放数量的统计。
- [ ] 5. 验证
  - [x] 5.1 用 hvigor 构建，确认没有编译错误。
  - [ ] 5.2 手动检查清单（需真机/模拟器）：
    - 新存档只开放第 1 关，训练页只有记忆网格可玩；
    - 在第 1 关拿到 1 星后，第 2 关开放，结算后弹出"谁是新来的"的发现提示；
    - 达到 3 星目标时本局自动结束，生命用完时直接进入关卡结算；
    - 重复通关同一关时，只按新增的星数发放金币；
    - 在训练页用金币提前解锁一款第 3 章的游戏后，可以游玩，但地图上对应的节点仍是问号；
    - 用 248 和舒尔特方格各打一关，确认本局成绩的口径正确；
    - 返回键：对话框、训练页、成长页、地图页的返回行为都符合设计；
    - 深色模式下地图可读。
