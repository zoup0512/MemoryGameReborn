import { Puzzle, Tile, Move } from '../core/Contract';
import { media } from '../core/GameCatalog';
import { memoryReady } from '../core/MemoryGames';
import { curves } from '@kit.ArkUI';

// Android sources of truth: RectangularGrid/WhoNewGrid/CountAllGrid/
// RectangularCircleGrid/ImageVortexGrid/CatchRectangularGrid/Game9Grid,
// hex/HexView, Game1..9 activities, drawable_state_{cell,circle}_*.xml.
// Canvas/background belongs to Index. This component has NO opaque board panel.
// elapsedMs is active round time; revision refreshes nested engine mutations.
// availableHeight is the FULL original grid_container (weight=1) height, not a
// square size limit. Android centers a screen-width-derived board inside it.
@Observed
class GridPosition {
  id: number = 0;
  index: number = 0;
  x: number = 0;
  y: number = 0;
  side: number = 40;
  height: number = 40;
  rotation: number = 0;
  opacity: number = 1;
  scale: number = 1;
  exitDuration: number = 0;
  exitDelay: number = 0;
  fill: string = '#FFFFFF';
  border: string = '#D9D9D9';
  icon: string = '';
  marker: string = '';
  markerSize: number = 40;
  tint: string = '#00000000';
  selected: boolean = false;
  enabled: boolean = false;
  hexagonal: boolean = false;
  circular: boolean = false;
  imageOnly: boolean = false;
  absent: boolean = false;
  accessible: string = '';
}

@Component
struct MemoryCell {
  @ObjectLink gridPosition: GridPosition;
  onTap: () => void = (): void => {};
  private exitCurve: ICurve = curves.customCurve((fraction: number): number =>
    (1 - Math.cos(Math.PI * Math.min(1, Math.max(0, fraction)))) / 2);

  private hexPoints(): number[][] {
    // HexView.getPath: width is hexSize, radius = width/sqrt(3).
    // No invented inset/rounded corners; HexView strokes the actual path.
    const width: number = this.gridPosition.side;
    const height: number = this.gridPosition.height;
    return [[width / 2, 0], [width, height / 4], [width, height * 3 / 4],
      [width / 2, height], [0, height * 3 / 4], [0, height / 4]];
  }

  build() {
    Stack() {
      if (this.gridPosition.imageOnly) {
        // Android replaces the ToggleButton BACKGROUND with the bitmap. The
        // PNG already contains its own transparent padding; never shrink it.
        Image(media(this.gridPosition.icon)).width('100%').height('100%')
          .objectFit(ImageFit.Fill).draggable(false)
      } else if (this.gridPosition.hexagonal) {
        Polygon().points(this.hexPoints())
          .width(this.gridPosition.side).height(this.gridPosition.height)
          .fill(this.gridPosition.fill).stroke(this.gridPosition.border).strokeWidth(1)
      } else if (this.gridPosition.circular) {
        // Android GradientDrawable strokes INSIDE its bounds. ArkUI Circle's
        // centered stroke needs a half-vp inset, unlike HexView's Canvas path.
        Circle().width(Math.max(0, this.gridPosition.side - 1))
          .height(Math.max(0, this.gridPosition.height - 1))
          .fill(this.gridPosition.fill).stroke(this.gridPosition.border).strokeWidth(1)
      } else {
        Stack() {}.width('100%').height('100%')
          .backgroundColor(this.gridPosition.fill)
          .border({ width: 1, color: this.gridPosition.border })
      }
      if (this.gridPosition.tint !== '#00000000') {
        Circle().width('100%').height('100%').fill(this.gridPosition.tint)
      }
      if (this.gridPosition.marker.length > 0) {
        Image(media(this.gridPosition.marker))
          .width(this.gridPosition.markerSize).height(this.gridPosition.markerSize)
          .objectFit(ImageFit.Fill).draggable(false)
      }
    }
    .width(this.gridPosition.side).height(this.gridPosition.height)
    .position({ x: this.gridPosition.x, y: this.gridPosition.y })
    .rotate({ angle: this.gridPosition.rotation })
    .scale({ x: this.gridPosition.scale, y: this.gridPosition.scale })
    .opacity(this.gridPosition.opacity)
    // The host freezes elapsedMs during feedback. Let ArkUI finish Android's
    // terminal fade/scale on the render thread without a timer or engine write.
    .animation({ duration: this.gridPosition.exitDuration, delay: this.gridPosition.exitDelay,
      curve: this.exitCurve })
    .visibility(this.gridPosition.absent ? Visibility.Hidden : Visibility.Visible)
    .enabled(this.gridPosition.enabled)
    .onClick(() => { if (this.gridPosition.enabled) { this.onTap(); } })
    .accessibilityText(this.gridPosition.accessible)
    .accessibilityLevel(this.gridPosition.absent ? 'no' : 'yes')
  }
}

@Component
export struct MemoryBoard {
  // Keep live ObjectLink identity, including stable observed positions under
  // reused ForEach keys. @Prop puzzle would detach preview from engine updates.
  @ObjectLink puzzle: Puzzle;
  @Prop @Watch('refresh') elapsedMs: number = 0;
  // Independent GridEnter clock. -1 keeps legacy preview-driven entry.
  // Host freezes engine elapsedMs during intro; exact 700ms (game6:1200ms)
  // switches to normal preview, whose elapsedMs starts at zero.
  @Prop @Watch('refresh') introElapsedMs: number = -1;
  @Prop @Watch('refresh') revision: number = 0;
  @Prop @Watch('refresh') availableHeight: number = 0;
  onMove: (move: Move) => void = (move: Move): void => {};
  @State private positions: GridPosition[] = [];
  @State private availableWidth: number = 320;
  @State private frameKey: string = '';
  // All Java int casts/layout rounding happen in physical pixels, then convert
  // back to vp. Using integer vp would distort spacing at non-unit densities.
  private density: number = 1;

  aboutToAppear(): void {
    const density: number = this.getUIContext().vp2px(1);
    if (Number.isFinite(density) && density > 0) { this.density = density; }
    this.refresh();
  }

  private ease(fraction: number): number {
    // ViewAnimator leaves interpolator=null -> ObjectAnimator's Android default
    // AccelerateDecelerateInterpolator, NOT linear motion or an invented spring.
    const value: number = Math.min(1, Math.max(0, fraction));
    return (1 - Math.cos(Math.PI * value)) / 2;
  }

  private boardWidth(): number {
    if (this.puzzle.game === 3 || this.puzzle.game === 7) { return this.availableWidth; }
    // Game1.startLevel, inherited by Game2; duplicated in Game4/5/6/8/9.
    return Math.floor(this.availableWidth * this.density *
      Math.min(1, this.puzzle.columns / 5)) / this.density;
  }

  private boardHeight(): number {
    return this.puzzle.hexagonal ? this.outerHeight() : this.boardWidth();
  }

  private outerHeight(): number {
    return this.availableHeight > 0 ? this.availableHeight : this.availableWidth;
  }

  private cellSize(): number {
    const columns: number = this.puzzle.columns;
    const sizePx: number = Math.fround(Math.fround(this.boardWidth() * this.density) /
      Math.fround(columns + Math.fround(Math.fround(0.1) * (columns + 1))));
    return Math.floor(sizePx) / this.density;
  }

  private halfMargin(): number {
    const columns: number = this.puzzle.columns;
    const sizePx: number = Math.fround(Math.fround(this.boardWidth() * this.density) /
      Math.fround(columns + Math.fround(Math.fround(0.1) * (columns + 1))));
    return Math.floor(Math.floor(Math.fround(Math.fround(0.1) * sizePx)) / 2) / this.density;
  }

  private pitch(): number { return this.cellSize() + 2 * this.halfMargin(); }

  private firstCell(): number {
    const widthPx: number = Math.round(this.boardWidth() * this.density);
    const occupiedPx: number = Math.round(this.puzzle.columns * this.pitch() * this.density);
    return Math.floor((widthPx - occupiedPx) / 2) / this.density + this.halfMargin();
  }

  private hasIntroClock(): boolean {
    const game: number = this.puzzle.game;
    return this.introElapsedMs >= 0 && game !== 4 && game !== 7;
  }

  private inIntro(): boolean {
    return this.hasIntroClock() && this.introElapsedMs < (this.puzzle.game === 6 ? 1200 : 700);
  }

  private entryProgress(index: number): number {
    const elapsed: number = this.hasIntroClock() ? this.introElapsedMs : this.elapsedMs;
    if (this.puzzle.game === 3) { return this.ease(elapsed / 400); }
    if (this.puzzle.game === 4 || this.puzzle.game === 5 || this.puzzle.game === 7) { return 1; }
    // Android creates children column-major but animateCells walks row-major.
    const order: number = Math.floor(index / this.puzzle.columns) +
      (index % this.puzzle.columns) * this.puzzle.columns;
    const delay: number = Math.floor(order * 400 / (this.puzzle.columns * this.puzzle.columns));
    return this.ease((elapsed - delay) / 300);
  }

  private layoutCell(cell: GridPosition, tile: Tile, index: number): void {
    const width: number = this.boardWidth();
    const left: number = (this.availableWidth - width) / 2;
    const top: number = (this.outerHeight() - this.boardHeight()) / 2;
    cell.side = this.cellSize(); cell.height = cell.side;
    cell.rotation = 0;
    let x: number = tile.x;
    let y: number = tile.y;
    if (this.puzzle.game === 2) {
      // Rotate the original board snapshot about its center with Android's
      // easing. Engine x/y are linear-time logic coordinates; never mutate them.
      const duration: number = this.puzzle.data[2];
      const fraction: number = duration > 0 ?
        Math.min(1, Math.max(0, (this.elapsedMs - this.puzzle.previewMs) / duration)) : 0;
      const angle: number = this.puzzle.data[1] * this.ease(fraction);
      const radians: number = angle * Math.PI / 180;
      // GridLayout padding can differ by one physical pixel after Java int
      // casts. Rotate about the VIEW center, not the cells' rounded centroid.
      const center: number = (width / 2 - this.firstCell() - cell.side / 2) / this.pitch();
      const dx: number = index % this.puzzle.columns - center;
      const dy: number = Math.floor(index / this.puzzle.columns) - center;
      x = center + dx * Math.cos(radians) - dy * Math.sin(radians);
      y = center + dx * Math.sin(radians) + dy * Math.cos(radians);
      // Real grid counter-rotates each child only when the snapshot is removed.
      cell.rotation = fraction > 0 && fraction < 1 ? angle : 0;
    } else if (this.puzzle.game === 8 && this.puzzle.data.length > 8 + index) {
      const duration: number = this.puzzle.data[2];
      const fraction: number = duration > 0 ?
        this.ease((this.elapsedMs - this.puzzle.previewMs) / duration) : 0;
      const destination: number = this.puzzle.data[8 + index];
      const startX: number = index % this.puzzle.columns;
      const startY: number = Math.floor(index / this.puzzle.columns);
      x = startX + (destination % this.puzzle.columns - startX) * fraction;
      y = startY + (Math.floor(destination / this.puzzle.columns) - startY) * fraction;
    }
    cell.x = left + this.firstCell() + x * this.pitch();
    cell.y = top + this.firstCell() + y * this.pitch();
    if (this.puzzle.hexagonal) {
      // HexView.getHexSize(.1), getPoints and getPath, including its intentional
      // close-packed pointed hexagons. Tile y already includes sqrt(3)/2.
      const lines: number = this.puzzle.data[5];
      const hexSize: number = width / ((lines + 1) * 1.1 + 0.1);
      const pitch: number = hexSize * 1.1;
      cell.side = hexSize; cell.height = hexSize * 2 / Math.sqrt(3);
      cell.x = (x + 0.5) * pitch + hexSize * 0.05 - cell.side / 2;
      cell.y = this.outerHeight() / 2 - (lines - 1) * Math.sqrt(3) * pitch / 4 +
        y * pitch - cell.height / 2;
    }
  }

  private styleCell(cell: GridPosition, tile: Tile, index: number): void {
    const game: number = this.puzzle.game;
    const terminal: boolean = this.puzzle.solved || this.puzzle.failed;
    const lastAnswer: number = this.puzzle.answers.length > 0 ?
      this.puzzle.answers[this.puzzle.answers.length - 1] : -1;
    const wrong: boolean = this.puzzle.failed && tile.selected && index === lastAnswer;
    const target: boolean = this.puzzle.target.indexOf(index) >= 0;
    cell.selected = tile.selected && !tile.hidden;
    cell.hexagonal = this.puzzle.hexagonal;
    cell.circular = game === 6 || game === 9;
    cell.icon = tile.hidden ? '' : tile.icon;
    cell.imageOnly = false;
    cell.marker = ''; cell.markerSize = cell.side;
    cell.tint = '#00000000';
    cell.absent = false;
    cell.fill = !tile.hidden && (tile.active || tile.selected) ? '#03A9F4' : '#FFFFFF';
    cell.border = '#D9D9D9';
    cell.opacity = this.entryProgress(index); cell.scale = 1;
    cell.exitDuration = 0; cell.exitDelay = 0;

    if (wrong) {
      cell.fill = '#F44336'; cell.marker = 'wronganswer';
    } else if (this.puzzle.failed && target && !tile.selected) {
      cell.fill = game === 3 && this.puzzle.answers.length === 0 ? '#03A9F4' : '#8C8C8C';
    } else if (this.puzzle.solved && index === lastAnswer && tile.selected) {
      cell.marker = 'rightanswer';
    }
    if (game === 3) { cell.markerSize = cell.side * 0.8; }
    if (game === 6 && wrong) { cell.markerSize = Math.max(0, cell.side - 10); }

    if (game === 4) {
      cell.opacity = 1;
      // WhoNewGrid NEVER hides non-success cells: all 100 white cells remain.
      if (!terminal && tile.value > 0) {
        if (this.elapsedMs < 1200 && tile.value === 1) {
          cell.fill = '#03A9F4'; cell.scale = this.ease(this.elapsedMs / 200);
        } else if (this.elapsedMs >= 1200 && this.elapsedMs < 1400 && tile.value === 1) {
          cell.fill = '#03A9F4'; cell.scale = 1 - this.ease((this.elapsedMs - 1200) / 200);
        } else if (this.elapsedMs >= this.puzzle.previewMs) {
          cell.fill = '#03A9F4'; cell.scale = this.ease((this.elapsedMs - this.puzzle.previewMs) / 200);
        } else { cell.fill = '#FFFFFF'; }
      }
    } else if (game === 5) {
      // CountAllGrid: occupied PNGs scale in 200ms, remain for 1000ms,
      // then scale out over 200ms; no empty board behind them.
      cell.absent = tile.value === 0;
      cell.icon = tile.value > 0 ? tile.icon : '';
      cell.imageOnly = cell.icon.length > 0;
      cell.marker = '';
      cell.opacity = 1;
      cell.scale = this.elapsedMs < this.puzzle.previewMs ? this.ease(this.elapsedMs / 200) :
        1 - this.ease((this.elapsedMs - this.puzzle.previewMs) / 200);
      if (this.puzzle.failed && tile.value > 0) { cell.scale = 1; }
    } else if (game === 6) {
      // Non-target white circles remain visible; only already introduced
      // sequence members become grey when masked. Never draw sequence numbers.
      if (tile.hidden && tile.value > 0 && (memoryReady(this.puzzle) || terminal)) {
        cell.fill = '#979696';
      }
    } else if (game === 7) {
      cell.absent = tile.value === 0 || tile.hidden;
      cell.imageOnly = cell.icon.length > 0;
      cell.opacity = 1;
      const start: number = this.puzzle.level === 1 ? 0 : this.puzzle.previewMs;
      cell.scale = this.ease((this.elapsedMs - start) / 300);
      cell.marker = '';
      // ImageVortexGrid.applyTint overlays a 40%-alpha circle, not a card.
      if (terminal && tile.value > 0 && lastAnswer >= 0) {
        if (index === lastAnswer) {
          cell.tint = this.puzzle.failed ? '#66FF0000' : '#6600FF00';
          cell.marker = this.puzzle.failed ? 'wronganswer' : 'rightanswer';
          cell.markerSize = cell.side * 0.8;
        } else if (this.puzzle.solved || target) { cell.tint = '#66000000'; }
      }
    } else if (game === 8) {
      cell.absent = tile.value === 0;
    } else if (game === 9) {
      // Game9Grid renders occupied bitmaps with no circle underneath; masked
      // positions are white circles, and a correct click only reveals the PNG.
      cell.imageOnly = cell.icon.length > 0 && (!wrong || target);
      if (cell.imageOnly) { cell.marker = ''; }
      else if (wrong) { cell.markerSize = Math.max(0, cell.side - 10); }
    }
    if (terminal && game !== 4 && !(game === 9 && this.puzzle.failed)) {
      if (game === 5 || game === 7) {
        cell.exitDuration = 200; cell.scale = 0;
      } else {
        cell.exitDuration = game === 3 ? 400 : 200;
        const order: number = Math.floor(index / this.puzzle.columns) +
          (index % this.puzzle.columns) * this.puzzle.columns;
        cell.exitDelay = game === 3 ? 0 :
          Math.floor(order * 300 / (this.puzzle.columns * this.puzzle.columns));
        cell.opacity = 0;
      }
    }
    // Intro is a drawing-only mask, never a write into live engine tiles. It
    // also hides already-created preview PNGs from accessibility and feedback.
    // Catch keeps only its occupied cells; WhoNew/Vortex have no such intro.
    if (this.inIntro()) {
      cell.fill = '#FFFFFF'; cell.icon = ''; cell.imageOnly = false;
      cell.marker = ''; cell.tint = '#00000000'; cell.selected = false;
      cell.scale = 1; cell.opacity = this.entryProgress(index);
      cell.exitDuration = 0; cell.exitDelay = 0;
      cell.absent = game === 5 || (game === 8 && tile.value === 0);
    }
    cell.enabled = !this.inIntro() && memoryReady(this.puzzle) && !tile.disabled &&
      this.puzzle.mode !== 'count' && !cell.absent;
    cell.accessible = (this.inIntro() || tile.hidden ? '遮盖的位置' : (cell.imageOnly ? '图片' : '方格')) +
      '，第' + (Math.round(tile.y) + 1).toString() + '行，第' + (Math.round(tile.x) + 1).toString() + '列';
  }

  private refresh(): void {
    if (this.puzzle.columns < 1) { return; }
    const key: string = this.puzzle.game.toString() + '-' + this.puzzle.level.toString();
    const newRound: boolean = key !== this.frameKey;
    if (newRound) { this.frameKey = key; }
    const result: GridPosition[] = [];
    for (let i: number = 0; i < this.puzzle.tiles.length; i++) {
      const tile: Tile = this.puzzle.tiles[i];
      const cell: GridPosition = !newRound && i < this.positions.length && this.positions[i].id === tile.id ?
        this.positions[i] : new GridPosition();
      cell.id = tile.id; cell.index = i;
      this.layoutCell(cell, tile, i);
      this.styleCell(cell, tile, i);
      result.push(cell);
    }
    this.positions = result;
  }

  private tap(index: number): void {
    if (this.inIntro() || !memoryReady(this.puzzle) || index < 0 || index >= this.puzzle.tiles.length ||
      this.puzzle.tiles[index].disabled || this.puzzle.mode === 'count') { return; }
    const move: Move = new Move();
    move.kind = 'tile'; move.index = index;
    this.onMove(move);
    this.refresh();
  }

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      ForEach(this.positions, (position: GridPosition) => {
        MemoryCell({ gridPosition: position, onTap: (): void => this.tap(position.index) })
          .width(position.side).height(position.height)
      }, (position: GridPosition) => this.frameKey + '-' + position.id.toString())
    }
    .width('100%').height(this.outerHeight())
    .clip(true)
    .onAreaChange((_old: Area, area: Area) => {
      const width: number = Number.parseFloat(area.width.toString());
      if (Number.isFinite(width) && width > 0 && Math.abs(width - this.availableWidth) > 0.5) {
        this.availableWidth = width; this.refresh();
      }
    })
  }
}
