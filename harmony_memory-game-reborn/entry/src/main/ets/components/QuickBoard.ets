import { Puzzle, Tile, Move } from '../core/Contract';
import { quickTargetSamples, quickCenterColor } from '../core/QuickGames';
import image from '@ohos.multimedia.image';

class QuickCardBounds {
  x: number = 0;
  y: number = 0;
  width: number = 0;
  height: number = 0;
}

// Android Game10/13/14/16/19/20/23 grids. The host owns the page background,
// toolbar, timers and answer controls; this Canvas stays transparent.
@Component
export struct QuickBoard {
  @ObjectLink puzzle: Puzzle;
  @Prop @Watch('redraw') revision: number = 0;
  @Prop @Watch('redraw') availableHeight: number = 420;
  @Prop interactive: boolean = true;
  onMove: (move: Move) => void = (move: Move): void => {};
  private context: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  private canvasWidth: number = 320;
  private canvasHeight: number = 420;
  private ready: boolean = false;
  private downIndex: number = -1;
  private downX: number = 0;
  private downY: number = 0;
  private lastMoveIndex: number = -1;
  private feedbackImages: Map<string, image.PixelMap> = new Map<string, image.PixelMap>();
  private feedbackPending: Set<string> = new Set<string>();
  private disposed: boolean = false;

  private async loadFeedbackImage(correct: boolean): Promise<void> {
    const name = correct ? 'rightanswer' : 'wronganswer';
    if (this.feedbackImages.has(name) || this.feedbackPending.has(name) || this.disposed) { return; }
    const host = this.getUIContext().getHostContext();
    if (host === undefined) { return; }
    this.feedbackPending.add(name);
    try {
      const resource = correct ? $r('app.media.rightanswer') : $r('app.media.wronganswer');
      const bytes = host.resourceManager.getMediaContentSync(resource.id);
      // createPixelMap is atomic-service compatible; createPixelMapSync and
      // ImageSource.release are not. Keep the package an atomic service.
      const pixels = await image.createImageSource(bytes.buffer as ArrayBuffer).createPixelMap();
      if (this.disposed) { await pixels.release(); return; }
      this.feedbackImages.set(name, pixels);
      this.redraw();
    } catch {
      // Retain the original stimulus if resource loading fails; never replace
      // Android's bitmap with a platform-dependent Unicode approximation.
    } finally {
      this.feedbackPending.delete(name);
    }
  }

  private feedbackImage(correct: boolean, x: number, y: number, cell: number): void {
    const name = correct ? 'rightanswer' : 'wronganswer';
    const pixels = this.feedbackImages.get(name);
    if (pixels === undefined) { this.loadFeedbackImage(correct); return; }
    // Original drawBitmap uses null Paint, so it must not inherit the shape
    // shadow. Margin = (cell / 2) * .3; bitmap width = .7cell.
    this.context.save();
    this.applyShapeShadow(0, 0, '#00000000');
    this.context.drawImage(pixels, x - cell * 0.35, y - cell * 0.35, cell * 0.7, cell * 0.7);
    this.context.restore();
  }

  aboutToDisappear(): void {
    this.disposed = true;
    this.feedbackImages.forEach((pixels: image.PixelMap): void => { pixels.release(); });
    this.feedbackImages.clear();
  }

  private headerHeight(): number {
    // Game19Grid uses CloseButton.STROKE_WIDTH, whose actual repository value
    // is 3 (DrawableConstants.java), not the tempting ROW_COUNT value 8.
    return this.puzzle.game === 19 ? this.canvasHeight / 3 : 0;
  }

  private rows(): number { return Math.max(1, Math.ceil(this.puzzle.tiles.length / this.puzzle.columns)); }
  private cellSize(): number {
    if (this.puzzle.game === 19) {
      return Math.max(0, Math.min(this.canvasWidth / 5, (this.canvasHeight - this.headerHeight()) / 7));
    }
    const schulte = this.puzzle.game === 23;
    return Math.max(0, Math.min(this.canvasWidth / (schulte ? 6.35 : 6.07),
      this.canvasHeight / (schulte ? 8.45 : 8.09)));
  }
  private cellMargin(): number { return this.cellSize() * (this.puzzle.game === 19 ? 0 : this.puzzle.game === 23 ? 0.05 : 0.01); }
  private pitch(): number { return this.cellSize() + this.cellMargin(); }
  private left(): number {
    return (this.canvasWidth - this.puzzle.columns * this.cellSize() -
      (this.puzzle.columns + 1) * this.cellMargin()) / 2 + this.cellMargin();
  }
  private top(): number {
    if (this.puzzle.game === 19) {
      const header = this.headerHeight();
      return header + (this.canvasHeight - header - this.rows() * this.cellSize()) / 2;
    }
    // Literal current Android source quirk: 10/13/14 use STROKE_WIDTH=3 in
    // bottomPosition although there are 8 rows; do not silently redesign it.
    const heightRows = this.puzzle.game === 23 ? 8 : 3;
    return (this.canvasHeight - heightRows * this.cellSize() - 9 * this.cellMargin()) / 2 + this.cellMargin();
  }

  private label(text: string, x: number, y: number, size: number, color: string,
    bold: boolean = true, inkCentered: boolean = true): void {
    this.context.fillStyle = color;
    const fontPx = Math.max(1, Math.round(this.getUIContext().vp2px(size)));
    this.context.font = fontPx + 'px ' + (bold ? 'RobotoBold' : 'RobotoRegular');
    if (inkCentered) {
      // Game14/23 use Paint.getTextBounds, not font line-box centering.
      const bounds = this.context.measureText(text);
      const width = bounds.actualBoundingBoxLeft + bounds.actualBoundingBoxRight;
      const height = bounds.actualBoundingBoxAscent + bounds.actualBoundingBoxDescent;
      this.context.textAlign = 'left';
      this.context.textBaseline = 'alphabetic';
      this.context.fillText(text, x - width / 2, y + height / 2);
    } else {
      this.context.textAlign = 'center';
      this.context.textBaseline = 'middle';
      this.context.fillText(text, x, y);
    }
  }

  private applyShapeShadow(radius: number, offset: number, color: string): void {
    this.context.shadowBlur = radius;
    this.context.shadowOffsetX = offset;
    this.context.shadowOffsetY = offset;
    this.context.shadowColor = color;
  }

  private finishPath(): void {
    // Android draws once with one solid-color Paint and no separate outline.
    // Do not emulate FILL_AND_STROKE with a second Canvas stroke: the release
    // Game10 reference shows that producing a dark rim / overlapping edges.
    this.context.fill();
  }

  private polygon(x: number, y: number, radius: number, sides: number, startDegrees: number): void {
    const context = this.context;
    context.beginPath();
    for (let i = 0; i <= sides; i++) {
      const angle = (startDegrees + (i % sides) * 360 / sides) * Math.PI / 180;
      const px = x + Math.cos(angle) * radius;
      const py = y + Math.sin(angle) * radius;
      if (i === 0) { context.moveTo(px, py); }
      else { context.lineTo(px, py); }
    }
    context.closePath();
    this.finishPath();
  }

  // Canonical Game16FigureView numbering. The grid variants have different
  // index mappings, but exactly the same path vertices and .9-radius oval.
  private shape(shape: number, x: number, y: number, radius: number, color: string): void {
    const context = this.context;
    context.fillStyle = color;
    if (shape === 0) { this.polygon(x, y, radius, 5, -18); }
    else if (shape === 1) { this.polygon(x, y, radius, 4, 180); }
    else if (shape === 2) { this.polygon(x, y, radius, 6, 0); }
    else if (shape === 3) { this.polygon(x, y, radius, 4, 225); }
    else if (shape === 4) { this.polygon(x, y, radius, 3, -90); }
    else if (shape === 5) {
      context.beginPath(); context.arc(x, y, radius * 0.9, 0, Math.PI * 2); this.finishPath();
    } else if (shape === 6) {
      if (this.puzzle.game === 13) {
        this.polygon(x, y, radius, 4, 225);
        this.applyShapeShadow(0, 0, '#00000000');
        this.polygon(x, y, radius, 4, 180);
        this.applyShapeShadow(2, 1, '#77000000');
      } else {
        this.polygon(x, y, radius, 4, 180);
        this.polygon(x, y, radius, 4, 225);
      }
    }
  }

  private figureFor(tile: Tile): number {
    if (this.puzzle.game === 10) { return [5, 1, 3, 6][tile.value % 4]; }
    if (this.puzzle.game === 13) {
      const androidShape = this.puzzle.data[tile.value * 2];
      return [4, 0, 2, 5, 1, 3, 6][androidShape];
    }
    return tile.value;
  }

  private pair(tile: Tile, x: number, y: number, radius: number): void {
    this.shape(2, x, y, radius * 0.95, tile.color);
    this.shape(2, x, y, radius * 0.7, quickCenterColor(tile));
  }

  // Current Android Game23Colors.textColor (27935fd), not a new UI policy.
  // Older APKs used four teal backgrounds and white text; that is NOT the
  // current repository's Game23TableActivity.generateElements implementation.
  private textColor(background: string): string {
    const r = Number.parseInt(background.slice(1, 3), 16) / 255;
    const g = Number.parseInt(background.slice(3, 5), 16) / 255;
    const b = Number.parseInt(background.slice(5, 7), 16) / 255;
    const red = r <= 0.04045 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
    const green = g <= 0.04045 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
    const blue = b <= 0.04045 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
    return red * 0.2126 + green * 0.7152 + blue * 0.0722 > 0.179 ? '#000000' : '#FFFFFF';
  }

  private drawGrid(): void {
    const p = this.puzzle;
    const cell = this.cellSize();
    const left = this.left();
    const top = this.top();
    if (cell <= 0) { return; }
    if (p.game === 13) { this.applyShapeShadow(2, 1, '#77000000'); }
    else if (p.game === 19) { this.applyShapeShadow(4, 2, '#AA000000'); }
    if (p.game === 19) {
      const samples = quickTargetSamples(p);
      const radius = this.headerHeight() / 2;
      for (let i = 0; i < samples.length; i++) {
        this.pair(samples[i], this.canvasWidth * (i + 1) / (samples.length + 1), radius, radius);
      }
    }
    for (let i = 0; i < p.tiles.length; i++) {
      const tile = p.tiles[i];
      // Generic feedback must not resurrect consumed or originally blank slots.
      if (tile.hidden || tile.text.length === 0 || (tile.selected && (p.game === 14 || p.game === 19 || p.game === 23))) { continue; }
      const x = left + (i % p.columns) * this.pitch() + cell / 2;
      const y = top + Math.floor(i / p.columns) * this.pitch() + cell / 2;
      if (p.game === 19) { this.pair(tile, x, y, cell / 2); }
      else if (p.game === 14) {
        const next = p.target[p.answers.length];
        const color = p.failed && i !== next ? '#8C8C8C' : tile.color;
        this.shape(5, x, y, cell / 2, color);
        this.label(tile.text, x, y, cell / 2, '#FFFFFF');
      } else if (p.game === 23) {
        const color = p.failed && i === this.lastMoveIndex ? '#8C8C8C' : tile.color;
        this.context.fillStyle = color;
        this.context.fillRect(x - cell / 2, y - cell / 2, cell, cell);
        this.label(tile.text, x, y, cell / 2, this.textColor(color));
      } else {
        this.shape(this.figureFor(tile), x, y, cell / 2, tile.color);
      }
      if ((p.game === 10 && p.solved && p.target.indexOf(i) >= 0) ||
        (p.game === 13 && p.solved && i === this.lastMoveIndex)) {
        this.feedbackImage(true, x, y, cell);
      } else if ((p.game === 10 || p.game === 19) && p.failed && i === this.lastMoveIndex) {
        this.feedbackImage(false, x, y, cell);
      }
    }
    this.applyShapeShadow(0, 0, '#00000000');
  }

  private stimulusCardHeight(): number {
    const tablet = this.canvasWidth >= 600;
    if (this.puzzle.game === 16) { return tablet ? 340 : 240; }
    // game20_item: explicit vertical padding + TextView + divider + FigureView
    // and its margins. The tablet adds 24/18dp word and 30/24dp figure margins.
    return tablet ? 371 : 268.6700001;
  }

  private stimulusStackMargin(): number {
    // Game20 retains the resource override: 20dp phone / 10dp tablet.
    // Game16.setGameParams -> notifyDataSetChanged -> CardStack.reset creates
    // a NEW CardAnimator and loses that override: default 20 PHYSICAL pixels.
    return this.puzzle.game === 16 ? this.getUIContext().px2vp(20) : this.canvasWidth >= 600 ? 10 : 20;
  }

  private stimulusCardRect(depth: number): QuickCardBounds {
    // CardStack allocates four wrappers, with index0/index1 coincident. Their
    // three visible positions are 5/10/15 PHYSICAL px horizontal insets and
    // centerY offsets -2/-1/0 times mStackMargin (CardAnimator.initLayout).
    const inset = this.getUIContext().px2vp(5 * (depth + 1));
    const margin = this.canvasWidth >= 600 ? 110 : 40;
    const bounds = new QuickCardBounds();
    bounds.x = margin + inset;
    bounds.width = Math.max(0, this.canvasWidth - bounds.x * 2);
    bounds.height = this.stimulusCardHeight();
    bounds.y = this.canvasHeight / 2 - this.stimulusStackMargin() * (2 - depth) - bounds.height / 2;
    return bounds;
  }

  private drawStimulusCard(depth: number): void {
    const bounds = this.stimulusCardRect(depth);
    // drawable_state_cell_empty: white fill, square corners, 1dp #D9D9D9
    // INSIDE stroke. Two filled rectangles avoid Canvas's extra outline pass.
    this.context.fillStyle = '#D9D9D9';
    this.context.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    this.context.fillStyle = '#FFFFFF';
    this.context.fillRect(bounds.x + 1, bounds.y + 1, Math.max(0, bounds.width - 2), Math.max(0, bounds.height - 2));
  }

  private drawStimulus(): void {
    const p = this.puzzle;
    if (!((p.game === 16 && p.data.length >= 3) || (p.game === 20 && p.tiles.length === 1))) { return; }
    // Draw back to front. All lower stimuli are covered by the next white card
    // at rest; only the top stimulus is visible in the Android reference.
    for (let depth = 2; depth >= 0; depth--) { this.drawStimulusCard(depth); }
    const card = this.stimulusCardRect(0);
    if (p.game === 16) {
      const side = this.canvasWidth >= 600 ? 240 : 200;
      this.shape(p.data[1], card.x + card.width / 2, card.y + card.height / 2, side / 2, p.targetColor);
    } else {
      // game20_item.xml content is measured INSIDE the top CardAnimator card,
      // not centered independently on the transparent full-height Canvas.
      const tablet = this.canvasWidth >= 600;
      const side = tablet ? 130 : 116;
      const divider = tablet ? 1 : 0.6700001;
      const wordTop = tablet ? 34 : 10;
      const figureMargin = tablet ? 30 : 4;
      this.label(p.targetText, card.x + card.width / 2, card.y + wordTop + 62, 56, p.targetColor, false, false);
      const dividerTop = card.y + wordTop + 124 + (tablet ? 18 : 0);
      this.context.fillStyle = tablet ? '#B4B4B4' : '#8C8C8C';
      this.context.fillRect(card.x + 16, dividerTop, Math.max(0, card.width - 32), divider);
      this.shape(p.tiles[0].value, card.x + card.width / 2, dividerTop + divider + figureMargin + side / 2,
        side / 2, p.tiles[0].color);
    }
  }

  private redraw(): void {
    if (!this.ready) { return; }
    this.context.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
    this.applyShapeShadow(0, 0, '#00000000');
    if (this.puzzle.game === 16 || this.puzzle.game === 20) { this.drawStimulus(); }
    else { this.drawGrid(); }
  }

  private cellAt(x: number, y: number): number {
    if (this.puzzle.mode !== 'grid' || x < 0 || y < 0 || x >= this.canvasWidth || y >= this.canvasHeight) { return -1; }
    const size = this.cellSize();
    if (size <= 0) { return -1; }
    const column = Math.floor((x - this.left()) / this.pitch());
    const row = Math.floor((y - this.top()) / this.pitch());
    if (column < 0 || column >= this.puzzle.columns || row < 0 || row >= this.rows()) { return -1; }
    const index = row * this.puzzle.columns + column;
    if (index >= this.puzzle.tiles.length) { return -1; }
    const tile = this.puzzle.tiles[index];
    if (tile.hidden || tile.disabled || tile.text.length === 0) { return -1; }
    const localX = x - this.left() - column * this.pitch();
    const localY = y - this.top() - row * this.pitch();
    // Android uses the whole cell rectangle (including a circle's corners),
    // strict edges, and excludes the actual inter-cell divider.
    return localX > 0 && localX < size && localY > 0 && localY < size ? index : -1;
  }

  private touch(event: TouchEvent): void {
    if (!this.interactive || this.puzzle.solved || this.puzzle.failed) { this.downIndex = -1; return; }
    const points = event.type === TouchType.Up || event.type === TouchType.Cancel ? event.changedTouches : event.touches;
    if (points.length === 0) { return; }
    const x = points[0].x;
    const y = points[0].y;
    if (event.type === TouchType.Down) {
      this.downIndex = this.cellAt(x, y); this.downX = x; this.downY = y;
    } else if (event.type === TouchType.Cancel) { this.downIndex = -1; }
    else if (event.type === TouchType.Up) {
      const index = this.cellAt(x, y);
      if (index >= 0 && index === this.downIndex && Math.abs(x - this.downX) + Math.abs(y - this.downY) < 14) {
        const move = new Move(); move.kind = 'tile'; move.index = index;
        this.lastMoveIndex = index;
        this.onMove(move);
        this.redraw();
      }
      this.downIndex = -1;
    }
  }

  build() {
    Canvas(this.context)
      .width('100%')
      .height(this.availableHeight)
      .clip(true)
      .onReady(() => {
        this.disposed = false;
        this.ready = true;
        this.loadFeedbackImage(true);
        this.loadFeedbackImage(false);
        this.redraw();
      })
      .onAreaChange((_old: Area, area: Area) => {
        this.canvasWidth = Number.parseFloat(area.width.toString());
        this.canvasHeight = Number.parseFloat(area.height.toString());
        this.redraw();
      })
      .onTouch((event: TouchEvent) => this.touch(event))
      .accessibilityText(this.puzzle.title + '，' + this.puzzle.subtitle)
  }
}
