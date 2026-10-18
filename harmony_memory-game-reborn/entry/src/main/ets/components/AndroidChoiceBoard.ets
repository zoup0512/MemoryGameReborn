import { Puzzle, Move } from '../core/Contract';

class ChoiceRect {
  x: number = 0;
  y: number = 0;
  width: number = 0;
  height: number = 0;
}

// Source: view_game11/12/16/20, game11_item, dialog_game5, Game*Grid,
// Game5CountAllActivity and CardAnimator. Page chrome/background belongs to Index.
// Android explicitly setCanSwipe(false) for BOTH arithmetic games. In particular,
// do not turn the comparison game's three invisible click bands into swipe input.
@Component
export struct AndroidChoiceBoard {
  @ObjectLink puzzle: Puzzle;
  @Prop @Watch('synchronize') availableHeight: number = 420;
  @Prop @Watch('synchronize') revision: number = 0;
  @Prop interactive: boolean = true;
  @Prop controlsOnly: boolean = false;
  @Prop soundOn: boolean = true;
  onSound: () => void = (): void => {};
  onMove: (move: Move) => void = (move: Move): void => {};
  @State private boardWidth: number = 320;
  @State private clicked: number = -1;
  @State private correct: number = -1;
  @State private outgoing: boolean = false;
  @State private oldTop: string = '';
  @State private oldBottom: string = '';
  @State private oldCounts: string[] = [];
  @State private oldCountAnswer: number = 0;
  @State private flightX1: number = 0;
  @State private flightY1: number = 0;
  @State private flightX2: number = 0;
  @State private flightY2: number = 0;
  @State private countScale: number = 1;
  @State private stackAdvance: number = 0;
  private observedPuzzle: Puzzle | null = null;
  private answeredPuzzle: Puzzle | null = null;
  private busy: boolean = false;
  private disposed: boolean = false;
  private timers: number[] = [];
  private epoch: number = 0;
  private downIndex: number = -1;
  private downX: number = 0;
  private downY: number = 0;

  aboutToAppear(): void {
    this.disposed = false;
    this.observedPuzzle = this.puzzle;
    if (this.puzzle.game === 5) { this.openCount(); }
  }

  aboutToDisappear(): void {
    this.disposed = true;
    this.clearTimers();
    this.downIndex = -1;
  }

  private later(delay: number, action: () => void): void {
    const epoch = this.epoch;
    const timer = setTimeout(() => {
      if (!this.disposed && this.epoch === epoch) { action(); }
    }, delay);
    this.timers.push(timer);
  }

  private clearTimers(): void {
    this.epoch++;
    for (let i = 0; i < this.timers.length; i++) { clearTimeout(this.timers[i]); }
    this.timers = [];
  }

  private resetVisuals(): void {
    this.clearTimers();
    this.busy = false;
    this.clicked = -1;
    this.correct = -1;
    this.outgoing = false;
    this.flightX1 = 0; this.flightY1 = 0;
    this.flightX2 = 0; this.flightY2 = 0;
    this.countScale = 1;
    this.stackAdvance = 0;
    this.answeredPuzzle = null;
    this.downIndex = -1;
  }

  private synchronize(): void {
    // revision also changes for timers/terminal feedback on the SAME object.
    // Do not wipe selection then, or let replacing a puzzle erase its flying card.
    if (this.observedPuzzle === this.puzzle) { return; }
    const previous = this.observedPuzzle;
    this.observedPuzzle = this.puzzle;
    if (previous !== null && previous.game === this.puzzle.game && this.busy) { return; }
    this.resetVisuals();
    if (this.puzzle.game === 5 && !this.disposed) { this.openCount(); }
  }

  private tablet(): boolean { return this.boardWidth >= 600; }
  private controlButtonHeight(): number { return this.tablet() ? 135 : 90; }
  private controlsHeight(): number { return this.controlButtonHeight() + (this.tablet() ? 32 : 0); }
  private boardHeight(): number {
    if (this.controlsOnly && (this.puzzle.game === 16 || this.puzzle.game === 20)) { return this.controlsHeight(); }
    return Math.max(0, this.availableHeight);
  }

  private rect(x: number, y: number, width: number, height: number): ChoiceRect {
    const value = new ChoiceRect();
    value.x = x; value.y = y; value.width = Math.max(0, width); value.height = Math.max(0, height);
    return value;
  }

  private controlRect(index: number): ChoiceRect {
    const margin = this.tablet() ? 16 : 0;
    const gap = this.tablet() ? 16 : 0;
    const width = (this.boardWidth - margin * 2 - gap) / 2;
    return this.rect(margin + (index === 1 ? 0 : width + gap),
      this.boardHeight() - margin - this.controlButtonHeight(), width, this.controlButtonHeight());
  }

  private expressionRect(index: number, depth: number): ChoiceRect {
    const compare = this.puzzle.game === 12;
    const height = compare ? this.boardHeight() : Math.max(0, this.boardHeight() - this.controlsHeight());
    const paddingTop = compare ? 16 : this.tablet() ? 80 : 0;
    const half = (height - paddingTop) / (compare ? 2 : 1);
    let center = paddingTop + half * (compare && index === 2 ? 1.5 : 0.5);
    if (compare && this.tablet()) { center += index === 2 ? -12 : 12; }
    // CardAnimator's 4 wrappers have 3 visible positions: 0/-20/-40dp.
    // Its 5/10/15 inset constants are PHYSICAL pixels, not Android dp.
    const advance = depth > 0 ? this.stackAdvance : 0;
    const inset = this.getUIContext().px2vp(5 * (depth + 1 - advance));
    // notifyDataSetChanged -> CardStack.reset -> new CardAnimator loses the
    // dp resource override. Its actual default is 20 PHYSICAL px on every size.
    const stackMargin = this.getUIContext().px2vp(20);
    center -= stackMargin * (2 - depth + advance);
    const margin = this.tablet() ? (compare ? 84 : 60) : 46;
    const cardHeight = this.tablet() ? 140 : 100;
    return this.rect(margin + inset, center - cardHeight / 2,
      this.boardWidth - 2 * (margin + inset), cardHeight);
  }

  private equalsRect(): ChoiceRect {
    const side = this.tablet() ? 100 : 64;
    // PercentFrameLayout has 16dp top padding and the TextView 8dp bottom margin.
    return this.rect((this.boardWidth - side) / 2, (this.boardHeight() - side) / 2 + 4, side, side);
  }

  private androidText(text: string): string {
    // Activities use ASCII multiplication/division; keep the engine untouched.
    return text.replace(/×/g, '*').replace(/÷/g, '/');
  }

  private expressionText(index: number): string {
    return this.androidText(this.puzzle.game === 12 ? this.puzzle.choices[index] : this.puzzle.targetText);
  }

  private feedbackColor(index: number): string {
    if (this.clicked < 0) { return '#FFFFFF'; }
    if (index === this.clicked) { return index === this.correct ? '#03A9F4' : '#F44336'; }
    if (this.puzzle.game === 12 && index === this.correct) { return '#979696'; }
    return '#FFFFFF';
  }

  private feedbackTextColor(index: number): string {
    return this.feedbackColor(index) === '#FFFFFF' ? '#000000' : '#FFFFFF';
  }

  private controlIcon(index: number): Resource {
    const white = this.clicked === index;
    if (index === 0) { return white ? $r('app.media.ic_game12_tick_white') : $r('app.media.ic_game12_tick'); }
    return white ? $r('app.media.ic_game12_cancel_white') : $r('app.media.ic_game12_cancel');
  }

  private answerCorrect(index: number): boolean {
    if (this.puzzle.game === 5) { return Number(this.puzzle.choices[index]) === this.puzzle.data[6]; }
    return this.puzzle.target.indexOf(index) >= 0;
  }

  private canAnswer(): boolean {
    if (!this.interactive || this.busy || this.clicked >= 0 || this.puzzle.solved || this.puzzle.failed) { return false; }
    if (this.puzzle.game === 5) {
      // MemoryGames INPUT=2. previewMs is the historical display duration, not
      // an input-phase flag; it remains nonzero after revealMemory().
      return this.puzzle.mode === 'count' && this.puzzle.data[0] === 2 && this.puzzle.choices.length === 4;
    }
    if (this.puzzle.game === 16 && (this.puzzle.previewMs > 0 || this.puzzle.choices.length < 2)) { return false; }
    return this.puzzle.mode === 'choice' &&
      (this.puzzle.game === 11 || this.puzzle.game === 12 || this.puzzle.game === 16 || this.puzzle.game === 20);
  }

  private discardDirection(index: number, correct: boolean): number {
    if (this.puzzle.game === 12) {
      if (this.correct === 0) { return index === 0 ? 1 : 0; }
      if (this.correct === 1) { return index === 0 ? 1 : 2; }
      return index === 0 ? 0 : 1;
    }
    return index === 0 ? (correct ? 1 : 3) : (correct ? 0 : 2);
  }

  private fly(direction1: number, direction2: number): void {
    // CardAnimator.REMOTE_DISTANCE=1000 physical px, 250ms, no rotation/fade.
    const distance = this.getUIContext().px2vp(1000);
    const epoch = this.epoch;
    this.getUIContext().animateTo({ duration: 250, curve: Curve.EaseInOut, onFinish: () => {
      if (this.disposed || this.epoch !== epoch) { return; }
      this.busy = false;
      if (this.puzzle !== this.answeredPuzzle) { this.resetVisuals(); }
    } }, () => {
      this.stackAdvance = 1;
      this.flightX1 = direction1 % 2 === 0 ? -distance : distance;
      this.flightY1 = direction1 < 2 ? -distance : distance;
      this.flightX2 = direction2 % 2 === 0 ? -distance : distance;
      this.flightY2 = direction2 < 2 ? -distance : distance;
    });
  }

  private openCount(): void {
    this.countScale = 0;
    this.later(0, () => {
      this.getUIContext().animateTo({ duration: 200, curve: Curve.EaseInOut }, () => { this.countScale = 1; });
    });
  }

  private closeCount(correct: boolean): void {
    this.later(correct ? 0 : 1800, () => {
      const epoch = this.epoch;
      this.getUIContext().animateTo({ duration: 200, curve: Curve.EaseInOut, onFinish: () => {
        if (this.disposed || this.epoch !== epoch) { return; }
        this.busy = false;
        if (this.puzzle !== this.answeredPuzzle) { this.resetVisuals(); this.openCount(); }
      } }, () => { this.countScale = 0; });
    });
  }

  private choose(index: number): void {
    this.synchronize();
    if (!this.canAnswer() || !Number.isInteger(index) || index < 0 || index >= this.puzzle.choices.length) { return; }
    this.clicked = index;
    const correct = this.answerCorrect(index);
    this.correct = this.puzzle.game === 5 ? this.puzzle.choices.indexOf(this.puzzle.data[6].toString()) : this.puzzle.target[0];
    this.answeredPuzzle = this.puzzle;
    this.busy = true;
    if (this.puzzle.game === 11 || this.puzzle.game === 12) {
      this.oldTop = this.expressionText(0);
      this.oldBottom = this.puzzle.game === 12 ? this.expressionText(2) : '';
      this.outgoing = true;
      const first = this.discardDirection(this.puzzle.game === 12 ? 0 : index, correct);
      const second = this.discardDirection(2, correct);
      this.later(150, () => { this.fly(first, second); });
    } else if (this.puzzle.game === 5) {
      this.oldCounts = this.puzzle.choices.slice();
      this.oldCountAnswer = this.puzzle.data[6];
      this.closeCount(correct);
    }
    else {
      // These games' stimuli/animations are owned by QuickBoard/Index.
      this.later(this.puzzle.game === 20 ? (correct ? 200 : 400) : 150, () => {
        this.busy = false;
        if (this.puzzle !== this.answeredPuzzle) { this.resetVisuals(); }
      });
    }
    const move = new Move(); move.kind = 'choice'; move.index = index;
    this.onMove(move);
  }

  private countSide(): number { return this.tablet() ? 110 : 70; }
  private countMargin(): number { return this.tablet() ? 8 : 5; }
  private countCardWidth(): number { return Math.max(0, Math.min(420, this.boardWidth - 32)); }
  private countValue(index: number): string {
    return this.clicked >= 0 ? this.oldCounts[index] : this.puzzle.choices[index];
  }
  private countFeedback(): string {
    if (this.clicked < 0) { return '请选择刚才看到的图案数量。'; }
    return (this.clicked === this.correct ? '正确！答案是 ' : '错误，正确答案是 ') + this.oldCountAnswer.toString() + '。';
  }
  private countColor(index: number): string {
    if (this.clicked < 0) { return '#087CAF'; }
    if (index === this.correct) { return '#FFFFFF'; }
    return index === this.clicked ? '#BD3045' : '#087CAF';
  }

  private contains(rect: ChoiceRect, x: number, y: number): boolean {
    return x >= rect.x && x < rect.x + rect.width && y >= rect.y && y < rect.y + rect.height;
  }

  private hitAt(x: number, y: number): number {
    if (x < 0 || x >= this.boardWidth || y < 0 || y >= this.boardHeight()) { return -1; }
    if (this.puzzle.game === 12) {
      // Literal phone .4/.2/.4; sw600dp .4/.15/.4 (total .95).
      const sum = this.tablet() ? 0.95 : 1;
      return y < this.boardHeight() * 0.4 / sum ? 0 :
        y < this.boardHeight() * (this.tablet() ? 0.55 : 0.6) / sum ? 1 : 2;
    }
    if (this.puzzle.game === 11 || this.puzzle.game === 16 || this.puzzle.game === 20) {
      for (let index = 0; index < 2; index++) { if (this.contains(this.controlRect(index), x, y)) { return index; } }
    }
    return -1;
  }

  private touch(event: TouchEvent): void {
    if (!this.canAnswer()) { this.downIndex = -1; return; }
    const points = event.type === TouchType.Up || event.type === TouchType.Cancel ? event.changedTouches : event.touches;
    if (points.length === 0) { return; }
    const x = points[0].x; const y = points[0].y;
    if (event.type === TouchType.Down) {
      this.downIndex = this.hitAt(x, y); this.downX = x; this.downY = y;
    } else if (event.type === TouchType.Cancel) { this.downIndex = -1; }
    else if (event.type === TouchType.Up) {
      const index = this.hitAt(x, y);
      if (index >= 0 && index === this.downIndex && Math.abs(x - this.downX) + Math.abs(y - this.downY) < 14) { this.choose(index); }
      this.downIndex = -1;
    }
  }

  @Builder
  private expressionCard(text: string, index: number, depth: number, flying: boolean) {
    Text(text)
      .fontFamily('RobotoRegular').fontSize(this.tablet() ? 55 : 28)
      .fontColor(depth === 0 && this.puzzle.game === 12 && (flying || !this.outgoing) ? this.feedbackTextColor(index) : '#000000')
      .textAlign(TextAlign.Center).padding(this.tablet() ? { top: 10, bottom: 10 } : 10)
      .width(this.expressionRect(index, depth).width).height(this.expressionRect(index, depth).height)
      .backgroundColor(depth === 0 && this.puzzle.game === 12 && (flying || !this.outgoing) ? this.feedbackColor(index) : '#FFFFFF')
      .border({ width: 1, color: '#D9D9D9', radius: 4 }).borderRadius(4).clip(true)
      .position({ x: this.expressionRect(index, depth).x, y: this.expressionRect(index, depth).y })
      .translate({ x: flying ? (index === 2 ? this.flightX2 : this.flightX1) : 0,
        y: flying ? (index === 2 ? this.flightY2 : this.flightY1) : 0 })
      .accessibilityLevel('no')
  }

  @Builder
  private expressionStack(index: number) {
    this.expressionCard(this.expressionText(index), index, 2, false)
    this.expressionCard(this.expressionText(index), index, 1, false)
    if (!this.outgoing || this.puzzle !== this.answeredPuzzle) {
      this.expressionCard(this.expressionText(index), index, 0, false)
    }
    if (this.outgoing) {
      this.expressionCard(index === 2 ? this.oldBottom : this.oldTop, index, 0, true)
    }
  }

  @Builder
  private binaryControl(index: number) {
    Row() {
      Image(this.controlIcon(index)).width(this.tablet() ? 55 : 40).height(this.tablet() ? 55 : 40).objectFit(ImageFit.Contain)
        .accessibilityLevel('no')
    }
    .justifyContent(FlexAlign.Center)
    .width(this.controlRect(index).width).height(this.controlRect(index).height)
    .backgroundColor(this.feedbackColor(index)).border({ width: 1, color: '#D9D9D9', radius: 0 })
    .position({ x: this.controlRect(index).x, y: this.controlRect(index).y })
    .visibility(this.puzzle.game === 16 && this.puzzle.choices.length < 2 ? Visibility.Hidden : Visibility.Visible)
    .accessibilityText(index === 0 ? '正确' : '不正确').accessibilityRole(AccessibilityRoleType.BUTTON)
    .onClick(() => { this.choose(index); })
  }

  @Builder
  private countOption(index: number) {
    Text(this.countValue(index))
      .fontFamily('RobotoLight').fontSize(this.tablet() ? 36 : 28).fontColor(this.countColor(index))
      .textAlign(TextAlign.Center).width(this.countSide()).height(this.countSide()).padding(4).margin(this.countMargin())
      .backgroundColor(this.clicked >= 0 && index === this.correct ? '#087CAF' : '#FFFFFF')
      .border({ width: this.clicked >= 0 && index === this.correct ? 0 : 1, color: '#D8E3ED', radius: 12 })
      .borderRadius(12).clip(true)
      .scale({ x: this.countScale, y: this.countScale })
      .accessibilityRole(AccessibilityRoleType.BUTTON)
      .accessibilityText(this.countValue(index) + (this.clicked < 0 ? '' : index === this.correct ? '，正确答案' : index === this.clicked ? '，错误答案' : ''))
      .onClick(() => { this.choose(index); })
  }

  @Builder
  private countDialog() {
    // DialogUi's window is transparent, its CONTENT is a white 20dp-radius
    // surface. Sound is shared with Index rather than a pretend local toggle.
    Column() {
      Row({ space: 8 }) {
        Image(this.soundOn ? $r('app.media.soundon') : $r('app.media.soundoff'))
          .width(24).height(24).objectFit(ImageFit.Contain).accessibilityLevel('no')
        Text(this.soundOn ? '声音：开启' : '声音：关闭').fontFamily('RobotoMedium').fontSize(16).fontColor('#087CAF')
      }
      .width('100%').constraintSize({ minHeight: 52 }).padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .justifyContent(FlexAlign.Center).margin({ bottom: 16 }).backgroundColor('#FFFFFF')
      .border({ width: 1, color: '#D8E3ED', radius: 12 }).borderRadius(12).clip(true)
      .accessibilityRole(AccessibilityRoleType.BUTTON)
      .accessibilityText(this.soundOn ? '声音已开启，点按关闭声音。' : '声音已关闭，点按开启声音。')
      .onClick(() => { if (this.interactive) { this.onSound(); } })
      Text('几个格子？').fontFamily('RobotoLight').fontStyle(FontStyle.Italic).fontSize(22).fontColor('#182230')
        .width('100%').textAlign(TextAlign.Center).margin({ bottom: 12 })
      Text(this.countFeedback()).fontFamily('RobotoRegular').fontSize(15)
        .fontColor(this.clicked < 0 ? '#596579' : this.clicked === this.correct ? '#087CAF' : '#BD3045')
        .textAlign(TextAlign.Center).width('100%').constraintSize({ minHeight: 48 }).margin({ bottom: 12 })
        .accessibilityText(this.countFeedback())
      Row() { this.countOption(0); this.countOption(1); }
      Row() { this.countOption(2); this.countOption(3); }
    }
    .width(this.countCardWidth()).padding(24).backgroundColor('#FFFFFF').borderRadius(20)
    .align(Alignment.Center)
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      if (this.puzzle.game === 5) {
        Row().width('100%').height('100%').backgroundColor('#73000000').accessibilityLevel('no')
        Scroll() {
          Column() { this.countDialog(); }.width('100%').constraintSize({ minHeight: this.boardHeight() })
            .justifyContent(FlexAlign.Center).padding({ top: 16, bottom: 16 })
        }.width('100%').height('100%').scrollBar(BarState.Off)
      } else if (this.puzzle.game === 12) {
        Text('=').fontFamily('RobotoRegular').fontSize(this.tablet() ? 55 : 36).textAlign(TextAlign.Center)
          .fontColor(this.feedbackTextColor(1)).backgroundColor(this.feedbackColor(1))
          .border({ width: 1, color: '#D9D9D9', radius: this.tablet() ? 50 : 32 })
          .borderRadius(this.tablet() ? 50 : 32).clip(true)
          .width(this.equalsRect().width).height(this.equalsRect().height)
          .position({ x: this.equalsRect().x, y: this.equalsRect().y }).accessibilityLevel('no')
        this.expressionStack(0)
        this.expressionStack(2)
        Column() {
          Row().width('100%').layoutWeight(0.4).accessibilityText(this.expressionText(0))
            .accessibilityRole(AccessibilityRoleType.BUTTON).onClick(() => { this.choose(0); })
          Row().width('100%').layoutWeight(this.tablet() ? 0.15 : 0.2).accessibilityText('相等')
            .accessibilityRole(AccessibilityRoleType.BUTTON).onClick(() => { this.choose(1); })
          Row().width('100%').layoutWeight(0.4).accessibilityText(this.expressionText(2))
            .accessibilityRole(AccessibilityRoleType.BUTTON).onClick(() => { this.choose(2); })
        }.width('100%').height('100%')
      } else if (this.puzzle.game === 11 || this.puzzle.game === 16 || this.puzzle.game === 20) {
        if (this.puzzle.game === 11) { this.expressionStack(0); }
        this.binaryControl(1)
        this.binaryControl(0)
      }
    }
    .width('100%').height(this.boardHeight()).clip(true)
    .onAreaChange((_old: Area, area: Area) => { this.boardWidth = Number.parseFloat(area.width.toString()); })
    .onTouch((event: TouchEvent) => { this.touch(event); })
  }
}
