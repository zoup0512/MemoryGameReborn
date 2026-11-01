import { GAMES, GameInfo, media } from '../core/GameCatalog';
import { requiresUnlock, unlockCost } from '../core/Economy';
import { STAGES, StageInfo, introStageOf, stageUnit, validStage } from '../core/StageCatalog';

// App shell (home, tutorial, dialogs). Game boards keep the Android visuals;
// shell colors come from resources/{base,dark}/element/color.json.
const ALL_CATEGORIES: string = '全部';
const CATEGORIES: string[] = [ALL_CATEGORIES, '记忆力', '注意力', '速度', '灵活性', '问题解决', '想象力'];
const LAST_PLAYED: string = '#FFA000';

function androidGame(gameIndex: number): GameInfo {
  return GAMES[Math.max(0, Math.min(GAMES.length - 1, Math.floor(gameIndex)))];
}

function androidLevel(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;
}

function stageAt(index: number): StageInfo {
  return STAGES[validStage(index) ? index : 0];
}

function stageGameIndex(stage: StageInfo): number {
  return Math.max(0, GAMES.findIndex((game: GameInfo) => game.id === stage.gameId));
}

// Chapter (1-based) whose intro stage reveals the game on the map.
function discoveryChapter(game: GameInfo): number {
  const intro = introStageOf(game.id);
  return intro >= 0 ? STAGES[intro].chapter + 1 : 1;
}

const STAR_ON: string = '#FFB300';
const STAR_OFF: string = '#33808A99';

@Builder
function starRow(earned: number, size: number) {
  Row({ space: size / 4 }) {
    ForEach([1, 2, 3], (star: number) => {
      SymbolGlyph($r('sys.symbol.star_fill')).fontSize(size)
        .fontColor([star <= earned ? STAR_ON : STAR_OFF])
    }, (star: number) => `${star}`)
  }.accessibilityLevel('no')
}

// '#RRGGBB' -> translucent '#AARRGGBB' tint for category tags.
function tint(color: string, alpha: string): string {
  return color.length === 7 ? `#${alpha}${color.substring(1)}` : color;
}

@Component
export struct AndroidHome {
  // GAMES array order (23 entries), highest reached, not highest completed.
  @Prop records: number[] = [];
  @Prop lastGame: number = -1;
  @Prop coins: number = 0;
  // Contains every open GameInfo.id, not array indexes. Empty while loading:
  // never infer ownership from a record, balance, or missing persisted data.
  @Prop unlockedIds: number[] = [];
  onSelect: (index: number) => void = (_index: number): void => {};
  @State private category: string = ALL_CATEGORIES;

  private recordAt(index: number): number {
    return androidLevel(index < this.records.length ? this.records[index] : 0);
  }

  private locked(game: GameInfo): boolean {
    return requiresUnlock(game.id) && this.unlockedIds.indexOf(game.id) < 0;
  }

  private priced(game: GameInfo): boolean {
    // The secret pyramid stays a zero-coin, confirmed unlock.
    return this.locked(game) && unlockCost(game.id) > 0;
  }

  // Undiscovered games stay mysteries: only category and chapter are shown.
  private rowDescription(game: GameInfo, index: number): string {
    if (this.locked(game)) {
      const price: string = this.priced(game) ? `提前解锁价格 ${unlockCost(game.id)} 金币` : '可免费提前解锁';
      return `神秘训练，${game.category}，未解锁，第 ${discoveryChapter(game)} 章发现，${price}`;
    }
    return `${game.name}，${game.category}，等级 ${this.recordAt(index)}`;
  }

  private visibleIndexes(): number[] {
    const result: number[] = [];
    GAMES.forEach((game: GameInfo, index: number) => {
      if (this.category === ALL_CATEGORIES || game.category === this.category) { result.push(index); }
    });
    return result;
  }

  private openCount(): number {
    return GAMES.filter((game: GameInfo) => !this.locked(game)).length;
  }

  @Builder
  private CategoryChip(name: string) {
    Text(name)
      .fontSize(14).fontWeight(this.category === name ? FontWeight.Medium : FontWeight.Normal)
      .fontColor(this.category === name ? $r('app.color.on_brand') : $r('app.color.text_secondary'))
      .height(34).padding({ left: 16, right: 16 }).borderRadius(17)
      .backgroundColor(this.category === name ? $r('app.color.brand') : $r('app.color.chip_background'))
      .onClick(() => { this.category = name; })
      .accessibilityText(`${name}分类`)
      .accessibilitySelected(this.category === name)
  }

  @Builder
  private GameCard(game: GameInfo, index: number) {
    Column() {
      Stack({ alignContent: Alignment.TopStart }) {
        if (!this.locked(game)) {
          Image(media(game.thumbnail))
            .width('100%').height('100%').padding(8)
            .objectFit(ImageFit.Contain).draggable(false).accessibilityLevel('no')
        }
        if (this.locked(game)) {
          Column({ space: 8 }) {
            Text('?').fontSize(40).fontWeight(FontWeight.Bold).fontColor('#FFFFFF')
              .accessibilityLevel('no')
            Row({ space: 4 }) {
              if (this.priced(game)) {
                Image(media('ic_coin')).width(16).height(16).objectFit(ImageFit.Contain)
                  .accessibilityLevel('no')
                Text(`${unlockCost(game.id)}`).fontSize(14).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
              } else {
                Text('免费解锁').fontSize(13).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
              }
            }.height(26).padding({ left: 10, right: 10 }).borderRadius(13).backgroundColor('#33FFFFFF')
          }.width('100%').height('100%').justifyContent(FlexAlign.Center)
            .linearGradient({ angle: 135, colors: [[tint(game.color, 'CC'), 0], ['#E6101820', 1]] })
            .hitTestBehavior(HitTestMode.None)
        }
        if (this.lastGame === index) {
          Text('上次训练').fontSize(11).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
            .height(22).padding({ left: 8, right: 8 }).borderRadius(11).backgroundColor(LAST_PLAYED)
            .margin({ left: 8, top: 8 }).hitTestBehavior(HitTestMode.None)
        }
      }.width('100%').aspectRatio(1.3).borderRadius(16).clip(true)
        .backgroundColor($r('app.color.thumb_background'))
      Text(this.locked(game) ? `第 ${discoveryChapter(game)} 章发现` : game.name)
        .width('100%').margin({ top: 10 }).fontSize(16).fontWeight(FontWeight.Medium)
        .fontColor(this.locked(game) ? $r('app.color.text_secondary') : $r('app.color.text_primary'))
        .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
      Row() {
        Text(game.category)
          .fontSize(11).fontWeight(FontWeight.Medium).fontColor(game.color)
          .height(20).padding({ left: 6, right: 6 }).borderRadius(6).backgroundColor(tint(game.color, '1F'))
        Blank()
        if (this.locked(game)) {
          Text('未发现').fontSize(12).fontColor($r('app.color.text_tertiary'))
        } else {
          Text(`Lv.${this.recordAt(index)}`).fontSize(13).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_secondary'))
        }
      }.width('100%').height(20).margin({ top: 6 })
    }.width('100%').padding({ left: 8, right: 8, top: 8, bottom: 12 })
      .backgroundColor($r('app.color.card_background')).borderRadius(22)
      .shadow({ radius: 16, color: $r('app.color.card_shadow'), offsetY: 4 })
      .clickEffect({ level: ClickEffectLevel.LIGHT, scale: 0.96 })
      .onClick(() => this.onSelect(index))
      .accessibilityText(this.rowDescription(game, index))
      .accessibilityGroup(true)
  }

  build() {
    Column() {
      Row() {
        Column() {
          Text('自由训练').fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          Text(`已开放 ${this.openCount()} / ${GAMES.length} 款训练`)
            .fontSize(13).fontColor($r('app.color.text_secondary')).margin({ top: 2 })
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Row({ space: 6 }) {
          Image(media('ic_coin')).width(20).height(20).objectFit(ImageFit.Contain)
            .accessibilityLevel('no')
          Text(`${androidLevel(this.coins)}`).fontSize(16).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.coin_text'))
        }.height(36).padding({ left: 10, right: 14 }).borderRadius(18)
          .backgroundColor($r('app.color.coin_background'))
          .accessibilityGroup(true).id('coin-balance')
          .accessibilityText(`金币余额 ${androidLevel(this.coins)}`)
      }.width('100%').padding({ left: 20, right: 16, top: 16, bottom: 12 })
      Scroll() {
        Row({ space: 8 }) {
          ForEach(CATEGORIES, (name: string) => { this.CategoryChip(name) }, (name: string) => name)
        }.padding({ left: 16, right: 16 })
      }.width('100%').height(34).scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off)
        .edgeEffect(EdgeEffect.Spring)
      List({ space: 12 }) {
        ForEach(this.visibleIndexes(), (index: number) => {
          ListItem() { this.GameCard(GAMES[index], index) }
        }, (index: number) => `${GAMES[index].id}`)
      }.width('100%').layoutWeight(1).lanes(2, 12).scrollBar(BarState.Off).edgeEffect(EdgeEffect.Spring)
        .padding({ left: 16, right: 16 }).contentStartOffset(16).contentEndOffset(24)
    }.width('100%').height('100%')
  }
}

@Component
export struct AndroidTutorial {
  @Prop @Watch('resetPages') gameIndex: number = 0;
  @Prop fromPause: boolean = false;
  onExit: () => void = (): void => {};
  onPlay: () => void = (): void => {};
  @State private pageIndex: number = 0;
  private pager: SwiperController = new SwiperController();

  private resetPages(): void {
    this.pageIndex = 0;
    this.pager.changeIndex(0, false);
  }

  private lastPage(): boolean {
    return this.pageIndex >= androidGame(this.gameIndex).tutorialImages.length - 1;
  }

  private actionLabel(): string {
    if (!this.lastPage()) { return '下一步'; }
    return this.fromPause ? '返回游戏' : '开始训练';
  }

  private nextOrPlay(): void {
    if (!this.lastPage()) { this.pager.showNext(); }
    else if (this.fromPause) { this.onExit(); }
    else { this.onPlay(); }
  }

  build() {
    Column() {
      Row() {
        Button({ type: ButtonType.Circle }) {
          SymbolGlyph($r('sys.symbol.chevron_left')).fontSize(22).fontColor([$r('app.color.text_primary')])
        }.width(40).height(40).backgroundColor($r('app.color.card_background'))
          .accessibilityText('返回').onClick(() => this.onExit())
        Column() {
          Text(androidGame(this.gameIndex).name).fontSize(18).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary')).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
          Text('玩法教程').fontSize(12).fontColor($r('app.color.text_secondary'))
        }.layoutWeight(1).padding({ left: 12, right: 12 })
        Text(this.fromPause || this.lastPage() ? '' : '跳过')
          .width(48).height(40).textAlign(TextAlign.Center).fontSize(15).fontColor($r('app.color.brand'))
          .enabled(!this.fromPause && !this.lastPage())
          .onClick(() => this.onPlay())
      }.width('100%').height(56)
      Column() {
        Swiper(this.pager) {
          ForEach(androidGame(this.gameIndex).tutorialImages, (assetName: string) => {
            Image(media(assetName)).width('100%').height('100%').padding(16)
              .objectFit(ImageFit.Contain).draggable(false)
          }, (assetName: string) => assetName)
        }.width('100%').layoutWeight(1).index(this.pageIndex).loop(false)
          .indicator(false).duration(250).curve(Curve.Friction)
          .backgroundColor($r('app.color.thumb_background')).borderRadius(20).clip(true)
          .onChange((index: number) => { this.pageIndex = index; })
        Text(androidGame(this.gameIndex).tutorialText[this.pageIndex])
          .width('100%').height(84).padding({ left: 8, right: 8 }).margin({ top: 16 })
          .fontSize(17).lineHeight(25).fontColor($r('app.color.text_primary'))
          .textAlign(TextAlign.Center).maxLines(3).minFontSize(12).maxFontSize(17)
          .heightAdaptivePolicy(TextHeightAdaptivePolicy.MIN_FONT_SIZE_FIRST)
        Row({ space: 6 }) {
          ForEach(androidGame(this.gameIndex).tutorialImages, (_assetName: string, index: number) => {
            Row().width(index === this.pageIndex ? 18 : 6).height(6).borderRadius(3)
              .backgroundColor(index === this.pageIndex ? $r('app.color.brand') : $r('app.color.divider'))
              .animation({ duration: 200, curve: Curve.Friction })
          }, (_assetName: string, index: number) => `${index}`)
        }.height(24).justifyContent(FlexAlign.Center).accessibilityLevel('no')
      }.width('100%').layoutWeight(1).padding(12).margin({ top: 4 })
        .backgroundColor($r('app.color.card_background')).borderRadius(28)
      Button(this.actionLabel(), { type: ButtonType.Capsule })
        .width('100%').height(48).margin({ top: 16, bottom: 16 }).constraintSize({ maxWidth: 420 })
        .fontSize(17).fontWeight(FontWeight.Medium).fontColor($r('app.color.on_brand'))
        .backgroundColor($r('app.color.brand')).onClick(() => this.nextOrPlay())
    }.width('100%').height('100%').padding({ left: 16, right: 16 })
  }
}

@Builder
function androidEmptyDialogContent() {
  Column() {}
}

// Internal shared surface. Host must mount overlays in a full-size Stack, without
// an extra dim layer. No tap-outside dismissal, network, timers or session logic.
@Component
struct AndroidDialogFrame {
  @Prop screenHeight: number = 0;
  @State private measuredViewportHeight: number = 0;
  @State private shown: boolean = false;
  @BuilderParam content: () => void = androidEmptyDialogContent;

  private maximumCardHeight(): number {
    const available: number = this.screenHeight > 0 ? this.screenHeight : this.measuredViewportHeight;
    return Math.max(1, (available > 0 ? available : 800) - 48);
  }

  build() {
    Stack() {
      // A sibling backdrop consumes outside taps without suppressing the card's
      // descendants. HitTestMode.Block on an ancestor also blocks its buttons.
      Column() {}.width('100%').height('100%').backgroundColor($r('app.color.scrim'))
        .opacity(this.shown ? 1 : 0)
        .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
        .onClick(() => {})
      Column() {
        Scroll() {
          this.content()
        }.width('100%').constraintSize({ maxWidth: 400, maxHeight: this.maximumCardHeight() })
          .scrollBar(BarState.Off).align(Alignment.Top)
          .backgroundColor($r('app.color.card_background')).borderRadius(32).clip(true)
          .opacity(this.shown ? 1 : 0).scale({ x: this.shown ? 1 : 0.92, y: this.shown ? 1 : 0.92 })
      }.width('100%').height('100%').padding({ left: 20, right: 20 })
        .justifyContent(FlexAlign.Center)
    }.width('100%').height('100%')
      .onClick(() => {})
      .onAppear(() => {
        this.getUIContext().animateTo({ duration: 220, curve: Curve.Friction }, () => { this.shown = true; });
      })
      .onAreaChange((_oldArea: Area, newArea: Area) => {
        const measured: number = Number(newArea.height);
        if (measured > 0) { this.measuredViewportHeight = measured; }
      })
  }
}

@Component
struct AndroidDialogAction {
  @Prop labelText: string = '';
  @Prop actionKind: string = 'primary';
  @Prop actionEnabled: boolean = true;
  onAction: () => void = (): void => {};

  private labelColor(): ResourceColor {
    if (this.actionKind === 'primary') { return $r('app.color.on_brand'); }
    return this.actionKind === 'danger' ? $r('app.color.danger') : $r('app.color.brand');
  }

  private fillColor(): ResourceColor {
    if (this.actionKind === 'primary') { return $r('app.color.brand'); }
    return this.actionKind === 'secondary' ? $r('app.color.brand_soft') : Color.Transparent;
  }

  build() {
    Button({ type: ButtonType.Capsule }) {
      Text(this.labelText).fontSize(16).fontWeight(FontWeight.Medium)
        .fontColor(this.labelColor()).textAlign(TextAlign.Center)
    }.width('100%').height(48).padding({ left: 16, right: 16 })
      .backgroundColor(this.fillColor())
      .enabled(this.actionEnabled).opacity(this.actionEnabled ? 1 : 0.5)
      .onClick(() => { if (this.actionEnabled) { this.onAction(); } }).accessibilityText(this.labelText)
  }
}

@Builder
function dialogBadge(symbol: Resource, foreground: ResourceColor, background: ResourceColor) {
  Stack() {
    SymbolGlyph(symbol).fontSize(28).fontColor([foreground])
  }.width(60).height(60).borderRadius(30).backgroundColor(background).accessibilityLevel('no')
}

@Component
struct PauseShortcut {
  symbol: Resource = $r('sys.symbol.arrow_clockwise');
  labelText: string = '';
  descriptionText: string = '';
  onAction: () => void = (): void => {};

  build() {
    Column({ space: 6 }) {
      SymbolGlyph(this.symbol).fontSize(22).fontColor([$r('app.color.brand')])
      Text(this.labelText).fontSize(13).fontColor($r('app.color.text_primary')).maxLines(1)
    }.layoutWeight(1).height(76).justifyContent(FlexAlign.Center)
      .borderRadius(18).backgroundColor($r('app.color.brand_soft'))
      .clickEffect({ level: ClickEffectLevel.LIGHT, scale: 0.95 })
      .onClick(() => this.onAction())
      .accessibilityGroup(true).accessibilityText(this.descriptionText)
  }
}

@Component
export struct AndroidUnlock {
  @Prop @Watch('resetConfirmation') gameIndex: number = 0;
  @Prop balance: number = 0;
  @Prop @Watch('resetConfirmation') busy: boolean = false;
  @Prop @Watch('resetConfirmation') errorText: string = '';
  @Prop screenHeight: number = 0;
  onConfirm: () => void = (): void => {};
  onCancel: () => void = (): void => {};
  @State private confirmPending: boolean = false;

  private resetConfirmation(): void {
    if (!this.busy) { this.confirmPending = false; }
  }

  private price(): number {
    return unlockCost(androidGame(this.gameIndex).id);
  }

  private canConfirm(): boolean {
    return !this.busy && !this.confirmPending && requiresUnlock(androidGame(this.gameIndex).id) &&
      Number.isFinite(this.balance) && this.balance >= this.price();
  }

  private confirm(): void {
    if (!this.canConfirm()) { return; }
    // Protect a second tap before the parent's busy prop reaches this component.
    this.confirmPending = true;
    this.onConfirm();
  }

  @Builder
  private UnlockContent() {
    Column() {
      Stack() {
        Image(media(androidGame(this.gameIndex).thumbnail)).width('100%').height('100%').padding(8)
          .objectFit(ImageFit.Contain).draggable(false).accessibilityLevel('no')
      }.width('100%').height(132).borderRadius(20).clip(true).backgroundColor($r('app.color.thumb_background'))
      Text('提前解锁').width('100%').margin({ top: 20 }).textAlign(TextAlign.Center)
        .fontSize(22).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      Text(`游戏：${androidGame(this.gameIndex).name}\n解锁价格：${this.price()} 金币\n当前余额：${androidLevel(this.balance)} 金币\n也可以在闯关第 ${discoveryChapter(androidGame(this.gameIndex))} 章免费发现它`)
        .width('100%').margin({ top: 12 }).padding(14).borderRadius(16)
        .backgroundColor($r('app.color.page_background'))
        .fontSize(15).fontColor($r('app.color.text_secondary')).lineHeight(24)
      if (androidLevel(this.balance) < this.price()) {
        Text(`还差 ${this.price() - androidLevel(this.balance)} 金币，暂时无法解锁。可通过闯关和训练获得金币。`)
          .width('100%').fontSize(14).fontColor($r('app.color.danger')).lineHeight(20).margin({ top: 10 })
      }
      if (this.errorText.length > 0) {
        Text(this.errorText).width('100%').fontSize(14)
          .fontColor($r('app.color.danger')).lineHeight(20).margin({ top: 10 })
      }
      AndroidDialogAction({ labelText: this.busy ? '正在解锁…' : `解锁 · ${this.price()} 金币`,
        actionEnabled: this.canConfirm(), onAction: () => this.confirm() }).margin({ top: 20 })
      AndroidDialogAction({ labelText: '取消', actionKind: 'secondary', actionEnabled: !this.busy,
        onAction: () => { if (!this.busy) { this.onCancel(); } } }).margin({ top: 10 })
    }.width('100%').padding(24).alignItems(HorizontalAlign.Start)
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.UnlockContent(); } })
  }
}

@Component
export struct AndroidPause {
  @Prop screenHeight: number = 0;
  onResume: () => void = (): void => {};
  onRestart: () => void = (): void => {};
  onTutorial: () => void = (): void => {};
  onHome: () => void = (): void => {};
  onEnd: () => void = (): void => {};
  @Prop homeLabel: string = '游戏列表';

  @Builder
  private PauseContent() {
    Column() {
      dialogBadge($r('sys.symbol.pause'), $r('app.color.brand'), $r('app.color.brand_soft'))
      Text('游戏已暂停').margin({ top: 16 }).fontSize(22).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))
      Text('准备好后可继续游戏。结束本局将进入成绩结算。')
        .width('100%').textAlign(TextAlign.Center).margin({ top: 8 })
        .fontSize(14).fontColor($r('app.color.text_secondary')).lineHeight(20)
      AndroidDialogAction({ labelText: '继续游戏', onAction: () => this.onResume() }).margin({ top: 24 })
      Row({ space: 10 }) {
        PauseShortcut({ symbol: $r('sys.symbol.arrow_clockwise'), labelText: '重新开始',
          descriptionText: '重新开始本局', onAction: () => this.onRestart() })
        PauseShortcut({ symbol: $r('sys.symbol.questionmark_circle'), labelText: '玩法教程',
          descriptionText: '查看玩法教程', onAction: () => this.onTutorial() })
        PauseShortcut({ symbol: $r('sys.symbol.square_grid_2x2'), labelText: this.homeLabel,
          descriptionText: this.homeLabel.startsWith('返回') ? this.homeLabel : `返回${this.homeLabel}`,
          onAction: () => this.onHome() })
      }.width('100%').margin({ top: 12 })
      AndroidDialogAction({ labelText: '结束本局并查看成绩', actionKind: 'danger',
        onAction: () => this.onEnd() }).margin({ top: 8 })
    }.width('100%').padding({ left: 24, right: 24, top: 28, bottom: 16 })
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.PauseContent(); } })
  }
}

@Component
export struct AndroidResult {
  @Prop gameIndex: number = 0;
  @Prop level: number = 1;
  @Prop best: number = 1;
  @Prop title: string = '时间到';
  @Prop screenHeight: number = 0;
  onReplay: () => void = (): void => {};
  onHome: () => void = (): void => {};
  onRecommend: (index: number) => void = (_index: number): void => {};

  private recommendedIndex(): number {
    return (Math.max(0, Math.min(GAMES.length - 1, Math.floor(this.gameIndex))) + 1) % GAMES.length;
  }

  @Builder
  private ResultContent() {
    Column() {
      dialogBadge($r('sys.symbol.flag_checkered'), '#FFFFFF', $r('app.color.brand'))
      Text(this.title).margin({ top: 16 }).fontSize(22).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))
      Column() {
        Text('本局到达').fontSize(13).fontColor($r('app.color.text_secondary'))
        Row({ space: 4 }) {
          Text('第').fontSize(16).fontColor($r('app.color.brand'))
          Text(`${androidLevel(this.level)}`).fontSize(48).fontWeight(FontWeight.Bold).fontColor($r('app.color.brand'))
          Text('级').fontSize(16).fontColor($r('app.color.brand'))
        }.alignItems(VerticalAlign.Bottom).margin({ top: 2 })
        Text(`最佳到达第 ${androidLevel(this.best)} 级`)
          .fontSize(13).fontColor($r('app.color.text_secondary')).margin({ top: 4 })
      }.width('100%').margin({ top: 16 }).padding({ top: 14, bottom: 14 }).borderRadius(20)
        .backgroundColor($r('app.color.page_background'))
        .accessibilityGroup(true)
        .accessibilityText(`本局到达第 ${androidLevel(this.level)} 级，最佳到达第 ${androidLevel(this.best)} 级`)
      AndroidDialogAction({ labelText: '再玩一次', onAction: () => this.onReplay() }).margin({ top: 20 })
      AndroidDialogAction({ labelText: '返回训练列表', actionKind: 'secondary', onAction: () => this.onHome() })
        .margin({ top: 10 })
      Text('试试其他游戏').width('100%').fontSize(13)
        .fontColor($r('app.color.text_secondary')).margin({ top: 20 })
      Row({ space: 12 }) {
        Image(media(androidGame(this.recommendedIndex()).thumbnail))
          .width(76).height(58).padding(4).borderRadius(12).backgroundColor($r('app.color.thumb_background'))
          .objectFit(ImageFit.Contain).draggable(false).accessibilityLevel('no')
        Column({ space: 4 }) {
          Text(androidGame(this.recommendedIndex()).name).fontSize(16).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary')).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(androidGame(this.recommendedIndex()).category).fontSize(12)
            .fontColor(androidGame(this.recommendedIndex()).color)
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        SymbolGlyph($r('sys.symbol.chevron_right')).fontSize(18).fontColor([$r('app.color.text_tertiary')])
      }.width('100%').padding(8).margin({ top: 8 }).borderRadius(18)
        .border({ width: 1, color: $r('app.color.divider') })
        .clickEffect({ level: ClickEffectLevel.LIGHT, scale: 0.97 })
        .onClick(() => this.onRecommend(this.recommendedIndex()))
        .accessibilityGroup(true)
        .accessibilityText(`试试其他游戏，${androidGame(this.recommendedIndex()).name}`)
    }.width('100%').padding({ left: 24, right: 24, top: 28, bottom: 24 })
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.ResultContent(); } })
  }
}

@Component
export struct AndroidFailure {
  // Kept as session-context props; the original no-coin failure layout does not print them.
  @Prop gameIndex: number = 0;
  @Prop level: number = 1;
  @Prop screenHeight: number = 0;
  onRestart: () => void = (): void => {};
  onEnd: () => void = (): void => {};

  @Builder
  private FailureContent() {
    Column() {
      dialogBadge($r('sys.symbol.heart_fill'), $r('app.color.danger'), $r('app.color.danger_soft'))
      Text('再试一次？').margin({ top: 16 }).fontSize(22).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))
      Text('请选择接下来的操作，无需倒计时内做决定。')
        .width('100%').textAlign(TextAlign.Center).margin({ top: 8 })
        .fontSize(14).fontColor($r('app.color.text_secondary')).lineHeight(20)
      AndroidDialogAction({ labelText: '重新开始本局', onAction: () => this.onRestart() }).margin({ top: 24 })
      AndroidDialogAction({ labelText: '结束本局并查看成绩', actionKind: 'danger', onAction: () => this.onEnd() })
        .margin({ top: 8 })
    }.width('100%').padding({ left: 24, right: 24, top: 28, bottom: 16 })
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.FailureContent(); } })
  }
}

@Component
export struct StageDetail {
  @Prop stageIndex: number = 0;
  @Prop best: number = 0;
  @Prop screenHeight: number = 0;
  onStart: () => void = (): void => {};
  onCancel: () => void = (): void => {};

  private target(star: number): string {
    const stage = stageAt(this.stageIndex);
    const unit = stageUnit(stage.gameId);
    const value = stage.stars[star - 1];
    if (unit === '分') { return `得到 ${value} 分`; }
    if (unit === '个') { return `找到 ${value} 个数字`; }
    return `完成 ${value} 级`;
  }

  @Builder
  private DetailContent() {
    Column() {
      Row({ space: 8 }) {
        Text(`第 ${this.stageIndex + 1} 关`).fontSize(22).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        if (stageAt(this.stageIndex).challenge) {
          Text('章节挑战').fontSize(12).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
            .height(22).padding({ left: 8, right: 8 }).borderRadius(11).backgroundColor(STAR_ON)
        }
        Blank()
        starRow(this.best, 18)
      }.width('100%')
      Row({ space: 12 }) {
        Image(media(androidGame(stageGameIndex(stageAt(this.stageIndex))).thumbnail))
          .width(84).height(64).padding(4).borderRadius(14).backgroundColor($r('app.color.thumb_background'))
          .objectFit(ImageFit.Contain).draggable(false).accessibilityLevel('no')
        Column({ space: 6 }) {
          Text(androidGame(stageGameIndex(stageAt(this.stageIndex))).name).fontSize(17).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary')).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(androidGame(stageGameIndex(stageAt(this.stageIndex))).category).fontSize(12).fontWeight(FontWeight.Medium)
            .fontColor(androidGame(stageGameIndex(stageAt(this.stageIndex))).color)
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      }.width('100%').margin({ top: 16 })
      Column() {
        ForEach([1, 2, 3], (star: number) => {
          Row({ space: 10 }) {
            starRow(star, 14)
            Text(this.target(star)).fontSize(15).fontColor($r('app.color.text_primary')).layoutWeight(1)
            if (this.best >= star) {
              Text('已达成').fontSize(12).fontWeight(FontWeight.Medium).fontColor(STAR_ON)
            }
          }.width('100%').height(36)
        }, (star: number) => `${star}`)
      }.width('100%').margin({ top: 16 }).padding({ left: 14, right: 14, top: 6, bottom: 6 }).borderRadius(16)
        .backgroundColor($r('app.color.page_background'))
        .accessibilityGroup(true)
        .accessibilityText(`一星${this.target(1)}，二星${this.target(2)}，三星${this.target(3)}，当前最佳 ${this.best} 星`)
      Text('达到一星即可通关并开放下一关，达到三星自动结束本局。')
        .width('100%').margin({ top: 10 }).fontSize(13).lineHeight(19).fontColor($r('app.color.text_secondary'))
      AndroidDialogAction({ labelText: '开始闯关', onAction: () => this.onStart() }).margin({ top: 20 })
      AndroidDialogAction({ labelText: '取消', actionKind: 'secondary', onAction: () => this.onCancel() })
        .margin({ top: 10 })
    }.width('100%').padding(24).alignItems(HorizontalAlign.Start)
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.DetailContent(); } })
  }
}

@Component
export struct StageResult {
  @Prop stageIndex: number = 0;
  @Prop stars: number = 0;
  @Prop best: number = 0;
  @Prop value: number = 0;
  @Prop coins: number = 0;
  @Prop hasNext: boolean = false;
  @Prop screenHeight: number = 0;
  onNext: () => void = (): void => {};
  onRetry: () => void = (): void => {};
  onMap: () => void = (): void => {};

  private title(): string {
    if (this.stars >= 3) { return '完美通关'; }
    return this.stars > 0 ? '闯关成功' : '未达成目标';
  }

  private valueText(): string {
    const unit = stageUnit(stageAt(this.stageIndex).gameId);
    if (unit === '分') { return `本局得分 ${androidLevel(this.value)}`; }
    if (unit === '个') { return `本局找到 ${androidLevel(this.value)} 个数字`; }
    return `本局完成 ${androidLevel(this.value)} 级`;
  }

  private nextGoal(): string {
    const stage = stageAt(this.stageIndex);
    const star = Math.min(3, Math.max(0, this.stars));
    if (star >= 3) { return '已获得全部星星'; }
    return `距离 ${star + 1} 星还差 ${Math.max(0, stage.stars[star] - androidLevel(this.value))}`;
  }

  @Builder
  private ResultContent() {
    Column() {
      Text(`第 ${this.stageIndex + 1} 关`).fontSize(14).fontColor($r('app.color.text_secondary'))
      Text(this.title()).margin({ top: 4 }).fontSize(24).fontWeight(FontWeight.Bold)
        .fontColor(this.stars > 0 ? $r('app.color.text_primary') : $r('app.color.danger'))
      Row() { starRow(this.stars, 44) }.margin({ top: 16 })
      Column({ space: 4 }) {
        Text(this.valueText()).fontSize(16).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        Text(this.nextGoal()).fontSize(13).fontColor($r('app.color.text_secondary'))
        if (this.coins > 0) {
          Row({ space: 4 }) {
            Image(media('ic_coin')).width(16).height(16).objectFit(ImageFit.Contain).accessibilityLevel('no')
            Text(`星级奖励 +${this.coins}`).fontSize(14).fontWeight(FontWeight.Medium).fontColor($r('app.color.coin_text'))
          }.margin({ top: 4 })
        }
      }.width('100%').margin({ top: 16 }).padding({ top: 14, bottom: 14 }).borderRadius(20)
        .backgroundColor($r('app.color.page_background'))
        .accessibilityGroup(true)
        .accessibilityText(`${this.title()}，获得 ${this.stars} 星，最佳 ${this.best} 星，${this.valueText()}` +
          (this.coins > 0 ? `，星级奖励 ${this.coins} 金币` : ''))
      if (this.stars > 0 && this.hasNext) {
        AndroidDialogAction({ labelText: '下一关', onAction: () => this.onNext() }).margin({ top: 20 })
        AndroidDialogAction({ labelText: '再玩一次', actionKind: 'secondary', onAction: () => this.onRetry() })
          .margin({ top: 10 })
        AndroidDialogAction({ labelText: '返回地图', actionKind: 'plain', onAction: () => this.onMap() })
          .margin({ top: 4 })
      } else {
        AndroidDialogAction({ labelText: this.stars > 0 ? '再玩一次' : '重试', onAction: () => this.onRetry() })
          .margin({ top: 20 })
        AndroidDialogAction({ labelText: '返回地图', actionKind: 'secondary', onAction: () => this.onMap() })
          .margin({ top: 10 })
      }
    }.width('100%').padding({ left: 24, right: 24, top: 28, bottom: 20 })
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.ResultContent(); } })
  }
}

@Component
export struct AndroidDiscovery {
  @Prop gameIndex: number = 0;
  @Prop screenHeight: number = 0;
  onContinue: () => void = (): void => {};

  @Builder
  private DiscoveryContent() {
    Column() {
      Text('发现新训练').fontSize(14).fontWeight(FontWeight.Medium).fontColor(STAR_ON)
      Text(androidGame(this.gameIndex).name).margin({ top: 6 }).fontSize(24).fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))
      Stack() {
        Image(media(androidGame(this.gameIndex).thumbnail)).width('100%').height('100%').padding(8)
          .objectFit(ImageFit.Contain).draggable(false).accessibilityLevel('no')
      }.width('100%').height(150).margin({ top: 16 }).borderRadius(20).clip(true)
        .backgroundColor($r('app.color.thumb_background'))
      Text(androidGame(this.gameIndex).category)
        .margin({ top: 14 }).fontSize(12).fontWeight(FontWeight.Medium).fontColor(androidGame(this.gameIndex).color)
        .height(22).padding({ left: 8, right: 8 }).borderRadius(8).backgroundColor(tint(androidGame(this.gameIndex).color, '1F'))
      Text(`${androidGame(this.gameIndex).tutorialText.join('，').replace(/\n/g, '')}。已加入自由训练。`)
        .width('100%').margin({ top: 10 }).textAlign(TextAlign.Center)
        .fontSize(14).lineHeight(20).fontColor($r('app.color.text_secondary'))
      AndroidDialogAction({ labelText: '去挑战', onAction: () => this.onContinue() }).margin({ top: 20 })
    }.width('100%').padding({ left: 24, right: 24, top: 28, bottom: 20 })
      .accessibilityGroup(false)
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.DiscoveryContent(); } })
  }
}
