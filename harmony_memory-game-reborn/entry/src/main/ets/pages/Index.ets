import { common } from '@kit.AbilityKit';
import { GAMES, GameInfo, media } from '../core/GameCatalog';
import { Puzzle, Move } from '../core/Contract';
import { createPuzzle, applyMove, reveal, animate } from '../core/GameEngine';
import { memoryReady } from '../core/MemoryGames';
import { ProgressStore } from '../services/ProgressStore';
import { SoundService } from '../services/SoundService';
import { SpatialBoard } from '../components/SpatialBoard';
import { MemoryBoard } from '../components/MemoryBoard';
import { QuickBoard } from '../components/QuickBoard';
import { revealQuickPuzzle } from '../core/QuickGames';
import { AndroidHome, AndroidTutorial, AndroidPause, AndroidResult, AndroidFailure } from '../components/AndroidScreens';
import { ReadyIndicator } from '../components/ReadyIndicator';
import { AndroidChoiceBoard } from '../components/AndroidChoiceBoard';

@Entry
@Component
struct Index {
  @StorageLink('appActive') @Watch('activeChanged') appActive: boolean = true;
  @State screen: string = 'home';
  @State gameIndex: number = 0;
  @State puzzle: Puzzle = new Puzzle();
  @State phase: string = 'ready';
  @State paused: boolean = false;
  @State tutorialFromPause: boolean = false;
  @State tutorialPage: number = 0;
  @State countdown: number = 0;
  @State lastGame: number = -1;
  @State resultTitle: string = '你完成了本局';
  @State level: number = 1;
  @State score: number = 0;
  @State lives: number = 2;
  @State remaining: number = 0;
  @State message: string = '';
  @State recordVersion: number = 0;
  @State soundOn: boolean = true;
  @State loaded: boolean = false;
  @State boardHeight: number = 450;
  @State introElapsedMs: number = -1;
  @State tickVersion: number = 0;
  private store: ProgressStore = new ProgressStore();
  private sound: SoundService = new SoundService();
  private timer: number = -1;
  private lastTick: number = 0;
  private phaseElapsed: number = 0;
  private roundElapsed: number = 0;
  private feedbackCorrect: boolean = false;
  private savedSession: boolean = false;
  private alive: boolean = false;

  async aboutToAppear(): Promise<void> {
    this.alive = true;
    this.getUIContext().getFont().registerFont({ familyName: 'RobotoLight', familySrc: $rawfile('fonts/Roboto-Light.ttf') });
    this.getUIContext().getFont().registerFont({ familyName: 'RobotoRegular', familySrc: $rawfile('fonts/Roboto-Regular.ttf') });
    this.getUIContext().getFont().registerFont({ familyName: 'RobotoMedium', familySrc: $rawfile('fonts/Roboto-Medium.ttf') });
    this.getUIContext().getFont().registerFont({ familyName: 'RobotoBold', familySrc: $rawfile('fonts/Roboto-Bold.ttf') });
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    await this.store.load(context);
    if (!this.alive) { return; }
    this.soundOn = this.store.soundEnabled();
    this.sound.enabled = this.soundOn;
    this.loaded = true;
    this.recordVersion++;
    this.sound.load(context);
    this.lastTick = Date.now();
    this.timer = setInterval(() => this.tick(), 40);
  }

  aboutToDisappear(): void {
    this.alive = false;
    if (this.timer >= 0) { clearInterval(this.timer); }
    this.saveSession();
    this.sound.release();
  }

  onBackPress(): boolean {
    if (this.screen === 'game') { this.pause(); return true; }
    if (this.screen === 'tutorial') {
      if (this.tutorialFromPause) { this.screen = 'game'; }
      else { this.screen = 'home'; }
      return true;
    }
    if (this.screen === 'result') { this.screen = 'home'; return true; }
    if (this.screen === 'failure') { return true; }
    return false;
  }

  private activeChanged(): void {
    this.lastTick = Date.now();
    if (!this.appActive) {
      this.pause(); this.sound.silence();
      if (!this.savedSession && (this.screen === 'game' || (this.screen === 'tutorial' && this.tutorialFromPause))) {
        this.store.checkpoint(this.game().id, this.level, this.score);
      }
    }
  }

  private game(): GameInfo { return GAMES[this.gameIndex]; }

  private selectGame(index: number): void {
    if (!this.loaded) { return; }
    this.gameIndex = index;
    this.tutorialPage = 0;
    this.tutorialFromPause = false;
    if (!this.store.record(this.game().id).tutorialSeen) { this.screen = 'tutorial'; }
    else { this.start(); }
  }

  private start(): void {
    this.store.seenTutorial(this.game().id);
    this.level = this.store.startingLevel(this.game().id, this.game().adaptive);
    this.score = 0; this.lives = 2; this.remaining = 0;
    this.savedSession = false;
    this.paused = false;
    this.screen = 'game';
    this.puzzle = createPuzzle(this.game().id, this.level, null);
    this.store.reached(this.game().id, this.level);
    this.phase = 'ready'; this.countdown = 0;
    this.introElapsedMs = this.hasIntro() ? 0 : -1;
    this.lastGame = this.gameIndex;
    this.phaseElapsed = 0; this.roundElapsed = 0;
    this.message = '准备好，专注当下';
    this.lastTick = Date.now();
    if ([1, 2, 3, 5, 6, 8, 9].indexOf(this.game().id) < 0) { this.beginRound(); }
  }

  private hasIntro(): boolean { return [1, 2, 3, 5, 6, 8, 9].indexOf(this.game().id) >= 0; }

  private beginRound(): void {
    this.phaseElapsed = 0;
    this.roundElapsed = 0;
    this.phase = this.puzzle.previewMs > 0 ? 'preview' : 'input';
    this.message = this.phase === 'preview' ? '仔细观察并记住' : this.puzzle.subtitle;
    if (this.puzzle.timeLimitMs > 0 && (this.remaining <= 0 || this.game().id === 11 || this.game().id === 12)) {
      this.remaining = this.puzzle.timeLimitMs;
    }
  }

  private tick(): void {
    const now = Date.now();
    const delta = Math.min(200, Math.max(0, now - this.lastTick));
    this.lastTick = now;
    if (this.screen !== 'game' || this.paused || !this.appActive) { return; }
    this.phaseElapsed += delta;
    if (this.phase === 'ready') {
      this.countdown = Math.min(1, this.phaseElapsed / 800);
      if (this.phaseElapsed >= 800) {
        this.phase = 'intro'; this.phaseElapsed = 0; this.introElapsedMs = 0;
      }
      return;
    }
    if (this.phase === 'intro') {
      this.introElapsedMs = this.phaseElapsed;
      if (this.phaseElapsed >= (this.game().id === 6 ? 1200 : 700)) { this.beginRound(); }
      this.tickVersion++;
      return;
    }
    if (this.phase === 'hide') {
      this.roundElapsed += delta;
      this.tickVersion++;
      if (this.phaseElapsed >= 200) { this.phase = 'input'; }
      return;
    }
    if (this.remaining > 0 && ((this.game().id !== 11 && this.game().id !== 12) || this.phase === 'input')) {
      this.remaining = Math.max(0, this.remaining - delta);
      if (this.remaining === 0) {
        if (this.game().id === 11 || this.game().id === 12) { this.feedback(false, 0); }
        else { this.finish('时间到，训练完成'); }
        return;
      }
    }
    if (this.phase === 'feedback') {
      if (this.phaseElapsed >= (this.game().id === 5 ? (this.feedbackCorrect ? 200 : 2000) : (this.feedbackCorrect ? 650 : 1400))) {
        if (!this.feedbackCorrect && this.lives <= 0) { this.screen = 'failure'; }
        else { this.nextRound(this.feedbackCorrect); }
      }
      return;
    }
    this.roundElapsed += delta;
    animate(this.puzzle, this.roundElapsed);
    if (this.phase === 'preview' && this.roundElapsed >= this.puzzle.previewMs) {
      reveal(this.puzzle);
      if (this.game().id === 16) {
        this.puzzle = revealQuickPuzzle(this.puzzle);
        this.level = this.puzzle.level;
        this.store.reached(this.game().id, this.level);
      }
      this.phase = this.game().id === 5 ? 'hide' : 'input';
      this.phaseElapsed = 0;
      this.message = this.puzzle.subtitle;
    }
    if (this.puzzle.solved) { this.feedback(true, 0); }
    this.tickVersion++;
  }

  private nextRound(success: boolean): void {
    if (this.game().id === 23 && !success) {
      this.puzzle.failed = false;
      this.phase = 'input'; this.phaseElapsed = 0;
      this.message = this.puzzle.subtitle;
      return;
    }
    const previous = this.puzzle;
    if (success) { this.level++; }
    this.puzzle = createPuzzle(this.game().id, this.level, success || this.game().id === 16 ? previous : null);
    this.store.reached(this.game().id, this.level);
    this.recordVersion++;
    if (this.hasIntro()) {
      this.phase = 'ready'; this.phaseElapsed = 0; this.roundElapsed = 0; this.countdown = 0; this.introElapsedMs = 0;
    } else { this.beginRound(); }
  }

  private move(kind: string, index: number, direction: number = 0): void {
    if (this.screen !== 'game' || this.paused || this.phase !== 'input') { return; }
    if (this.game().id <= 9 && !memoryReady(this.puzzle)) { return; }
    if (kind === 'tile' && (index < 0 || index >= this.puzzle.tiles.length || this.puzzle.tiles[index].disabled ||
      (this.puzzle.tiles[index].selected && this.game().id !== 17 && this.game().id !== 21))) { return; }
    const move = new Move(); move.kind = kind; move.index = index; move.direction = direction;
    const outcome = applyMove(this.puzzle, move);
    this.puzzle.tiles = this.puzzle.tiles.slice();
    this.tickVersion++;
    if (!outcome.correct) {
      if (this.puzzle.failed) { this.feedback(false, 0); }
      else if (outcome.message.length > 0) { this.message = outcome.message; }
    }
    else {
      this.sound.play('tap');
      if (outcome.message.length > 0) { this.message = outcome.message; }
      this.score += outcome.points;
      if (this.game().id === 23) {
        this.level = Math.min(48, this.puzzle.answers.length + 1);
        this.store.reached(this.game().id, this.level);
      }
      if (outcome.complete) {
        if (this.game().id === 23) { this.finish('48 个数字全部找到，挑战成功'); }
        else { this.feedback(true, 0); }
      }
    }
  }

  private feedback(correct: boolean, points: number): void {
    if (this.phase === 'feedback') { return; }
    this.feedbackCorrect = correct;
    this.phase = 'feedback'; this.phaseElapsed = 0;
    this.score += points;
    if (correct) { this.message = '太棒了！'; this.sound.play('win'); }
    else {
      this.lives--;
      this.message = this.lives > 0 ? '再试一次，留意答案' : '本轮结束';
      this.sound.play('wrong');
      if (this.game().id <= 9) {
        for (const target of this.puzzle.target) {
          if (target >= 0 && target < this.puzzle.tiles.length) {
            this.puzzle.tiles[target].hidden = false;
            this.puzzle.tiles[target].active = true;
          }
        }
      }
      this.puzzle.tiles = this.puzzle.tiles.slice();
    }
  }

  private saveSession(): void {
    if (this.savedSession || (this.screen !== 'game' && this.screen !== 'result' && this.screen !== 'failure' && !this.tutorialFromPause)) { return; }
    this.store.finish(this.game().id, this.level, this.score);
    this.savedSession = true;
    this.recordVersion++;
  }

  private finish(text: string): void {
    this.message = text;
    this.resultTitle = text.indexOf('时间') >= 0 ? '时间到' : '你完成了本局';
    this.saveSession();
    this.screen = 'result'; this.paused = false;
    this.sound.play(this.lives <= 0 ? 'error' : 'win');
  }

  private pause(): void {
    if (this.screen === 'game') { this.paused = true; this.sound.silence(); }
  }

  private resume(): void { this.paused = false; this.lastTick = Date.now(); }

  private toggleSound(): void {
    this.soundOn = !this.soundOn;
    this.sound.enabled = this.soundOn;
    this.store.setSound(this.soundOn);
    if (!this.soundOn) { this.sound.silence(); }
  }

  private openTutorial(): void {
    this.paused = true; this.tutorialFromPause = true;
    this.tutorialPage = 0; this.screen = 'tutorial';
  }

  private trophy(): string {
    const completed = this.game().id === 23 ? this.level : Math.max(0, this.level - 1);
    const thresholds = this.game().trophies;
    const names: string[] = ['水晶奖杯', '铜杯', '银杯', '金杯'];
    let earned = '';
    for (let i = 0; i < thresholds.length && i < names.length; i++) {
      if (completed >= thresholds[i]) { earned = names[i]; }
    }
    return earned;
  }

  private highestLevels(): number[] {
    return GAMES.map((game: GameInfo) => this.store.record(game.id).highest);
  }

  private showsLives(): boolean {
    return [13, 17, 22].indexOf(this.game().id) < 0;
  }

  private gameBackground(): string { return '#E9F5FB'; }

  @Builder
  private Header() {
    Stack({ alignContent: Alignment.Center }) {
      Text(this.game().name).fontFamily('RobotoLight').fontSize(18).fontColor('#000000').padding({ top: 12 })
      Row() {
        Column() {
          Image(media('ic_pause_new')).width(28).height(28).objectFit(ImageFit.Contain)
        }.width(48).height(48).padding({ left: 12, top: 12 }).justifyContent(FlexAlign.Center)
          .onClick(() => this.pause()).accessibilityText('暂停')
        Column() {
          Image(media(this.soundOn ? 'speaker' : 'mute')).width(24).height(24).objectFit(ImageFit.Contain)
        }.width(44).height(48).padding({ top: 12 }).justifyContent(FlexAlign.Center)
          .onClick(() => this.toggleSound())
          .accessibilityText(this.soundOn ? '关闭声音' : '开启声音')
        Blank()
        if (this.showsLives()) {
          Row({ space: 4 }) {
            Image(media('ic_life')).width(24).height(24).opacity(this.lives >= 2 ? 1 : 0)
            Image(media('ic_life')).width(24).height(24).opacity(this.lives >= 1 ? 1 : 0)
          }.height(48).padding({ right: 12, top: 12 })
        }
      }.width('100%').height(48)
    }.width('100%').height(48)
  }

  @Builder
  private ProgressBar() {
    Row() {
      if (this.puzzle.timeLimitMs > 0) {
        Row().width(`${Math.max(0, Math.min(100, 100 * this.remaining / this.puzzle.timeLimitMs))}%`).height(4).backgroundColor('#03A9F4')
      }
    }.width('100%').height(4).backgroundColor(this.puzzle.timeLimitMs > 0 ? '#99CCCCCC' : Color.Transparent).margin({ top: 4 })
  }

  @Builder
  private GameSurface() {
    Stack() {
      if (this.game().id === 21) {
        Row() {
          Row().width('50%').height('100%').backgroundColor('#DCDCDC')
          Row().width('50%').height('100%').backgroundColor('#E5F6FE')
        }.width('100%').height('100%')
      }
    Column() {
      this.Header()
      if (this.game().id === 17) { this.ProgressBar() }
      Text(this.game().id === 17 ? `得分：${this.score}` : `第 ${this.level} 关`).fontFamily('RobotoLight').fontSize(14).fontColor('#000000')
        .width('100%').textAlign(TextAlign.Center).height(this.game().id === 17 ? 38 : 24)
      if (this.game().id !== 17 && this.game().id !== 19) { this.ProgressBar() }
      if (this.game().id === 17) {
        Text(`${this.puzzle.data.length > 0 ? this.puzzle.data[0] : 0}`).width(48).height(48).textAlign(TextAlign.Center)
          .fontFamily('RobotoBold').fontSize(18).fontColor('#FFFFFF').backgroundColor('#5C6BC0').borderRadius(24)
          .margin({ top: 5 }).visibility(this.puzzle.answers.length >= 2 ? Visibility.Visible : Visibility.Hidden)
      }
      Stack({ alignContent: Alignment.Center }) {
        if (this.game().id <= 9) {
          MemoryBoard({ puzzle: this.puzzle, elapsedMs: this.roundElapsed, revision: this.tickVersion,
            availableHeight: this.boardHeight, introElapsedMs: this.introElapsedMs,
            onMove: (move: Move) => this.move(move.kind, move.index, move.direction) })
          if (this.game().id === 9 && this.puzzle.targetIcon.length > 0) {
            Image(media(this.puzzle.targetIcon)).width('100%').height(80).objectFit(ImageFit.Contain)
              .position({ x: 0, y: 8 })
          }
          if (this.game().id === 5 && (this.phase === 'input' || this.phase === 'feedback')) {
            AndroidChoiceBoard({ puzzle: this.puzzle, availableHeight: this.boardHeight, revision: this.tickVersion,
              interactive: this.phase === 'input' && !this.paused, soundOn: this.soundOn,
              onSound: () => this.toggleSound(), onMove: (move: Move) => this.move(move.kind, move.index, move.direction) })
          }
        } else if ([15, 17, 18, 21, 22].indexOf(this.game().id) >= 0) {
          SpatialBoard({ puzzle: this.puzzle, elapsedMs: this.roundElapsed, revision: this.tickVersion,
            availableHeight: this.boardHeight,
            onMove: (move: Move) => this.move(move.kind, move.index, move.direction) })
        } else if ([10, 13, 14, 16, 19, 20, 23].indexOf(this.game().id) >= 0) {
          Column() {
            QuickBoard({ puzzle: this.puzzle, revision: this.tickVersion,
              availableHeight: this.game().id === 16 || this.game().id === 20 ? Math.max(0, this.boardHeight - 90) : this.boardHeight,
              interactive: this.phase === 'input' && !this.paused,
              onMove: (move: Move) => this.move(move.kind, move.index, move.direction) })
            if (this.game().id === 16 || this.game().id === 20) {
              AndroidChoiceBoard({ puzzle: this.puzzle, availableHeight: 90, controlsOnly: true, revision: this.tickVersion,
                interactive: this.phase === 'input' && !this.paused,
                onMove: (move: Move) => this.move(move.kind, move.index, move.direction) })
            }
          }.width('100%').height('100%')
        } else {
          AndroidChoiceBoard({ puzzle: this.puzzle, availableHeight: this.boardHeight, revision: this.tickVersion,
            interactive: this.phase === 'input' && !this.paused,
            onMove: (move: Move) => this.move(move.kind, move.index, move.direction) })
        }
        if (this.game().id === 19 && this.remaining > 0) {
          Row() {
            Row().width(`${Math.max(0, Math.min(100, this.remaining / this.puzzle.timeLimitMs * 100))}%`)
              .height(4).backgroundColor('#37BC84')
          }.width('100%').height(4).backgroundColor('#FFFFFF')
            .position({ x: 0, y: Math.max(0, (this.boardHeight - 4) / 8) })
        }
        if (this.phase === 'ready') {
          Column() {
            ReadyIndicator({ progress: this.countdown })
            Text('准备！').fontSize(34).fontFamily('RobotoLight').fontColor('#03A9F4')
          }.width('100%').height('100%').justifyContent(FlexAlign.Center).backgroundColor('#AAE9F5FB')
        }
        if (this.screen === 'result') { Row().width('100%').height('100%').backgroundColor('#E5F6FE') }
      }.width('100%').layoutWeight(1)
        .onAreaChange((_old: Area, area: Area) => this.boardHeight = Number.parseFloat(area.height.toString()))
    }.width('100%').height('100%').backgroundColor(this.game().id === 21 ? Color.Transparent : this.gameBackground())
    }.width('100%').height('100%')
  }

  build() {
    Column() {
      if (this.screen === 'home') {
        AndroidHome({ records: this.highestLevels(), lastGame: this.lastGame,
          onSelect: (index: number) => this.selectGame(index) })
      } else if (this.screen === 'tutorial') {
        AndroidTutorial({ gameIndex: this.gameIndex, fromPause: this.tutorialFromPause,
          onExit: () => { this.screen = this.tutorialFromPause ? 'game' : 'home'; },
          onPlay: () => this.start() })
      } else {
        Stack() {
          this.GameSurface()
          if (this.paused && this.screen === 'game') {
            AndroidPause({ onResume: () => this.resume(),
              onRestart: () => { this.saveSession(); this.start(); }, onTutorial: () => this.openTutorial(),
              onHome: () => { this.saveSession(); this.paused = false; this.screen = 'home'; },
              onEnd: () => this.finish('训练记录已保存') })
          }
          if (this.screen === 'result') {
            AndroidResult({ gameIndex: this.gameIndex, level: this.level, best: this.store.record(this.game().id).highest,
              title: this.resultTitle, onReplay: () => this.start(), onHome: () => { this.screen = 'home'; },
              onRecommend: (index: number) => this.selectGame(index) })
          }
          if (this.screen === 'failure') {
            AndroidFailure({ gameIndex: this.gameIndex, level: this.level,
              onRestart: () => { this.saveSession(); this.start(); }, onEnd: () => this.finish('本局结束') })
          }
        }.width('100%').height('100%')
      }
    }.width('100%').height('100%').padding({ top: 36 }).backgroundColor('#E5F6FE')
  }
}
