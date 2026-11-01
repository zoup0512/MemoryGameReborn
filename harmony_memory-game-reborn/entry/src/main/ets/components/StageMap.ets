import { GAMES, GameInfo, media } from '../core/GameCatalog';
import { CHAPTERS, ChapterInfo, STAGES, StageInfo } from '../core/StageCatalog';

// Level map: chapter banners followed by a winding path of stage nodes.
// Nodes are absolutely positioned inside one Stack per chapter.
const ROW: number = 104;        // vertical distance between stage centers
const NODE: number = 60;        // normal node diameter
const BIG_NODE: number = 72;    // chapter challenge diameter
const BANNER: number = 84;      // chapter banner block height, including margins
const PAD: number = 24;         // space below the last node of a chapter
const LANES: number[] = [0.22, 0.5, 0.78, 0.5];
const STAR_ON: string = '#FFB300';
const STAR_OFF: string = '#33808A99';
const PATH_DONE: string = '#FFB300';
const PATH_LOCKED: string = '#2A808A99';

class Segment {
  key: string = '';
  x1: number = 0;
  y1: number = 0;
  x2: number = 0;
  y2: number = 0;
  done: boolean = false;
}

function gameOf(stage: StageInfo): GameInfo {
  const found = GAMES.find((game: GameInfo) => game.id === stage.gameId);
  return found !== undefined ? found : GAMES[0];
}

function chapterStages(chapter: ChapterInfo): StageInfo[] {
  return STAGES.slice(chapter.first, chapter.first + chapter.count);
}

@Component
export struct StageMap {
  // Best stars per STAGES index; missing entries count as 0.
  @Prop stars: number[] = [];
  @Prop coins: number = 0;
  @Prop current: number = 0;
  onOpen: (stage: number) => void = (_stage: number): void => {};
  @State private mapWidth: number = 0;
  @State private pulse: boolean = false;
  private viewport: number = 0;
  private scrolled: boolean = false;
  private scroller: Scroller = new Scroller();

  aboutToAppear(): void {
    this.scrolled = false;
  }

  private starsAt(index: number): number {
    const value = index >= 0 && index < this.stars.length ? this.stars[index] : 0;
    return Number.isInteger(value) ? Math.max(0, Math.min(3, value)) : 0;
  }

  private open(index: number): boolean {
    return index === 0 || this.starsAt(index - 1) >= 1;
  }

  private totalStars(): number {
    return STAGES.reduce((sum: number, stage: StageInfo) => sum + this.starsAt(stage.index), 0);
  }

  private chapterStars(chapter: ChapterInfo): number {
    return chapterStages(chapter).reduce((sum: number, stage: StageInfo) => sum + this.starsAt(stage.index), 0);
  }

  private x(local: number): number {
    return LANES[local % LANES.length] * this.mapWidth;
  }

  private y(local: number): number {
    return ROW / 2 + local * ROW;
  }

  private nodeSize(stage: StageInfo): number {
    return stage.challenge ? BIG_NODE : NODE;
  }

  private chapterHeight(chapter: ChapterInfo): number {
    return chapter.count * ROW + PAD;
  }

  private segments(chapter: ChapterInfo): Segment[] {
    const result: Segment[] = [];
    for (let local = 0; local + 1 < chapter.count; local++) {
      const segment = new Segment();
      segment.key = `${chapter.first + local}-${this.mapWidth}`;
      segment.x1 = this.x(local); segment.y1 = this.y(local);
      segment.x2 = this.x(local + 1); segment.y2 = this.y(local + 1);
      segment.done = this.starsAt(chapter.first + local) >= 1;
      result.push(segment);
    }
    return result;
  }

  // Content offset of a stage's center, used to center the current stage.
  private stageOffset(index: number): number {
    let offset = 0;
    for (const chapter of CHAPTERS) {
      if (index < chapter.first + chapter.count) {
        return offset + BANNER + this.y(index - chapter.first);
      }
      offset += BANNER + this.chapterHeight(chapter);
    }
    return offset;
  }

  private scrollToCurrent(): void {
    if (this.scrolled || this.viewport <= 0 || this.mapWidth <= 0) { return; }
    this.scrolled = true;
    const target = Math.max(0, this.stageOffset(this.current) - this.viewport / 2);
    this.scroller.scrollTo({ xOffset: 0, yOffset: target });
  }

  private nodeColor(stage: StageInfo): ResourceColor {
    if (!this.open(stage.index)) { return $r('app.color.chip_background'); }
    return stage.index === this.current && this.starsAt(stage.index) === 0 ? $r('app.color.brand') : gameOf(stage).color;
  }

  private description(stage: StageInfo): string {
    const name = this.open(stage.index) ? gameOf(stage).name : (stage.intro ? '神秘训练' : '未开放');
    const kind = stage.challenge ? '，章节挑战' : '';
    const state = !this.open(stage.index) ? '，未开放' :
      `，${this.starsAt(stage.index)} 星${stage.index === this.current ? '，当前关卡' : ''}`;
    return `第 ${stage.index + 1} 关，${name}${kind}${state}`;
  }

  @Builder
  private Banner(chapter: ChapterInfo, index: number) {
    Row() {
      Column({ space: 2 }) {
        Text(`第 ${index + 1} 章`).fontSize(12).fontWeight(FontWeight.Medium).fontColor('#CCFFFFFF')
        Text(chapter.title).fontSize(19).fontWeight(FontWeight.Bold).fontColor('#FFFFFF')
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      if (this.open(chapter.first)) {
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.star_fill')).fontSize(16).fontColor(['#FFE082'])
          Text(`${this.chapterStars(chapter)} / ${chapter.count * 3}`).fontSize(14).fontWeight(FontWeight.Medium)
            .fontColor('#FFFFFF')
        }
      } else {
        SymbolGlyph($r('sys.symbol.lock_fill')).fontSize(20).fontColor(['#FFFFFF'])
      }
    }.width('100%').height(64).margin({ top: 12, bottom: 8 }).padding({ left: 18, right: 18 })
      .borderRadius(20).opacity(this.open(chapter.first) ? 1 : 0.6)
      .linearGradient({ angle: 90, colors: index === 0 ? [['#03A9F4', 0], ['#26C6DA', 1]] :
        index === 1 ? [['#7E57C2', 0], ['#5C6BC0', 1]] : [['#F4511E', 0], ['#FFB300', 1]] })
      .accessibilityGroup(true)
      .accessibilityText(`第 ${index + 1} 章 ${chapter.title}` +
        (this.open(chapter.first) ? `，已获得 ${this.chapterStars(chapter)} 星` : '，未开放'))
  }

  @Builder
  private NodeFace(stage: StageInfo) {
    if (!this.open(stage.index)) {
      if (stage.intro) {
        Text('?').fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_tertiary'))
      } else {
        SymbolGlyph($r('sys.symbol.lock_fill')).fontSize(20).fontColor([$r('app.color.text_tertiary')])
      }
    } else if (stage.intro) {
      Image(media(gameOf(stage).thumbnail)).width('100%').height('100%').padding(6)
        .borderRadius(this.nodeSize(stage) / 2).backgroundColor('#FFFFFF')
        .objectFit(ImageFit.Contain).draggable(false)
    } else {
      Column() {
        if (stage.challenge) {
          SymbolGlyph($r('sys.symbol.crown_fill')).fontSize(18).fontColor(['#FFE082'])
        }
        Text(`${stage.index + 1}`).fontSize(stage.challenge ? 20 : 22).fontFamily('RobotoBold').fontColor('#FFFFFF')
      }.justifyContent(FlexAlign.Center)
    }
  }

  @Builder
  private Node(stage: StageInfo, local: number) {
    Column() {
      Stack() {
        this.NodeFace(stage)
      }.width(this.nodeSize(stage)).height(this.nodeSize(stage)).borderRadius(this.nodeSize(stage) / 2)
        .backgroundColor(this.nodeColor(stage))
        .border({ width: 4, color: this.open(stage.index) ? '#FFFFFF' : Color.Transparent })
        .shadow(this.open(stage.index) ? { radius: 10, color: '#40000000', offsetY: 3 } :
          { radius: 0, color: Color.Transparent })
        .scale(stage.index === this.current && this.starsAt(stage.index) === 0 && this.pulse ?
          { x: 1.1, y: 1.1 } : { x: 1, y: 1 })
        .animation(stage.index === this.current ?
          { duration: 800, curve: Curve.EaseInOut, iterations: -1, playMode: PlayMode.Alternate } :
          { duration: 0 })
      if (this.open(stage.index)) {
        Row({ space: 2 }) {
          ForEach([1, 2, 3], (star: number) => {
            SymbolGlyph($r('sys.symbol.star_fill')).fontSize(14)
              .fontColor([star <= this.starsAt(stage.index) ? STAR_ON : STAR_OFF])
          }, (star: number) => `${star}`)
        }.margin({ top: 4 })
      }
    }.width(BIG_NODE + 24)
      .position({ x: this.x(local) - (BIG_NODE + 24) / 2, y: this.y(local) - this.nodeSize(stage) / 2 })
      .clickEffect({ level: ClickEffectLevel.LIGHT, scale: 0.92 })
      .onClick(() => { if (this.open(stage.index)) { this.onOpen(stage.index); } })
      .accessibilityGroup(true)
      .accessibilityText(this.description(stage))
  }

  @Builder
  private ChapterPath(chapter: ChapterInfo) {
    Stack() {
      if (this.mapWidth > 0) {
        ForEach(this.segments(chapter), (segment: Segment) => {
          Line().width('100%').height(this.chapterHeight(chapter))
            .startPoint([segment.x1, segment.y1]).endPoint([segment.x2, segment.y2])
            .stroke(segment.done ? PATH_DONE : PATH_LOCKED).strokeWidth(8).strokeLineCap(LineCapStyle.Round)
            .strokeDashArray(segment.done ? [] : [2, 14])
            .position({ x: 0, y: 0 }).hitTestBehavior(HitTestMode.None)
        }, (segment: Segment) => `${segment.key}-${segment.done}`)
        ForEach(chapterStages(chapter), (stage: StageInfo, local: number) => {
          this.Node(stage, local)
        }, (stage: StageInfo) => `${stage.index}-${this.starsAt(stage.index)}-${this.current}-${this.mapWidth}`)
      }
    }.width('100%').height(this.chapterHeight(chapter))
  }

  build() {
    Column() {
      Row() {
        Column() {
          Text('脑力训练大师').fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          Text(`闯关进度 第 ${Math.min(STAGES.length, this.current + 1)} 关 · ${this.totalStars()} / ${STAGES.length * 3} 星`)
            .fontSize(13).fontColor($r('app.color.text_secondary')).margin({ top: 2 })
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Row({ space: 6 }) {
          Image(media('ic_coin')).width(20).height(20).objectFit(ImageFit.Contain)
            .accessibilityLevel('no')
          Text(`${Math.max(0, Math.floor(this.coins))}`).fontSize(16).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.coin_text'))
        }.height(36).padding({ left: 10, right: 14 }).borderRadius(18)
          .backgroundColor($r('app.color.coin_background'))
          .accessibilityGroup(true)
          .accessibilityText(`金币余额 ${Math.max(0, Math.floor(this.coins))}`)
      }.width('100%').padding({ left: 20, right: 16, top: 16, bottom: 8 })
      Scroll(this.scroller) {
        Column() {
          ForEach(CHAPTERS, (chapter: ChapterInfo, index: number) => {
            this.Banner(chapter, index)
            this.ChapterPath(chapter)
          }, (chapter: ChapterInfo) => chapter.title)
        }.width('100%').padding({ left: 16, right: 16, bottom: 24 })
          .onAreaChange((_old: Area, area: Area) => {
            const width = Number(area.width) - 32;
            if (width > 0 && width !== this.mapWidth) { this.mapWidth = width; }
            this.scrollToCurrent();
          })
      }.width('100%').layoutWeight(1).scrollBar(BarState.Off).edgeEffect(EdgeEffect.Spring)
        .onAreaChange((_old: Area, area: Area) => {
          this.viewport = Number(area.height);
          this.scrollToCurrent();
        })
    }.width('100%').height('100%')
    .onAppear(() => { this.pulse = true; })
  }
}
