import { Puzzle, Move } from '../core/Contract';
import { media } from '../core/GameCatalog';
import image from '@ohos.multimedia.image';

// Android references: Game15Grid, TwoFoldGrid, Game18Grid, SymmetryGrid,
// Game22Grid and activity_game15/17/21.xml. The host owns background/header/dialogs.
// Geometry is vp (Android dp); only Canvas font CSS sizes use physical px.
@Component
export struct SpatialBoard {
  @ObjectLink puzzle: Puzzle;
  @Prop @Watch('redraw') elapsedMs: number = 0;
  @Prop @Watch('redraw') revision: number = 0;
  @Prop @Watch('redraw') availableHeight: number = 420;
  onMove: (move: Move) => void = (move: Move): void => {};
  private context: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  @State private boardWidth: number = 320;
  @State private boardHeight: number = 420;
  private ready: boolean = false;
  private images: Map<string, image.PixelMap> = new Map<string, image.PixelMap>();
  private loading: Set<string> = new Set<string>();
  private disposed: boolean = false;
  private startX: number = 0;
  private startY: number = 0;
  private startIndex: number = -1;
  private lastIndex: number = -1;
  private dragging: boolean = false;
  private dragToken: number = -1;
  private dragX: number = 0;
  private dragY: number = 0;
  private chain: boolean = false;
  private pyramidChoice: number = -1;
  private cloudCoordinates: number[] = [];
  private cloudWidth: number = 0;
  private cloudHeight: number = 0;
  private planeCoordinates: number[] = [];
  private planeWidth: number = 0;
  private planeHeight: number = 0;

  private emit(kind: string, index: number, direction: number = 0): void {
    const move = new Move();
    move.kind = kind; move.index = index; move.direction = direction;
    this.onMove(move);
    this.redraw();
  }

  private bitmap(name: string): image.PixelMap | undefined {
    const cached = this.images.get(name);
    if (cached !== undefined) { return cached; }
    const host = this.getUIContext().getHostContext();
    if (host === undefined) { return undefined; }
    if (this.loading.has(name) || this.disposed) { return undefined; }
    this.loading.add(name);
    try {
      const bytes = host.resourceManager.getMediaContentSync(media(name).id);
      const source = image.createImageSource(bytes.buffer as ArrayBuffer);
      // createPixelMapSync and ImageSource.release are not atomicservice APIs.
      // Async decode is supported; source lifetime follows its decode Promise.
      source.createPixelMap().then((pixels: image.PixelMap): void => {
        this.loading.delete(name);
        if (this.disposed) { pixels.release(); return; }
        this.images.set(name, pixels);
        this.redraw();
      }).catch((): void => { this.loading.delete(name); });
    } catch (error) {
      this.loading.delete(name);
      console.error('SpatialBoard cannot decode original media ' + name + ': ' + String(error));
    }
    return undefined;
  }

  aboutToDisappear(): void {
    this.disposed = true;
    this.ready = false;
    this.images.forEach((pixels: image.PixelMap): void => { pixels.release(); });
    this.images.clear();
  }

  private sprite(name: string, x: number, y: number, width: number, height: number, rotation: number = 0): void {
    const pixels = this.bitmap(name);
    if (pixels === undefined) { return; }
    this.context.save();
    this.context.translate(x, y);
    this.context.rotate(rotation * Math.PI / 180);
    this.context.drawImage(pixels, -width / 2, -height / 2, width, height);
    this.context.restore();
  }

  private text(text: string, x: number, y: number, size: number = 18, color: string = '#000000'): void {
    this.context.fillStyle = color;
    this.context.font = Math.round(this.getUIContext().vp2px(size)) + 'px RobotoLight';
    this.context.textAlign = 'center';
    this.context.textBaseline = 'middle';
    this.context.fillText(text, x, y);
  }

  private line(x1: number, y1: number, x2: number, y2: number, color: string, width: number): void {
    this.context.beginPath(); this.context.moveTo(x1, y1); this.context.lineTo(x2, y2);
    this.context.strokeStyle = color; this.context.lineWidth = width; this.context.stroke();
  }

  private circle(x: number, y: number, radius: number, color: string): void {
    this.context.beginPath(); this.context.arc(x, y, radius, 0, Math.PI * 2);
    this.context.fillStyle = color; this.context.fill();
  }

  private polygon(points: number[], color: string): void {
    this.context.beginPath(); this.context.moveTo(points[0], points[1]);
    for (let i = 2; i < points.length; i += 2) { this.context.lineTo(points[i], points[i + 1]); }
    this.context.closePath(); this.context.fillStyle = color; this.context.fill();
  }

  private roundedRect(x: number, y: number, width: number, height: number, radius: number, color: string): void {
    const c = this.context;
    c.beginPath(); c.moveTo(x + radius, y); c.lineTo(x + width - radius, y);
    c.quadraticCurveTo(x + width, y, x + width, y + radius);
    c.lineTo(x + width, y + height - radius);
    c.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
    c.lineTo(x + radius, y + height); c.quadraticCurveTo(x, y + height, x, y + height - radius);
    c.lineTo(x, y + radius); c.quadraticCurveTo(x, y, x + radius, y);
    c.closePath(); c.fillStyle = color; c.fill();
  }

  // cellSize is the center-to-center pitch. Android DIVIDER_COEFFICIENT is 0.1.
  private cellEdge(): number {
    const n = this.puzzle.columns;
    if (this.puzzle.mode === '248') { return Math.min(this.boardWidth, this.boardHeight) / 8.9; }
    if (this.puzzle.mode === 'symmetry') {
      const rows = this.puzzle.tiles.length / n;
      const width = this.boardWidth * (n < 5 ? n / 5 : 1);
      return Math.min(width / (n + 0.1 * (n + 1)), this.boardHeight / (Math.max(n, rows) + 0.1 * (Math.max(n, rows) + 1)));
    }
    // Match Game22Grid.initializeFigures; additionally fit the upward shifted grid
    // and bottom tray when the host offers less height than the Android full View.
    return Math.min(this.boardWidth / (n + 0.1 * (n + 1)), this.boardHeight / (n + 0.1 * (n + 1) + 2));
  }
  private cellSize(): number { return this.cellEdge() * 1.1; }
  private gridLeft(): number { return (this.boardWidth - this.cellSize() * this.puzzle.columns) / 2; }
  private gridTop(): number {
    const rows = this.puzzle.tiles.length / this.puzzle.columns;
    if (this.puzzle.mode === 'laser') {
      const edge = this.cellEdge();
      return (this.boardHeight - (rows * edge + (rows + 1) * edge * 0.1)) / 2 - edge + edge * 0.05;
    }
    if (this.puzzle.mode === '248') { return (this.boardHeight - this.cellSize() * 6) / 2; }
    // Symmetry Activity uses a square View at 6/8 columns, even at 5/7 rows;
    // GridLayout starts at its top padding rather than centering the short rows.
    const layoutRows = this.puzzle.mode === 'symmetry' && this.puzzle.columns >= 5 ? this.puzzle.columns : rows;
    return (this.boardHeight - layoutRows * this.cellSize()) / 2;
  }
  private centerX(index: number): number { return this.gridLeft() + (index % this.puzzle.columns + 0.5) * this.cellSize(); }
  private centerY(index: number): number { return this.gridTop() + (Math.floor(index / this.puzzle.columns) + 0.5) * this.cellSize(); }

  private cellAt(x: number, y: number): number {
    const size = this.cellSize();
    const column = Math.floor((x - this.gridLeft()) / size);
    const row = Math.floor((y - this.gridTop()) / size);
    const n = this.puzzle.columns;
    if (column < 0 || column >= n || row < 0 || row >= this.puzzle.tiles.length / n) { return -1; }
    const index = row * n + column;
    // GridLayout margins are not clickable cells.
    if (Math.abs(x - this.centerX(index)) > this.cellEdge() / 2 ||
      Math.abs(y - this.centerY(index)) > this.cellEdge() / 2) { return -1; }
    return index;
  }

  private prepareClouds(): void {
    if (this.cloudWidth === this.boardWidth && this.cloudHeight === this.boardHeight) { return; }
    this.cloudWidth = this.boardWidth; this.cloudHeight = this.boardHeight;
    this.cloudCoordinates = [];
    let retries = 0;
    // Game15Grid.buildAndGetClouds: four intrinsic-size PNGs, max 100
    // consecutive retries, reject overlapping 128dp bounding squares.
    while (this.cloudCoordinates.length < 8 && retries < 100) {
      retries++;
      const x = Math.floor(Math.random() * Math.max(1, this.boardWidth - 128));
      const y = Math.floor(Math.random() * Math.max(1, this.boardHeight - 128));
      let overlap = false;
      for (let i = 0; i < this.cloudCoordinates.length; i += 2) {
        if (Math.abs(x - this.cloudCoordinates[i]) <= 128 && Math.abs(y - this.cloudCoordinates[i + 1]) <= 128) {
          overlap = true; break;
        }
      }
      if (!overlap) { this.cloudCoordinates.push(x, y); retries = 0; }
    }
  }

  private preparePlanes(): void {
    if (this.planeWidth === this.boardWidth && this.planeHeight === this.boardHeight) { return; }
    this.planeWidth = this.boardWidth; this.planeHeight = this.boardHeight;
    this.planeCoordinates = [];
    // Game15Grid.buildAndGetFigures caches six non-overlapping bitmap top-left
    // coordinates on its View, including when setGameParams changes the question.
    // Keep that visual state out of Puzzle: revision/elapsed/round changes must not
    // reshuffle it. A new board area rebuilds against its actual dimensions.
    // Bound the entire search to 100 attempts; crowded boards may show fewer
    // planes, as Android does, rather than falling back to visibly regular slots.
    for (let attempt = 0; attempt < 100 && this.planeCoordinates.length < 12; attempt++) {
      const x = Math.floor(Math.random() * Math.max(1, this.boardWidth - 73));
      const y = Math.floor(Math.random() * Math.max(1, this.boardHeight - 73));
      let overlap = false;
      for (let i = 0; i < this.planeCoordinates.length; i += 2) {
        if (Math.abs(x - this.planeCoordinates[i]) <= 73 && Math.abs(y - this.planeCoordinates[i + 1]) <= 73) {
          overlap = true; break;
        }
      }
      if (!overlap) { this.planeCoordinates.push(x, y); }
    }
  }

  private drawPlanes(): void {
    const p = this.puzzle;
    if (p.data.length < 10) { return; }
    const width = this.boardWidth;
    const height = this.boardHeight;
    // Game15Grid draws original PNGs at their mdpi intrinsic sizes: 73dp/128dp.
    // Cloud layout belongs to the View, persists across rounds and freezes on pause.
    this.prepareClouds();
    for (let i = 0; i < this.cloudCoordinates.length; i += 2) {
      const shift = p.data[3] < 0 ? 0 : this.elapsedMs / 4000 * width * (p.data[3] === 1 ? 1 : -1);
      const left = (this.cloudCoordinates[i] + shift) % width;
      const top = this.cloudCoordinates[i + 1];
      const x = (left + width) % width;
      this.sprite('rain_cloud', x + 64, top + 64, 128, 128);
      if (x + 128 > width) { this.sprite('rain_cloud', x - width + 64, top + 64, 128, 128); }
    }
    this.preparePlanes();
    for (let i = 0; i < Math.min(p.tiles.length, this.planeCoordinates.length / 2); i++) {
      const tile = p.tiles[i];
      const distance = this.elapsedMs / p.data[4 + i] * width;
      // Android stores bitmap top-left; Canvas sprite helper takes its center.
      let x = this.planeCoordinates[i * 2] + 36.5;
      let y = this.planeCoordinates[i * 2 + 1] + 36.5;
      if (p.data[1] === 0) { y -= distance; }
      if (p.data[1] === 1) { x += distance; }
      if (p.data[1] === 2) { y += distance; }
      if (p.data[1] === 3) { x -= distance; }
      x = ((x % width) + width) % width;
      y = ((y % height) + height) % height;
      const name = p.data[0] === 0 ? 'paperplane_cyan' : 'paperplane_orange';
      for (let dx = -1; dx <= 1; dx++) {
        for (let dy = -1; dy <= 1; dy++) {
          const px = x + dx * width;
          const py = y + dy * height;
          if (px + 36.5 >= 0 && px - 36.5 <= width && py + 36.5 >= 0 && py - 36.5 <= height) {
            this.sprite(name, px, py, 73, 73, tile.rotation);
          }
        }
      }
    }
  }

  private optionX(option: number): number { return this.boardWidth * (option === 0 ? 0.25 : option === 1 ? 0.75 : 0.5); }
  private optionY(option: number): number { return this.boardHeight * (option === 2 ? 0.75 : 0.5); }
  private optionRadius(): number { return this.boardWidth * 0.175; }
  private pyramidAt(x: number, y: number): number {
    for (let option = 0; option < 3; option++) {
      const dx = x - this.optionX(option); const dy = y - this.optionY(option);
      if (dx * dx + dy * dy <= this.optionRadius() * this.optionRadius()) { return option; }
    }
    return -1;
  }

  private drawPyramid(): void {
    const p = this.puzzle; const n = p.data[0];
    if (p.tiles.length < n * 4) { return; }
    // Game18Grid.drawPyramid uses CloseButton.STROKE_WIDTH == 3, even for 8 layers.
    const height = this.boardHeight * 0.2;
    const step = height / 3;
    const top = 17.5 + (height - n * step) / 2;
    for (let i = 0; i < n; i++) {
      const tile = p.tiles[i]; const width = (this.boardWidth - 70) * tile.value / 1000;
      this.roundedRect((this.boardWidth - width) / 2, top + (n - i - 1) * step, width, step, step * 0.2, tile.color);
    }
    // Android baseline = height * .2 + 1.5 * padding_larger (35dp).
    this.context.fillStyle = '#000000';
    this.context.font = Math.round(this.getUIContext().vp2px(18)) + 'px RobotoLight';
    this.context.textAlign = 'center'; this.context.textBaseline = 'alphabetic';
    this.context.fillText('选择正确答案', this.boardWidth / 2, height + 52.5);
    for (let option = 0; option < 3; option++) {
      this.context.save();
      this.context.shadowBlur = 4; this.context.shadowOffsetX = 2; this.context.shadowOffsetY = 2;
      this.context.shadowColor = '#AA000000';
      for (let i = 0; i < n; i++) {
        const tile = p.tiles[(option + 1) * n + i];
        this.circle(this.optionX(option), this.optionY(option), this.optionRadius() * tile.value / 1000, tile.color);
      }
      this.context.restore();
      if (p.solved && p.answers[0] === option || p.failed && this.pyramidChoice === option) {
        this.sprite(p.solved ? 'rightanswer' : 'wronganswer', this.optionX(option), this.optionY(option), 44, 44);
      }
    }
  }

  private cellRect(index: number, color: string, stroke: string = '#D9D9D9'): void {
    const edge = this.cellEdge(); const x = this.centerX(index) - edge / 2; const y = this.centerY(index) - edge / 2;
    this.context.fillStyle = color; this.context.fillRect(x, y, edge, edge);
    this.context.strokeStyle = stroke; this.context.lineWidth = 1;
    this.context.strokeRect(x + 0.5, y + 0.5, edge - 1, edge - 1);
  }

  private drawFold(): void {
    const p = this.puzzle; const size = this.cellSize(); const edge = this.cellEdge();
    for (let i = 0; i < p.tiles.length; i++) {
      // TwoFoldGrid.repaintGrid removes the selected cell background entirely.
      if (!p.tiles[i].selected) { this.cellRect(i, p.tiles[i].color); }
    }
    if (p.answers.length > 0) {
      this.context.beginPath(); this.context.moveTo(this.centerX(p.answers[0]), this.centerY(p.answers[0]));
      for (let i = 1; i < p.answers.length; i++) { this.context.lineTo(this.centerX(p.answers[i]), this.centerY(p.answers[i])); }
      this.context.strokeStyle = p.tiles[p.answers[0]].color;
      this.context.lineWidth = 15; this.context.lineJoin = 'round'; this.context.lineCap = 'butt'; this.context.stroke();
    }
    for (let i = 0; i < 6; i++) {
      this.sprite('left2', this.gridLeft() - size / 2, this.centerY(i * 6), edge, edge);
      this.sprite('right2', this.gridLeft() + size * 6.5, this.centerY(i * 6), edge, edge);
      this.sprite('up2', this.centerX(i), this.gridTop() - size / 2, edge, edge);
      this.sprite('down2', this.centerX(i), this.gridTop() + size * 6.5, edge, edge);
    }
  }

  private drawSymmetry(): void {
    const p = this.puzzle; const edge = this.cellEdge();
    // activity_game21 has a split root, NOT a blue axis line or a white card.
    this.context.fillStyle = '#DCDCDC'; this.context.fillRect(0, 0, this.boardWidth / 2, this.boardHeight);
    for (let i = 0; i < p.tiles.length; i++) {
      const tile = p.tiles[i];
      if (i % p.columns < p.columns / 2) {
        if (tile.active) {
          this.context.fillStyle = '#555555';
          this.context.fillRect(this.centerX(i) - edge / 2, this.centerY(i) - edge / 2, edge, edge);
        } else { this.circle(this.centerX(i), this.centerY(i), edge * 0.15, '#666666'); }
      } else {
        this.cellRect(i, tile.selected && !tile.active ? '#F44336' : tile.active ? '#03A9F4' : '#FFFFFF');
        if (tile.selected && !tile.active || p.solved && p.answers[p.answers.length - 1] === i) {
          this.sprite(tile.active ? 'rightanswer' : 'wronganswer', this.centerX(i), this.centerY(i), edge, edge);
        }
      }
    }
  }

  private mirror(x: number, y: number, orientation: number, size: number, selected: boolean): void {
    // Exact two polygon faces from Game22Grid.Figure; no invented selection halo.
    const left = x - size / 2; const top = y - size / 2; const pad = size * 0.1;
    if (orientation === 4) {
      this.polygon([left + pad, top + pad, left + 2 * pad, top + pad,
        left + size - pad, top + size - 2 * pad, left + size - pad, top + size - pad], '#03A9F4');
      this.polygon([left + pad, top + pad, left + pad, top + 2 * pad,
        left + size - 2 * pad, top + size - pad, left + size - pad, top + size - pad], '#92D5F3');
    } else {
      this.polygon([left + size - pad, top + pad, left + size - 2 * pad, top + pad,
        left + pad, top + size - 2 * pad, left + pad, top + size - pad], '#03A9F4');
      this.polygon([left + size - pad, top + pad, left + size - pad, top + 2 * pad,
        left + 2 * pad, top + size - pad, left + pad, top + size - pad], '#92D5F3');
    }
  }

  private trayY(): number { return this.boardHeight - this.cellEdge() * 1.1; }
  private trayX(token: number): number { return this.boardWidth / 2 + (token - (this.puzzle.data[4] - 1) / 2) * this.cellSize(); }
  private trayAt(x: number, y: number): number {
    for (let i = 0; i < this.puzzle.data[4]; i++) {
      if (Math.abs(x - this.trayX(i)) <= this.cellEdge() / 2 && Math.abs(y - this.trayY()) <= this.cellEdge() / 2) { return i; }
    }
    return -1;
  }
  private tokenAt(index: number): number {
    const count = this.puzzle.data[4];
    for (let i = 0; i < count; i++) { if (this.puzzle.data[6 + count + i] === index) { return i; } }
    return -1;
  }

  private receiverRotation(index: number): number {
    const n = this.puzzle.columns;
    if (index % n === n - 1) { return 180; }
    if (Math.floor(index / n) === 0) { return 90; }
    if (Math.floor(index / n) === n - 1) { return 270; }
    return 0;
  }

  private beamPath(center: boolean): void {
    const p = this.puzzle; const path = p.answers;
    if (path.length < 2) { return; }
    const edge = this.cellEdge(); const padding = 5 + edge * 0.1;
    const c = this.context;
    c.beginPath(); c.moveTo(this.centerX(path[0]), this.centerY(path[0]));
    for (let i = 1; i < path.length; i++) {
      const x = this.centerX(path[i]); const y = this.centerY(path[i]);
      if (i < path.length - 1 && this.tokenAt(path[i]) >= 0) {
        const previousX = this.centerX(path[i - 1]); const previousY = this.centerY(path[i - 1]);
        const nextX = this.centerX(path[i + 1]); const nextY = this.centerY(path[i + 1]);
        c.lineTo(x + Math.sign(previousX - x) * padding, y + Math.sign(previousY - y) * padding);
        c.lineTo(x + Math.sign(nextX - x) * padding, y + Math.sign(nextY - y) * padding);
      } else if (i === path.length - 1) {
        const previousX = this.centerX(path[i - 1]); const previousY = this.centerY(path[i - 1]);
        const dx = Math.sign(x - previousX); const dy = Math.sign(y - previousY);
        // Both Android and engine include the terminal blocker/receiver in path.
        // Stop at midpoint + .2 cell; border/core have independent terminal ends.
        let borderEnd = -this.cellSize() / 2 + edge * 0.2;
        if (path[i] !== p.data[1]) { borderEnd -= edge * 0.05; }
        const extension = borderEnd + (center ? 10 : 0);
        c.lineTo(x + dx * extension, y + dy * extension);
      } else { c.lineTo(x, y); }
    }
    c.strokeStyle = center ? '#F34334' : '#ED8988'; c.lineWidth = center ? 4 : 10;
    c.lineJoin = 'miter'; c.lineCap = 'butt'; c.stroke();
  }

  private drawLaser(): void {
    const p = this.puzzle; const edge = this.cellEdge(); const count = p.data[4];
    for (let i = 0; i < p.tiles.length; i++) {
      const tile = p.tiles[i]; if (tile.hidden || tile.value < 0) { continue; }
      if (tile.value === 0 || tile.value === 4 || tile.value === 5) {
        const lit = p.answers.indexOf(i) >= 0;
        this.cellRect(i, lit ? '#F2F8FC' : '#FFFFFF', lit ? '#E6EDF1' : '#D9D9D9');
      } else if (tile.value === 1) {
        const left = this.centerX(i) - edge / 2; const top = this.centerY(i) - edge / 2;
        this.context.fillStyle = '#B8B8B8'; this.context.fillRect(left, top, edge, edge);
        this.polygon([left, top, left + edge, top, left + edge, top + edge], '#A0A0A0');
      } else if (tile.value === 3) {
        this.sprite(p.data[5] === 1 ? 'point_red' : 'point_gray', this.centerX(i), this.centerY(i), edge, edge, this.receiverRotation(i));
      }
    }
    for (let i = 0; i < count; i++) {
      const x = this.trayX(i); const y = this.trayY();
      this.context.fillStyle = '#FFFFFF'; this.context.fillRect(x - edge / 2, y - edge / 2, edge, edge);
      this.context.strokeStyle = '#D9D9D9'; this.context.lineWidth = 1;
      this.context.strokeRect(x - edge / 2 + 0.5, y - edge / 2 + 0.5, edge - 1, edge - 1);
      const position = p.data[6 + count + i];
      if (!(this.dragging && this.dragToken === i)) {
        this.mirror(position < 0 ? x : this.centerX(position), position < 0 ? y : this.centerY(position), p.data[6 + i], edge, false);
      }
    }
    if (this.dragging && this.dragToken >= 0) { this.mirror(this.dragX, this.dragY, p.data[6 + this.dragToken], edge, false); }
    this.beamPath(false); this.beamPath(true);
    // Game22Grid draws the animated source LAST, masking the start of the beam.
    const source = p.data[0];
    this.sprite('laser_' + (Math.floor(this.elapsedMs / 100) % 6 + 1), this.centerX(source), this.centerY(source), edge, edge, (p.data[2] + 2) % 4 * 90);
  }

  private redraw(): void {
    if (!this.ready) { return; }
    this.context.clearRect(0, 0, this.boardWidth, this.boardHeight);
    if (this.puzzle.mode === 'plane') { this.drawPlanes(); }
    else if (this.puzzle.mode === 'pyramid') { this.drawPyramid(); }
    else if (this.puzzle.mode === '248') { this.drawFold(); }
    else if (this.puzzle.mode === 'symmetry') { this.drawSymmetry(); }
    else if (this.puzzle.mode === 'laser') { this.drawLaser(); }
  }

  private foldArrow(x: number, y: number): boolean {
    const size = this.cellSize();
    const column = Math.floor((x - this.gridLeft()) / size);
    const row = Math.floor((y - this.gridTop()) / size);
    if (column >= 0 && column < 6 && row === -1) { this.emit('swipe', column, 0); return true; }
    if (column >= 0 && column < 6 && row === 6) { this.emit('swipe', 30 + column, 2); return true; }
    if (row >= 0 && row < 6 && column === -1) { this.emit('swipe', row * 6, 3); return true; }
    if (row >= 0 && row < 6 && column === 6) { this.emit('swipe', row * 6 + 5, 1); return true; }
    return false;
  }

  private touch(event: TouchEvent): void {
    if (this.puzzle.solved || this.puzzle.failed) { return; }
    const touches = event.type === TouchType.Up || event.type === TouchType.Cancel ? event.changedTouches : event.touches;
    if (touches.length === 0) { return; }
    const x = touches[0].x; const y = touches[0].y; const mode = this.puzzle.mode;
    if (event.type === TouchType.Down) {
      this.startX = x; this.startY = y;
      this.startIndex = this.cellAt(x, y); this.lastIndex = this.startIndex;
      this.dragging = false; this.dragToken = -1; this.chain = false;
      if (mode === 'laser') {
        this.dragToken = this.trayAt(x, y);
        if (this.dragToken < 0 && this.startIndex >= 0) { this.dragToken = this.tokenAt(this.startIndex); }
        if (this.dragToken >= 0) { this.emit('choice', this.dragToken); }
      }
      return;
    }
    if (event.type === TouchType.Move) {
      const dx = x - this.startX; const dy = y - this.startY;
      if (Math.abs(dx) + Math.abs(dy) > 8) { this.dragging = true; }
      if (mode === 'laser' && this.dragToken >= 0) { this.dragX = x; this.dragY = y; this.redraw(); }
      if (mode === '248' && this.startIndex >= 0) {
        const index = this.cellAt(x, y);
        if (index >= 0 && index !== this.lastIndex) {
          if (!this.chain && this.puzzle.tiles[index].value === this.puzzle.tiles[this.startIndex].value &&
            Math.abs(index % 6 - this.startIndex % 6) + Math.abs(Math.floor(index / 6) - Math.floor(this.startIndex / 6)) === 1) {
            this.chain = true; this.emit('tile', this.startIndex);
          }
          if (this.chain) { this.emit('tile', index); }
          this.lastIndex = index;
        }
      }
      return;
    }
    if (event.type === TouchType.Cancel) {
      if (mode === '248' && this.chain) { this.emit('choice', 1); }
      this.dragging = false; this.dragToken = -1; this.redraw(); return;
    }
    if (event.type !== TouchType.Up) { return; }
    const index = this.cellAt(x, y);
    if (mode === 'plane') {
      const dx = x - this.startX; const dy = y - this.startY;
      if (Math.max(Math.abs(dx), Math.abs(dy)) >= 18) { this.emit('swipe', 0, Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0)); }
    } else if (mode === 'pyramid') {
      const option = this.pyramidAt(x, y);
      if (option >= 0) { this.pyramidChoice = option; this.emit('choice', option); }
    } else if (mode === 'laser') {
      if (this.dragging && this.dragToken >= 0) {
        if (index >= 0 && this.puzzle.tiles[index].value === 0) { this.emit('tile', index); }
        else if (Math.abs(y - this.trayY()) < this.cellEdge()) { this.emit('choice', this.puzzle.data[4]); }
      } else if (this.dragToken < 0 && index >= 0) { this.emit('tile', index); }
    } else if (mode === 'symmetry') {
      if (!this.dragging && index >= 0) { this.emit('tile', index); }
    } else if (mode === '248') {
      if (this.chain) { this.emit('choice', 0); }
      else if (!this.dragging && this.foldArrow(x, y)) { /* Arrow already emitted. */ }
      else if (this.dragging && this.startIndex >= 0) {
        const dx = x - this.startX; const dy = y - this.startY;
        this.emit('swipe', this.startIndex, Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0));
      } else if (index >= 0) { this.emit('tile', index); }
    }
    this.dragging = false; this.dragToken = -1; this.redraw();
  }

  build() {
    Canvas(this.context)
      .width('100%')
      .height(Math.max(240, this.availableHeight))
      .clip(true)
      .onReady(() => { this.ready = true; this.redraw(); })
      .onAreaChange((oldArea: Area, area: Area) => {
        this.boardWidth = Number.parseFloat(area.width.toString());
        this.boardHeight = Number.parseFloat(area.height.toString());
        this.redraw();
      })
      .onTouch((event: TouchEvent) => { this.touch(event); })
  }
}
