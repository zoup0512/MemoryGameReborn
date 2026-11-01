import { GAMES } from '../core/GameCatalog';
import { BrainProfile, DIMENSIONS, DimensionInfo, DimensionScore, GameStat, brainTitle, buildProfile } from '../core/BrainProfile';

const RADAR_ANIMATION_MS: number = 700;
const RECENT_COLOR: string = '#FF9800';

class RadarPalette {
  grid: string;
  band: string;
  text: string;
  muted: string;
  fill: string;
  stroke: string;
  ring: string;

  constructor(dark: boolean) {
    this.grid = dark ? '#33404D' : '#DCE4EC';
    this.band = dark ? '#151B22' : '#F5F9FC';
    this.text = dark ? '#F2F4F7' : '#13171C';
    this.muted = dark ? '#A3ADBA' : '#5F6B7A';
    this.fill = dark ? 'rgba(61,155,233,0.32)' : 'rgba(2,119,189,0.22)';
    this.stroke = dark ? '#3D9BE9' : '#0277BD';
    this.ring = dark ? '#1B2129' : '#FFFFFF';
  }
}

class RadarPoint {
  x: number;
  y: number;

  constructor(x: number, y: number) {
    this.x = x;
    this.y = y;
  }
}

@Component
export struct BrainHole {
  @Prop @Watch('rebuild') stats: GameStat[] = [];
  @Prop @Watch('rebuild') unlockedIds: number[] = [];
  @Prop @Watch('draw') darkMode: boolean = false;
  onBack: () => void = (): void => {};
  onPractice: (index: number) => void = (_index: number): void => {};
  @State private profile: BrainProfile = new BrainProfile();
  @State @Watch('draw') private selected: number = 0;
  @State private chartWidth: number = 320;
  private context: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  private ready: boolean = false;
  private progress: number = 0;
  private timer: number = -1;

  aboutToAppear(): void {
    this.rebuild();
  }

  aboutToDisappear(): void {
    this.stopAnimation();
  }

  private rebuild(): void {
    this.profile = buildProfile(this.stats, this.unlockedIds);
    if (this.profile.strongest >= 0 && this.progress === 0) { this.selected = this.profile.strongest; }
    this.startAnimation();
  }

  private stopAnimation(): void {
    if (this.timer >= 0) { clearInterval(this.timer); this.timer = -1; }
  }

  private startAnimation(): void {
    this.stopAnimation();
    const started = Date.now();
    this.progress = 0;
    this.timer = setInterval(() => {
      const t = Math.min(1, (Date.now() - started) / RADAR_ANIMATION_MS);
      this.progress = 1 - Math.pow(1 - t, 3);
      this.draw();
      if (t >= 1) { this.stopAnimation(); }
    }, 16);
  }

  private dimension(): DimensionScore {
    return this.profile.dimensions.length > this.selected ? this.profile.dimensions[this.selected] : new DimensionScore();
  }

  private info(index: number): DimensionInfo {
    return DIMENSIONS[Math.max(0, Math.min(DIMENSIONS.length - 1, index))];
  }

  private chartHeight(): number {
    return Math.min(360, Math.round(this.chartWidth * 0.86));
  }

  private center(): RadarPoint {
    return new RadarPoint(this.chartWidth / 2, this.chartHeight() / 2 + 4);
  }

  private radius(): number {
    return Math.max(40, Math.min(this.chartWidth, this.chartHeight()) / 2 - 46);
  }

  private vertex(index: number, fraction: number): RadarPoint {
    const angle = -Math.PI / 2 + index * Math.PI * 2 / DIMENSIONS.length;
    const c = this.center();
    const r = this.radius() * fraction;
    return new RadarPoint(c.x + Math.cos(angle) * r, c.y + Math.sin(angle) * r);
  }

  private px(vp: number): number {
    return Math.round(this.getUIContext().vp2px(vp));
  }

  private polygon(values: number[]): void {
    const c = this.context;
    c.beginPath();
    values.forEach((value: number, index: number) => {
      const point = this.vertex(index, Math.max(0, Math.min(100, value)) / 100);
      if (index === 0) { c.moveTo(point.x, point.y); } else { c.lineTo(point.x, point.y); }
    });
    c.closePath();
  }

  private draw(): void {
    if (!this.ready) { return; }
    const c = this.context;
    const palette = new RadarPalette(this.darkMode);
    const count = DIMENSIONS.length;
    c.clearRect(0, 0, this.chartWidth, this.chartHeight());
    for (let ring = 4; ring >= 1; ring--) {
      this.polygon(DIMENSIONS.map((): number => ring * 25));
      c.fillStyle = ring % 2 === 0 ? palette.band : palette.ring;
      c.fill();
      c.strokeStyle = palette.grid; c.lineWidth = 1; c.setLineDash([]); c.stroke();
    }
    const centre = this.center();
    for (let i = 0; i < count; i++) {
      const end = this.vertex(i, 1);
      c.beginPath(); c.moveTo(centre.x, centre.y); c.lineTo(end.x, end.y);
      c.strokeStyle = palette.grid; c.stroke();
    }
    const scores = this.profile.dimensions.map((item: DimensionScore) => item.score * this.progress);
    const recent = this.profile.dimensions.map((item: DimensionScore) => item.recent * this.progress);
    if (scores.length === count) {
      if (recent.some((value: number) => value > 0)) {
        this.polygon(recent);
        c.strokeStyle = RECENT_COLOR; c.lineWidth = 2; c.setLineDash([6, 4]); c.stroke();
        c.setLineDash([]);
      }
      this.polygon(scores);
      c.fillStyle = palette.fill; c.fill();
      c.strokeStyle = palette.stroke; c.lineWidth = 2.5; c.stroke();
      scores.forEach((value: number, index: number) => {
        const point = this.vertex(index, Math.max(0, Math.min(100, value)) / 100);
        const size = index === this.selected ? 7 : 4.5;
        c.beginPath(); c.arc(point.x, point.y, size, 0, Math.PI * 2);
        c.fillStyle = this.info(index).color; c.fill();
        c.lineWidth = 2; c.strokeStyle = palette.ring; c.stroke();
      });
    }
    c.textAlign = 'center'; c.textBaseline = 'middle';
    for (let i = 0; i < count; i++) {
      const label = this.vertex(i, 1);
      const offset = this.labelOffset(i);
      const chosen = i === this.selected;
      c.font = `${chosen ? 'bold ' : ''}${this.px(chosen ? 14 : 13)}px sans-serif`;
      c.fillStyle = chosen ? this.info(i).color : palette.text;
      c.fillText(this.info(i).name, label.x + offset.x, label.y + offset.y - 8);
      c.font = `bold ${this.px(12)}px sans-serif`;
      c.fillStyle = palette.muted;
      const value = this.profile.dimensions.length > i ? this.profile.dimensions[i].score : 0;
      c.fillText(`${Math.round(value * this.progress)}`, label.x + offset.x, label.y + offset.y + 9);
    }
  }

  private labelOffset(index: number): RadarPoint {
    const angle = -Math.PI / 2 + index * Math.PI * 2 / DIMENSIONS.length;
    return new RadarPoint(Math.cos(angle) * 30, Math.sin(angle) * 24);
  }

  private pick(x: number, y: number): void {
    let nearest = -1;
    let distance = 48;
    for (let i = 0; i < DIMENSIONS.length; i++) {
      const point = this.vertex(i, 1);
      const offset = this.labelOffset(i);
      const d = Math.hypot(point.x + offset.x * 0.6 - x, point.y + offset.y * 0.6 - y);
      if (d < distance) { distance = d; nearest = i; }
    }
    if (nearest >= 0) { this.selected = nearest; }
  }

  @Builder
  private Stat(value: string, label: string) {
    Column({ space: 2 }) {
      Text(value).fontSize(20).fontWeight(FontWeight.Bold).fontColor('#FFFFFF')
      Text(label).fontSize(12).fontColor('#D9FFFFFF')
    }.layoutWeight(1).accessibilityGroup(true).accessibilityText(`${label} ${value}`)
  }

  @Builder
  private Hero() {
    Column() {
      Row() {
        Column({ space: 4 }) {
          Text('脑洞值').fontSize(14).fontColor('#E6FFFFFF')
          Row({ space: 4 }) {
            Text(`${this.profile.total}`).fontSize(44).fontWeight(FontWeight.Bold).fontColor('#FFFFFF')
            Text(`/ ${DIMENSIONS.length * 100}`).fontSize(14).fontColor('#B3FFFFFF').margin({ bottom: 8 })
          }.alignItems(VerticalAlign.Bottom)
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        Column({ space: 6 }) {
          SymbolGlyph($r('sys.symbol.crown_fill')).fontSize(28).fontColor(['#FFE082'])
          Text(brainTitle(this.profile.average, this.profile.explored)).fontSize(13).fontWeight(FontWeight.Medium)
            .fontColor('#FFFFFF').height(26).padding({ left: 10, right: 10 }).borderRadius(13)
            .backgroundColor('#33FFFFFF')
        }.alignItems(HorizontalAlign.End)
      }.width('100%').accessibilityGroup(true)
        .accessibilityText(`脑洞值 ${this.profile.total}，称号 ${brainTitle(this.profile.average, this.profile.explored)}`)
      Row() {
        this.Stat(`${this.profile.plays}`, '训练次数')
        Divider().vertical(true).height(28).color('#40FFFFFF')
        this.Stat(`${this.profile.explored}/${GAMES.length}`, '探索游戏')
        Divider().vertical(true).height(28).color('#40FFFFFF')
        this.Stat(`${this.profile.trophies}`, '获得奖杯')
      }.width('100%').margin({ top: 16 }).padding({ top: 12, bottom: 12 }).borderRadius(18)
        .backgroundColor('#1FFFFFFF')
    }.width('100%').padding(20).borderRadius(28)
    .linearGradient({ angle: 135, colors: [['#2B8CEB', 0], ['#7C4DFF', 1]] })
  }

  @Builder
  private Legend(color: string, label: string, dashed: boolean) {
    Row({ space: 6 }) {
      if (dashed) {
        Row({ space: 2 }) {
          Row().width(4).height(2).backgroundColor(color)
          Row().width(4).height(2).backgroundColor(color)
          Row().width(4).height(2).backgroundColor(color)
        }.width(16)
      } else {
        Row().width(16).height(8).borderRadius(4).backgroundColor(color)
      }
      Text(label).fontSize(12).fontColor($r('app.color.text_secondary'))
    }
  }

  @Builder
  private Radar() {
    Column() {
      Row() {
        Text('六维脑力雷达').fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        Blank()
        Row({ space: 12 }) {
          this.Legend(this.darkMode ? '#3D9BE9' : '#0277BD', '最佳', false)
          this.Legend(RECENT_COLOR, '近期', true)
        }
      }.width('100%')
      Canvas(this.context)
        .width('100%').height(this.chartHeight()).margin({ top: 8 })
        .onReady(() => { this.ready = true; this.draw(); })
        .onAreaChange((_old: Area, area: Area) => {
          const width = Number(area.width);
          if (width > 0) { this.chartWidth = width; }
          // A resized canvas may be cleared; repaint on every size change.
          this.draw();
        })
        .onClick((event: ClickEvent) => this.pick(event.x, event.y))
        .accessibilityText(this.profile.dimensions.map((item: DimensionScore) =>
          `${this.info(item.index).name} ${item.score} 分`).join('，'))
      Scroll() {
        Row({ space: 8 }) {
          ForEach(DIMENSIONS, (item: DimensionInfo, index: number) => {
            Row({ space: 6 }) {
              Row().width(8).height(8).borderRadius(4).backgroundColor(item.color)
              Text(item.name).fontSize(13)
                .fontColor(this.selected === index ? $r('app.color.text_primary') : $r('app.color.text_secondary'))
                .fontWeight(this.selected === index ? FontWeight.Medium : FontWeight.Normal)
            }.height(32).padding({ left: 12, right: 12 }).borderRadius(16)
              .backgroundColor(this.selected === index ? tintOf(item.color, '2E') : $r('app.color.page_background'))
              .onClick(() => { this.selected = index; })
              .accessibilityText(`${item.name}`).accessibilitySelected(this.selected === index)
          }, (item: DimensionInfo) => item.name)
        }
      }.width('100%').scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).margin({ top: 4 })
    }.width('100%').padding(16).borderRadius(28).backgroundColor($r('app.color.card_background'))
  }

  @Builder
  private Detail() {
    Column() {
      Row({ space: 12 }) {
        Stack() {
          Text(this.info(this.selected).name.substring(0, 1)).fontSize(22).fontWeight(FontWeight.Bold).fontColor('#FFFFFF')
        }.width(48).height(48).borderRadius(16).backgroundColor(this.info(this.selected).color)
        Column({ space: 2 }) {
          Text(this.info(this.selected).name).fontSize(18).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Text(`超能力：${this.info(this.selected).power}`).fontSize(13).fontColor(this.info(this.selected).color)
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        Row({ space: 2 }) {
          Text(`${this.dimension().score}`).fontSize(32).fontWeight(FontWeight.Bold)
            .fontColor(this.info(this.selected).color)
          Text('分').fontSize(13).fontColor($r('app.color.text_secondary')).margin({ bottom: 6 })
        }.alignItems(VerticalAlign.Bottom)
      }.width('100%')
      Progress({ value: this.dimension().score, total: 100, type: ProgressType.Capsule })
        .width('100%').height(10).margin({ top: 14 })
        .color(this.info(this.selected).color).backgroundColor(tintOf(this.info(this.selected).color, '26'))
      Row() {
        Text(`近期 ${this.dimension().recent} 分`).fontSize(12).fontColor($r('app.color.text_secondary'))
        Blank()
        Text(`已探索 ${this.dimension().explored}/${this.dimension().total} 款 · 练习 ${this.dimension().plays} 次`)
          .fontSize(12).fontColor($r('app.color.text_secondary'))
      }.width('100%').margin({ top: 8 })
      Text(this.info(this.selected).description).width('100%').margin({ top: 14 })
        .fontSize(15).lineHeight(22).fontColor($r('app.color.text_primary'))
      Row({ space: 8 }) {
        SymbolGlyph($r('sys.symbol.star_fill')).fontSize(16).fontColor([this.info(this.selected).color])
        Text(`脑洞小贴士：${this.info(this.selected).tip}`).fontSize(13).lineHeight(19)
          .fontColor($r('app.color.text_secondary')).layoutWeight(1)
      }.width('100%').margin({ top: 10 }).padding(12).borderRadius(16)
        .backgroundColor(tintOf(this.info(this.selected).color, '14'))
      if (this.dimension().practiceIndex >= 0) {
        Button({ type: ButtonType.Capsule }) {
          Row({ space: 8 }) {
            SymbolGlyph($r('sys.symbol.play_fill')).fontSize(16).fontColor(['#FFFFFF'])
            Text(`去练练：${GAMES[this.dimension().practiceIndex].name}`).fontSize(16)
              .fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
          }
        }.width('100%').height(48).margin({ top: 14 }).backgroundColor(this.info(this.selected).color)
          .onClick(() => this.onPractice(this.dimension().practiceIndex))
          .accessibilityText(`去练练 ${GAMES[this.dimension().practiceIndex].name}`)
      } else {
        Text('解锁这个维度的游戏，就能开始训练啦').width('100%').margin({ top: 14 }).textAlign(TextAlign.Center)
          .fontSize(13).fontColor($r('app.color.text_tertiary'))
      }
    }.width('100%').padding(20).borderRadius(28).backgroundColor($r('app.color.card_background'))
  }

  @Builder
  private Insight() {
    Column({ space: 12 }) {
      Text('脑洞发现').width('100%').fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      if (this.profile.explored === 0) {
        Text('你的小脑袋还在等待唤醒！玩一局任意游戏，雷达图就会长出来。')
          .width('100%').fontSize(14).lineHeight(21).fontColor($r('app.color.text_secondary'))
      } else {
        if (this.profile.strongest >= 0) {
          this.InsightRow($r('sys.symbol.medal'), this.info(this.profile.strongest).color,
            `最强超能力「${this.info(this.profile.strongest).power}」`,
            `你的${this.info(this.profile.strongest).name}表现最棒，继续保持！`)
        }
        if (this.profile.weakest >= 0) {
          this.InsightRow($r('sys.symbol.flag'), this.info(this.profile.weakest).color,
            `成长空间最大：${this.info(this.profile.weakest).name}`,
            '每天练一练，下次雷达图会更圆哦。')
        }
      }
      ForEach(this.profile.dimensions, (item: DimensionScore) => {
        Row({ space: 10 }) {
          Text(this.info(item.index).name).width(64).fontSize(13).fontColor($r('app.color.text_secondary'))
          Progress({ value: item.score, total: 100, type: ProgressType.Linear })
            .layoutWeight(1).color(this.info(item.index).color).style({ strokeWidth: 8 })
          Text(`${item.score}`).width(32).textAlign(TextAlign.End).fontSize(13).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
        }.width('100%').onClick(() => { this.selected = item.index; })
          .accessibilityGroup(true).accessibilityText(`${this.info(item.index).name} ${item.score} 分`)
      }, (item: DimensionScore) => `${item.index}-${item.score}`)
    }.width('100%').padding(20).borderRadius(28).backgroundColor($r('app.color.card_background'))
  }

  @Builder
  private InsightRow(symbol: Resource, color: string, title: string, body: string) {
    Row({ space: 12 }) {
      Stack() {
        SymbolGlyph(symbol).fontSize(20).fontColor([color])
      }.width(40).height(40).borderRadius(20).backgroundColor(tintOf(color, '24'))
      Column({ space: 2 }) {
        Text(title).fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        Text(body).fontSize(13).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
    }.width('100%').accessibilityGroup(true)
  }

  build() {
    Column() {
      Row() {
        Button({ type: ButtonType.Circle }) {
          SymbolGlyph($r('sys.symbol.chevron_left')).fontSize(22).fontColor([$r('app.color.text_primary')])
        }.width(40).height(40).backgroundColor($r('app.color.card_background'))
          .accessibilityText('返回').onClick(() => this.onBack())
        Column() {
          Text('我的脑洞').fontSize(20).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          Text('看看你的六维脑力超能力').fontSize(12).fontColor($r('app.color.text_secondary'))
        }.alignItems(HorizontalAlign.Start).padding({ left: 12 }).layoutWeight(1)
      }.width('100%').height(56).padding({ left: 16, right: 16 })
      Scroll() {
        Column({ space: 14 }) {
          this.Hero()
          this.Radar()
          this.Detail()
          this.Insight()
          Text('脑洞数据只保存在本机，根据每款游戏到达的关卡计算。').fontSize(11)
            .fontColor($r('app.color.text_tertiary')).margin({ top: 2, bottom: 24 })
        }.padding({ left: 16, right: 16, top: 4 })
      }.width('100%').layoutWeight(1).scrollBar(BarState.Off).edgeEffect(EdgeEffect.Spring).align(Alignment.Top)
    }.width('100%').height('100%')
  }
}

function tintOf(color: string, alpha: string): string {
  return color.length === 7 ? `#${alpha}${color.substring(1)}` : color;
}
