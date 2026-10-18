import { GAMES, GameInfo, media } from '../core/GameCatalog';

// Android dp -> ArkUI vp; fonts are registered by the host Index.
// Text is copied from res/values-zh/{strings,dialog_game_strings,dialog_common_strings}.xml.
const ANDROID_BACKGROUND: string = '#E5F6FE';
const ANDROID_DIVIDER: string = '#D9D9D9';
const ANDROID_DIALOG_TEXT: string = '#182230';
const ANDROID_DIALOG_BODY: string = '#596579';
const ANDROID_DIALOG_BLUE: string = '#087CAF';
const ANDROID_DIALOG_BORDER: string = '#D8E3ED';
const ANDROID_DIALOG_DANGER: string = '#BD3045';

class AndroidImageMetrics {
  logicalWidth: number;
  logicalHeight: number;

  constructor(logicalWidth: number, logicalHeight: number) {
    this.logicalWidth = logicalWidth;
    this.logicalHeight = logicalHeight;
  }
}

// Original highest-density PNG dimensions / 4, including their transparent margins.
// CENTER_INSIDE must not enlarge these images to the viewport's bounds.
function androidImageMetrics(assetName: string): AndroidImageMetrics {
  switch (assetName) {
    case 'th1':
    case 'th4': return new AndroidImageMetrics(203.75, 192);
    case 'th2': return new AndroidImageMetrics(239, 192);
    case 'th3': return new AndroidImageMetrics(212.75, 192);
    case 'th5': return new AndroidImageMetrics(160, 192);
    case 'th6': return new AndroidImageMetrics(202.75, 192);
    case 'th7': return new AndroidImageMetrics(200.5, 192);
    case 'th8': return new AndroidImageMetrics(154, 192);
    case 'th9': return new AndroidImageMetrics(204, 192);
    case 'th10': return new AndroidImageMetrics(205.5, 192);
    case 'th11':
    case 'th12': return new AndroidImageMetrics(155, 192);
    case 'th13': return new AndroidImageMetrics(193, 192);
    case 'th14': return new AndroidImageMetrics(216, 192);
    case 'th15': return new AndroidImageMetrics(172.5, 192);
    case 'th16': return new AndroidImageMetrics(236, 192);
    case 'th17': return new AndroidImageMetrics(185.5, 192);
    case 'th18': return new AndroidImageMetrics(220.25, 192);
    case 'th19': return new AndroidImageMetrics(212.25, 192);
    case 'th20': return new AndroidImageMetrics(165.75, 192);
    case 'th21':
    case 'preview_laser':
    case 'preview_table': return new AndroidImageMetrics(360, 192);
    case 'game1_1':
    case 'game1_2':
    case 'game1_3': return new AndroidImageMetrics(152, 204);
    case 'game2_1': return new AndroidImageMetrics(204, 204);
    case 'game2_2': return new AndroidImageMetrics(279, 278.75);
    case 'game2_3': return new AndroidImageMetrics(204.25, 204.25);
    case 'game3_1':
    case 'game3_2':
    case 'game3_3': return new AndroidImageMetrics(213.5, 154.75);
    case 'game4_1':
    case 'game4_2':
    case 'game4_3': return new AndroidImageMetrics(152, 204.25);
    case 'game5_1': return new AndroidImageMetrics(152.25, 204);
    case 'game5_2': return new AndroidImageMetrics(120.5, 241);
    case 'game6_1':
    case 'game6_2':
    case 'game6_3': return new AndroidImageMetrics(202.75, 203.25);
    case 'game7_1': return new AndroidImageMetrics(244.25, 244.25);
    case 'game7_2':
    case 'game7_3': return new AndroidImageMetrics(244.25, 252.75);
    case 'game8_1': return new AndroidImageMetrics(100, 100);
    case 'game8_2':
    case 'game8_3':
    case 'game8_4': return new AndroidImageMetrics(204, 204);
    case 'game9_1': return new AndroidImageMetrics(140.5, 140.25);
    case 'game9_2': return new AndroidImageMetrics(140.5, 226.25);
    case 'game9_3': return new AndroidImageMetrics(146.5, 232);
    case 'game10_1': return new AndroidImageMetrics(139.75, 140);
    case 'game10_2': return new AndroidImageMetrics(142.25, 142);
    case 'game11_1':
    case 'game11_2':
    case 'game11_3': return new AndroidImageMetrics(287.25, 227);
    case 'game12_1':
    case 'game12_2':
    case 'game12_3': return new AndroidImageMetrics(287.25, 235.5);
    case 'game13_1':
    case 'game13_2': return new AndroidImageMetrics(172.5, 172.75);
    case 'game13_3': return new AndroidImageMetrics(171, 171.25);
    case 'game14_1': return new AndroidImageMetrics(175.25, 174.75);
    case 'game14_2': return new AndroidImageMetrics(175.25, 184.75);
    case 'game14_3': return new AndroidImageMetrics(184.5, 184.5);
    case 'game15_1': return new AndroidImageMetrics(194.75, 168.75);
    case 'game15_2': return new AndroidImageMetrics(195.25, 212.5);
    case 'game15_3': return new AndroidImageMetrics(199.75, 184.75);
    case 'game16_1':
    case 'game16_2': return new AndroidImageMetrics(159, 208);
    case 'game16_3': return new AndroidImageMetrics(174.5, 223.5);
    case 'game17_1':
    case 'game17_2': return new AndroidImageMetrics(253, 249.5);
    case 'game17_3': return new AndroidImageMetrics(259.25, 249.5);
    case 'game18_1': return new AndroidImageMetrics(184, 277.25);
    case 'game18_2': return new AndroidImageMetrics(189.5, 277.25);
    case 'game19_1':
    case 'game19_2': return new AndroidImageMetrics(224.25, 269);
    case 'game20_1':
    case 'game20_2': return new AndroidImageMetrics(266.75, 378.25);
    case 'game21_1':
    case 'game21_2': return new AndroidImageMetrics(203.75, 204);
    case 'tutorial_laser_1':
    case 'tutorial_laser_2':
    case 'tutorial_table_1':
    case 'tutorial_table_2':
    case 'tutorial_table_3': return new AndroidImageMetrics(250, 350);
    default: return new AndroidImageMetrics(192, 192);
  }
}

function androidGame(gameIndex: number): GameInfo {
  return GAMES[Math.max(0, Math.min(GAMES.length - 1, Math.floor(gameIndex)))];
}

function androidLevel(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;
}

@Component
export struct AndroidHome {
  // GAMES array order (23 entries), highest reached, not highest completed.
  @Prop records: number[] = [];
  @Prop lastGame: number = -1;
  onSelect: (index: number) => void = (_index: number): void => {};
  @State private viewportWidth: number = 360;

  private recordAt(index: number): number {
    return androidLevel(index < this.records.length ? this.records[index] : 0);
  }

  private thumbnailScale(game: GameInfo): number {
    return Math.min(1, this.viewportWidth / androidImageMetrics(game.thumbnail).logicalWidth);
  }

  @Builder
  private GameRow(game: GameInfo, index: number) {
    Column() {
      Stack({ alignContent: Alignment.Top }) {
        Image(media(game.thumbnail))
          .width(androidImageMetrics(game.thumbnail).logicalWidth * this.thumbnailScale(game))
          .height(androidImageMetrics(game.thumbnail).logicalHeight * this.thumbnailScale(game))
          .objectFit(ImageFit.Contain)
          .position({ x: (this.viewportWidth - androidImageMetrics(game.thumbnail).logicalWidth *
            this.thumbnailScale(game)) / 2, y: 0 })
          .draggable(false)
          .accessibilityLevel('no')
        Column() {
          Row() {
            Text(game.name)
              .fontFamily('RobotoLight').fontSize(18).fontColor('#000000')
              .layoutWeight(1).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
            Text('等级')
              .fontFamily('RobotoLight').fontSize(16).fontColor('#000000').margin({ right: 4 })
            Text(`${this.recordAt(index)}`)
              .fontFamily('RobotoLight').fontSize(18).fontColor('#000000')
              .width(32).height(32).textAlign(TextAlign.Center)
              .backgroundColor('#F0FAFF').borderRadius(16)
              .border({ width: 1, color: ANDROID_DIVIDER, radius: 16 }).borderRadius(16).clip(true)
          }.width('100%').height(32).padding({ left: 16, right: 16 })
          Text(game.category)
            .fontFamily('RobotoBold').fontWeight(FontWeight.Bold).fontSize(12).fontColor(game.color)
            .lineHeight(16).padding({ left: 16, right: 16, bottom: 4 })
        }.width('100%').height(52).alignItems(HorizontalAlign.Start)
          .position({ x: 0, y: Math.max(0, Math.floor(this.viewportWidth / 1.875) - 52) })
        if (this.lastGame === index) {
          Rect().width(6).height('100%').fill('#FFC109').position({ x: 0, y: 0 })
        }
      }.width('100%').height(Math.floor(this.viewportWidth / 1.875))
        .backgroundColor(ANDROID_BACKGROUND).clip(true)
      Rect().width('100%').height(1).fill(ANDROID_DIVIDER)
    }.width('100%').onClick(() => this.onSelect(index))
      .accessibilityText(`${game.name}，${game.category}，等级 ${this.recordAt(index)}`)
      .accessibilityGroup(true)
  }

  build() {
    Column() {
      Row() {
        Text('记忆训练大师')
          .fontFamily('RobotoMedium').fontSize(20).fontColor('#000000')
          .accessibilityText('记忆训练大师')
      }.width('100%').height(45).justifyContent(FlexAlign.Center)
        .padding({ left: 15, right: 15 }).backgroundColor('#FFFFFF')
      Rect().width('100%').height(1).fill(ANDROID_DIVIDER)
      List({ space: 0 }) {
        ForEach(GAMES, (game: GameInfo, index: number) => {
          ListItem() { this.GameRow(game, index) }
        }, (game: GameInfo) => `${game.id}`)
      }.width('100%').layoutWeight(1).scrollBar(BarState.Off)
    }.width('100%').height('100%').backgroundColor(ANDROID_BACKGROUND)
      .onAreaChange((_oldArea: Area, newArea: Area) => {
        const measured: number = Number(newArea.width);
        if (measured > 0) { this.viewportWidth = measured; }
      })
  }
}

@Component
export struct AndroidTutorial {
  @Prop @Watch('resetPages') gameIndex: number = 0;
  @Prop fromPause: boolean = false;
  onExit: () => void = (): void => {};
  onPlay: () => void = (): void => {};
  @State private pageIndex: number = 0;
  @State private viewportWidth: number = 360;
  @State private tutorialAreaHeight: number = 560;
  private pager: SwiperController = new SwiperController();

  private resetPages(): void {
    this.pageIndex = 0;
    this.pager.changeIndex(0, false);
  }

  private squareExtent(): number {
    // On short screens shrink only, and keep clear of the 70dp text / 72dp dots.
    return Math.max(0, Math.min(this.viewportWidth, this.tutorialAreaHeight - 144));
  }

  private imageScale(assetName: string): number {
    const metrics: AndroidImageMetrics = androidImageMetrics(assetName);
    return Math.min(1, this.squareExtent() / metrics.logicalWidth,
      this.squareExtent() / metrics.logicalHeight);
  }

  private nextOrPlay(): void {
    if (this.pageIndex < androidGame(this.gameIndex).tutorialImages.length - 1) {
      this.pager.showNext();
    } else {
      this.onPlay();
    }
  }

  build() {
    Column() {
      Row() {
        Button({ type: ButtonType.Normal }) {
          Image(media('ic_arrow_back_white')).width(29).height(29)
            // Preserve the original PNG alpha/geometry; force RGB to black.
            .objectFit(ImageFit.Contain).colorFilter([
              0, 0, 0, 0, 0,
              0, 0, 0, 0, 0,
              0, 0, 0, 0, 0,
              0, 0, 0, 1, 0
            ]).accessibilityLevel('no')
        }.width(45).height(45).padding(8).margin({ left: 8, right: 8 })
          .borderRadius(0).backgroundColor(Color.Transparent)
          .accessibilityText('返回').onClick(() => this.onExit())
      }.width('100%').height(45)
      Stack() {
        Swiper(this.pager) {
          ForEach(androidGame(this.gameIndex).tutorialImages, (assetName: string) => {
            Stack() {
              Image(media(assetName))
                .width(androidImageMetrics(assetName).logicalWidth * this.imageScale(assetName))
                .height(androidImageMetrics(assetName).logicalHeight * this.imageScale(assetName))
                .objectFit(ImageFit.Contain).draggable(false)
            }.width('100%').height('100%')
          }, (assetName: string) => assetName)
        }.width('100%').height('100%').index(this.pageIndex).loop(false)
          .indicator(false).duration(250)
          .onChange((index: number) => { this.pageIndex = index; })
        Text(androidGame(this.gameIndex).tutorialText[this.pageIndex])
          .width('100%').height(70).padding({ left: 32, right: 32 })
          .fontFamily('RobotoRegular').fontSize(20).fontStyle(FontStyle.Italic).fontColor('#000000')
          .textAlign(TextAlign.Center).maxLines(3).minFontSize(12).maxFontSize(20)
          .heightAdaptivePolicy(TextHeightAdaptivePolicy.MIN_FONT_SIZE_FIRST)
          .position({ x: 0, y: 0 }).hitTestBehavior(HitTestMode.None)
        Row({ space: 4 }) {
          ForEach(androidGame(this.gameIndex).tutorialImages, (_assetName: string, index: number) => {
            Circle().width(8).height(8).fill(index === this.pageIndex ? '#8C8C8C' : ANDROID_DIVIDER)
          }, (_assetName: string, index: number) => `${index}`)
        }.width('100%').height(72).padding(32).justifyContent(FlexAlign.Center)
          .position({ x: 0, y: Math.max(0, this.tutorialAreaHeight - 72) }).hitTestBehavior(HitTestMode.None)
      }.width('100%').layoutWeight(1).clip(true)
        .onAreaChange((_oldArea: Area, newArea: Area) => {
          const measured: number = Number(newArea.height);
          if (measured > 0) { this.tutorialAreaHeight = measured; }
        })
      // INVISIBLE, not GONE: pause tutorial reserves the entire original action region.
      Column() {
        Rect().width('100%').height(1).fill(ANDROID_DIVIDER)
        Row() {
          Button(this.pageIndex < androidGame(this.gameIndex).tutorialImages.length - 1 ? '下一步' : '开始',
            { type: ButtonType.Normal })
            .height(42).padding({ left: 36, right: 36 }).borderRadius(2)
            .fontFamily('RobotoRegular').fontSize(20).fontWeight(FontWeight.Normal).fontColor('#FFFFFF')
            .backgroundColor('#03A9F4').onClick(() => this.nextOrPlay())
        }.width('100%').padding({ top: 24, bottom: 24 }).justifyContent(FlexAlign.Center)
          .backgroundColor('#FFFFFF')
      }.width('100%').visibility(this.fromPause ? Visibility.Hidden : Visibility.Visible)
    }.width('100%').height('100%').backgroundColor(ANDROID_BACKGROUND)
      .onAreaChange((_oldArea: Area, newArea: Area) => {
        const measured: number = Number(newArea.width);
        if (measured > 0) { this.viewportWidth = measured; }
      })
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
  @BuilderParam content: () => void = androidEmptyDialogContent;

  private maximumCardHeight(): number {
    const available: number = this.screenHeight > 0 ? this.screenHeight : this.measuredViewportHeight;
    return Math.max(1, (available > 0 ? available : 800) - 48);
  }

  build() {
    Stack() {
      // A sibling backdrop consumes outside taps without suppressing the card's
      // descendants. HitTestMode.Block on an ancestor also blocks its buttons.
      Column() {}.width('100%').height('100%').backgroundColor('#73000000')
        .onClick(() => {})
      Column() {
        Scroll() {
          this.content()
        }.width('100%').constraintSize({ maxWidth: 420, maxHeight: this.maximumCardHeight() })
          .scrollBar(BarState.Off).align(Alignment.Top)
          .backgroundColor('#FFFFFF').borderRadius(20).clip(true)
      }.width('100%').height('100%').padding({ left: 16, right: 16 })
        .justifyContent(FlexAlign.Center)
    }.width('100%').height('100%')
      .onClick(() => {})
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
  @Prop lightTypeface: boolean = false;
  onAction: () => void = (): void => {};

  build() {
    Button({ type: ButtonType.Normal }) {
      Text(this.labelText).fontFamily(this.lightTypeface ? 'RobotoLight' : 'RobotoMedium')
        .fontSize(16).fontWeight(FontWeight.Normal)
        .fontColor(this.actionKind === 'primary' ? '#FFFFFF' :
          (this.actionKind === 'danger' ? ANDROID_DIALOG_DANGER : ANDROID_DIALOG_BLUE))
        .textAlign(TextAlign.Center)
    }.width('100%').height(52).padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .backgroundColor(this.actionKind === 'primary' ? ANDROID_DIALOG_BLUE : Color.Transparent)
      .borderRadius(12)
      .border({ width: this.actionKind === 'secondary' ? 1 : 0, color: ANDROID_DIALOG_BORDER, radius: 12 })
      .borderRadius(12).clip(true)
      .onClick(() => this.onAction()).accessibilityText(this.labelText)
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

  @Builder
  private PauseContent() {
    Column() {
      Text('游戏已暂停').width('100%').fontFamily('RobotoLight').fontSize(22).fontColor(ANDROID_DIALOG_TEXT)
      Text('准备好后可继续游戏。结束本局将进入成绩结算。')
        .width('100%').fontFamily('RobotoRegular').fontSize(15).fontColor(ANDROID_DIALOG_BODY)
        .lineHeight(20).margin({ top: 8 })
      AndroidDialogAction({ labelText: '继续游戏', lightTypeface: true, onAction: () => this.onResume() })
        .margin({ top: 20 })
      AndroidDialogAction({ labelText: '重新开始本局', actionKind: 'secondary', lightTypeface: true,
        onAction: () => this.onRestart() }).margin({ top: 8 })
      AndroidDialogAction({ labelText: '查看玩法教程', actionKind: 'secondary', lightTypeface: true,
        onAction: () => this.onTutorial() }).margin({ top: 8 })
      AndroidDialogAction({ labelText: '返回游戏列表', actionKind: 'secondary', lightTypeface: true,
        onAction: () => this.onHome() }).margin({ top: 8 })
      AndroidDialogAction({ labelText: '结束本局并查看成绩', actionKind: 'danger', lightTypeface: true,
        onAction: () => this.onEnd() }).margin({ top: 8 })
    }.width('100%').padding(24).alignItems(HorizontalAlign.Start)
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
      Text(this.title).width('100%').fontFamily('RobotoRegular').fontSize(22).fontColor(ANDROID_DIALOG_TEXT)
      Text(`本局到达第 ${androidLevel(this.level)} 关`)
        .width('100%').fontFamily('RobotoMedium').fontSize(22).fontColor(ANDROID_DIALOG_BLUE)
        .margin({ top: 20 })
      Text(`最佳到达第 ${androidLevel(this.best)} 关`)
        .width('100%').fontFamily('RobotoRegular').fontSize(15).fontColor(ANDROID_DIALOG_BODY)
        .lineHeight(20).margin({ top: 6 })
      AndroidDialogAction({ labelText: '再玩一次', onAction: () => this.onReplay() }).margin({ top: 24 })
      AndroidDialogAction({ labelText: '返回游戏列表', actionKind: 'secondary', onAction: () => this.onHome() })
        .margin({ top: 10 })
      Text('试试其他游戏').width('100%').fontFamily('RobotoLight').fontSize(15)
        .fontColor(ANDROID_DIALOG_BODY).lineHeight(20).margin({ top: 24 })
      Button({ type: ButtonType.Normal }) {
        // Android timeout uses FIT_CENTER here (unlike the home CENTER_INSIDE).
        Image(media(androidGame(this.recommendedIndex()).thumbnail))
          .width('100%').height('100%').objectFit(ImageFit.Contain).draggable(false)
      }.width('100%').height(112).padding(8).margin({ top: 8 })
        .backgroundColor('#FFFFFF').border({ width: 1, color: ANDROID_DIALOG_BORDER, radius: 12 })
        .borderRadius(12).clip(true)
        .onClick(() => this.onRecommend(this.recommendedIndex()))
        .accessibilityText(`试试其他游戏，${androidGame(this.recommendedIndex()).name}`)
      Text(androidGame(this.recommendedIndex()).name)
        .width('100%').fontFamily('RobotoRegular').fontSize(15).fontColor(ANDROID_DIALOG_BODY)
        .lineHeight(20).textAlign(TextAlign.Center).margin({ top: 6 })
    }.width('100%').padding(24).alignItems(HorizontalAlign.Start)
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
      Text('再试一次？').width('100%').fontFamily('RobotoMedium').fontSize(22).fontColor(ANDROID_DIALOG_TEXT)
      Text('请选择接下来的操作，无需倒计时内做决定。')
        .width('100%').fontFamily('RobotoRegular').fontSize(15).fontColor(ANDROID_DIALOG_BODY)
        .lineHeight(20).margin({ top: 8 })
      AndroidDialogAction({ labelText: '重新开始本局', onAction: () => this.onRestart() }).margin({ top: 20 })
      AndroidDialogAction({ labelText: '结束本局并查看成绩', actionKind: 'danger', onAction: () => this.onEnd() })
        .margin({ top: 8 })
    }.width('100%').padding(24).alignItems(HorizontalAlign.Start)
  }

  build() {
    AndroidDialogFrame({ screenHeight: this.screenHeight, content: () => { this.FailureContent(); } })
  }
}
