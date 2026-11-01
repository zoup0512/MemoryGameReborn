import { common } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
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
import { AndroidHome, AndroidTutorial, AndroidPause, AndroidResult, AndroidFailure, AndroidUnlock,
  StageDetail, StageResult, AndroidDiscovery } from '../components/AndroidScreens';
import { StageMap } from '../components/StageMap';
import { levelCoins, scoreCoins248 } from '../core/Economy';
import { STAGES, StageInfo, stageScore, starsFor, stageUnit, validStage } from '../core/StageCatalog';
import { ReadyIndicator } from '../components/ReadyIndicator';
import { AndroidChoiceBoard } from '../components/AndroidChoiceBoard';
import { AbilityGrowth } from '../components/AbilityScreen';
import { GameStat } from '../core/BrainProfile';

// Game surfaces keep the Android light palette in both color modes.
const GAME_INK: string = '#13171C';
// Bottom tab order; the screen value of each tab.
const TAB_SCREENS: string[] = ['map', 'training', 'growth'];

@Entry
@Component
struct Index {
  @StorageLink('appActive') @Watch('activeChanged') appActive: boolean = true;
  @StorageLink('darkMode') @Watch('syncSystemBar') darkMode: boolean = false;
  // 'map' | 'training' | 'growth' tabs, then 'tutorial' | 'game' | 'result' | 'failure'.
  @State @Watch('syncSystemBar') screen: string = 'map';
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
  @State coins: number = 0;
  @State unlockedIds: number[] = [];
  @State unlockIndex: number = -1;
  @State purchasing: boolean = false;
  @State purchaseError: string = '';
  @State storageError: string = '';
  @State retryingStorage: boolean = false;
  // Level map. stageIndex -1 means free training (endless rules).
  @State stageIndex: number = -1;
  @State stageDetail: number = -1;
  @State stageStars: number[] = [];
  @State currentStage: number = 0;
  @State stageEarned: number = 0;
  @State stageBest: number = 0;
  @State stageValue: number = 0;
  @State stageCoins: number = 0;
  @State discoveryIndex: number = -1;
  private stageSettled: boolean = false;
  // GAMES index revealed by the last clear, shown when the player returns to the map.
  private pendingDiscovery: number = -1;
  // Stage whose detail opens after the discovery dialog closes.
  private pendingStage: number = -1;
  private store: ProgressStore = new ProgressStore();
  private sound: SoundService = new SoundService();
  private timer: number = -1;
  private lastTick: number = 0;
  private phaseElapsed: number = 0;
  private roundElapsed: number = 0;
  private feedbackCorrect: boolean = false;
  private savedSession: boolean = false;
  private alive: boolean = false;
  // Bottom tab ('map' | 'training' | 'growth') to return to after a tutorial or game.
  private tab: string = 'map';

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
    this.refreshEconomy();
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
    if (this.unlockIndex >= 0) { this.cancelUnlock(); return true; }
    if (this.discoveryIndex >= 0) { this.closeDiscovery(); return true; }
    if (this.stageDetail >= 0) { this.stageDetail = -1; return true; }
    if (this.screen === 'game') { this.pause(); return true; }
    if (this.screen === 'tutorial') {
      if (this.tutorialFromPause) { this.screen = 'game'; }
      else { this.leaveSession(); }
      return true;
    }
    if (this.screen === 'result') { this.leaveSession(); return true; }
    // Back on the training or growth tab first returns to the map, then exits.
    if (this.screen === 'growth' || this.screen === 'training') { this.screen = 'map'; this.tab = 'map'; return true; }
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

  private refreshEconomy(): void {
    this.loaded = this.store.ready();
    this.coins = this.store.coins();
    this.unlockedIds = this.store.unlockedGames();
    this.stageStars = this.store.stageStarList();
    this.currentStage = this.store.currentStage();
    this.storageError = this.store.error;
  }

  private async retryStorage(): Promise<void> {
    if (this.retryingStorage || this.purchasing) { return; }
    this.retryingStorage = true;
    if (this.store.ready()) {
      this.store.flush();
      await this.store.settled();
    } else {
      await this.store.load(this.getUIContext().getHostContext() as common.UIAbilityContext);
      this.soundOn = this.store.soundEnabled();
      this.sound.enabled = this.soundOn;
    }
    this.refreshEconomy();
    this.retryingStorage = false;
  }

  private async reward(amount: number): Promise<void> {
    if (amount <= 0) { return; }
    const saved = this.store.earnCoins(amount);
    this.refreshEconomy();
    await saved;
    this.refreshEconomy();
  }

  private cancelUnlock(): void {
    if (this.purchasing) { return; }
    this.unlockIndex = -1;
    this.purchaseError = '';
  }

  private async confirmUnlock(): Promise<void> {
    if (!this.loaded || this.unlockIndex < 0 || this.unlockIndex >= GAMES.length || this.purchasing) { return; }
    const index = this.unlockIndex;
    this.purchasing = true;
    this.purchaseError = '';
    const result = await this.store.purchase(GAMES[index].id);
    this.refreshEconomy();
    this.purchasing = false;
    if (result === 'purchased' || result === 'owned') {
      this.unlockIndex = -1;
    } else if (result === 'error') {
      this.purchaseError = this.store.error || '解锁保存失败，请重试';
    } else if (result === 'invalid') {
      this.purchaseError = '无法解锁此游戏';
    }
  }

  // Free training entry (training tab, growth practice, result recommendation).
  private selectGame(index: number): void {
    if (!this.loaded || this.purchasing || this.unlockIndex >= 0 || index < 0 || index >= GAMES.length || !Number.isInteger(index)) { return; }
    if (!this.store.isUnlocked(GAMES[index].id)) {
      this.unlockIndex = index;
      this.purchaseError = '';
      return;
    }
    this.stageIndex = -1;
    this.enterGame(index);
  }

  private openStage(index: number): void {
    if (!this.loaded || this.purchasing || this.unlockIndex >= 0 || !validStage(index) || !this.store.stageOpen(index)) { return; }
    this.stageDetail = index;
  }

  // An open stage implies its game is discovered, so no purchase is involved.
  private playStage(index: number): void {
    if (!this.loaded || this.purchasing || this.unlockIndex >= 0 || !validStage(index) || !this.store.stageOpen(index)) { return; }
    const gameIndex = GAMES.findIndex((game: GameInfo) => game.id === STAGES[index].gameId);
    if (gameIndex < 0) { return; }
    this.stageDetail = -1;
    this.stageIndex = index;
    this.tab = 'map';
    this.enterGame(gameIndex);
  }

  private stage(): StageInfo { return STAGES[validStage(this.stageIndex) ? this.stageIndex : 0]; }

  private stageMode(): boolean { return validStage(this.stageIndex); }

  private stageProgress(): number {
    return stageScore(this.game().id, this.level, this.score, this.puzzle.answers.length);
  }

  private stageGoalReached(): boolean {
    return this.stageMode() && this.screen === 'game' && this.stageProgress() >= this.stage().stars[2];
  }

  // Runs once per stage session, after the record was saved.
  private settleStage(): void {
    if (!this.stageMode() || this.stageSettled) { return; }
    this.stageSettled = true;
    const index = this.stageIndex;
    const previous = this.store.stageStars(index);
    this.stageValue = this.stageProgress();
    this.stageEarned = starsFor(this.stage(), this.stageValue);
    this.stageCoins = this.store.completeStage(index, this.stageEarned);
    this.stageBest = Math.max(previous, this.stageEarned);
    const next = index + 1;
    if (previous === 0 && this.stageEarned > 0 && validStage(next) && STAGES[next].intro) {
      this.pendingDiscovery = GAMES.findIndex((game: GameInfo) => game.id === STAGES[next].gameId);
    }
    this.refreshEconomy();
  }

  private hasNextStage(): boolean { return validStage(this.stageIndex + 1); }

  private tabScreen(): boolean { return TAB_SCREENS.indexOf(this.screen) >= 0; }

  // In-game badge, e.g. "1 星 · 6/9 级": progress toward the next unmet star.
  private stageGoalText(): string {
    const value = this.stageProgress();
    const stars = starsFor(this.stage(), value);
    const target = this.stage().stars[Math.min(2, stars)];
    return `${stars} 星 · ${Math.min(value, target)}/${target} ${stageUnit(this.game().id)}`;
  }

  // Leaves a result or tutorial: stage sessions go back to the map.
  private leaveSession(): void {
    if (this.stageMode()) { this.toMap(false); }
    else { this.screen = this.tab; }
  }

  private toMap(openNext: boolean): void {
    const next = this.stageIndex + 1;
    this.screen = 'map'; this.tab = 'map';
    const target = openNext && validStage(next) && this.store.stageOpen(next) ? next : -1;
    if (this.pendingDiscovery >= 0) {
      this.discoveryIndex = this.pendingDiscovery;
      this.pendingDiscovery = -1;
      this.pendingStage = target;
    } else if (target >= 0) {
      this.stageDetail = target;
    }
  }

  private closeDiscovery(): void {
    this.discoveryIndex = -1;
    if (this.pendingStage >= 0) { this.stageDetail = this.pendingStage; }
    this.pendingStage = -1;
  }

  private enterGame(index: number): void {
    this.gameIndex = index;
    this.tutorialPage = 0;
    this.tutorialFromPause = false;
    if (!this.store.record(this.game().id).tutorialSeen) { this.screen = 'tutorial'; }
    else { this.start(); }
  }

  private start(): void {
    if (!this.loaded || this.purchasing || this.unlockIndex >= 0) { return; }
    if (!this.store.isUnlocked(this.game().id)) {
      this.unlockIndex = this.gameIndex;
      this.purchaseError = '';
      return;
    }
    this.store.seenTutorial(this.game().id);
    // Stage sessions always climb from level 1 so star targets mean the same for everyone.
    this.level = this.stageMode() ? 1 : this.store.startingLevel(this.game().id, this.game().adaptive);
    this.score = 0; this.lives = 2; this.remaining = 0;
    this.savedSession = false;
    this.stageSettled = false;
    this.stageEarned = 0; this.stageValue = 0; this.stageCoins = 0;
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
        if (!this.feedbackCorrect && this.lives <= 0) {
          // A stage is judged by what was reached; there is nothing to restart into.
          if (this.stageMode()) { this.finish('本局结束'); }
          else { this.screen = 'failure'; }
        }
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
    if (success && this.stageGoalReached()) {
      this.store.reached(this.game().id, this.level);
      this.finish('满星通关');
      return;
    }
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
    const previousScore = this.score;
    const previousAnswers = this.puzzle.answers.length;
    const completedLevel = this.level;
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
      if (this.game().id === 17 && outcome.points > 0) {
        this.reward(scoreCoins248(this.score) - scoreCoins248(previousScore));
        if (this.stageGoalReached()) { this.finish('满星通关'); return; }
      }
      if (this.game().id === 23) {
        if (this.puzzle.answers.length > previousAnswers) { this.reward(levelCoins(23, completedLevel)); }
        this.level = Math.min(48, this.puzzle.answers.length + 1);
        this.store.reached(this.game().id, this.level);
        if (!outcome.complete && this.stageGoalReached()) { this.finish('满星通关'); return; }
      }
      if (outcome.complete) {
        if (this.game().id === 23) { this.finish('48 个数字全部找到，挑战成功'); }
        else {
          this.reward(levelCoins(this.game().id, completedLevel));
          this.feedback(true, 0);
        }
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
    this.settleStage();
    this.screen = 'result'; this.paused = false;
    const failed = this.stageMode() ? this.stageEarned === 0 : this.lives <= 0;
    this.sound.play(failed ? 'error' : 'win');
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
  private HeaderButton(symbol: Resource, label: string, action: () => void) {
    Button({ type: ButtonType.Circle }) {
      SymbolGlyph(symbol).fontSize(20).fontColor([GAME_INK])
    }.width(40).height(40).backgroundColor('#CCFFFFFF')
      .onClick(action).accessibilityText(label)
  }

  @Builder
  private Header() {
    Stack({ alignContent: Alignment.Center }) {
      Text(this.game().name).fontSize(17).fontWeight(FontWeight.Medium).fontColor(GAME_INK)
        .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).constraintSize({ maxWidth: '50%' })
      Row({ space: 8 }) {
        this.HeaderButton($r('sys.symbol.pause'), '暂停', () => this.pause())
        this.HeaderButton(this.soundOn ? $r('sys.symbol.speaker_wave_2') : $r('sys.symbol.speaker_slash'),
          this.soundOn ? '关闭声音' : '开启声音', () => this.toggleSound())
        Blank()
        if (this.showsLives()) {
          Row({ space: 4 }) {
            Image(media('ic_life')).width(22).height(22).opacity(this.lives >= 2 ? 1 : 0.25)
            Image(media('ic_life')).width(22).height(22).opacity(this.lives >= 1 ? 1 : 0.25)
          }.height(40).padding({ left: 10, right: 10 }).borderRadius(20).backgroundColor('#CCFFFFFF')
            .accessibilityGroup(true).accessibilityText(`剩余机会 ${Math.max(0, this.lives)}`)
        }
      }.width('100%').height(56).padding({ left: 12, right: 12 })
    }.width('100%').height(56)
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
      Row({ space: 8 }) {
        Text(this.game().id === 17 ? `得分 ${this.score}` : `第 ${this.level} 级`).fontSize(13).fontWeight(FontWeight.Medium)
          .fontColor('#0277BD').height(24).padding({ left: 12, right: 12 }).borderRadius(12).backgroundColor('#DDEFFA')
        if (this.stageMode()) {
          Row({ space: 4 }) {
            SymbolGlyph($r('sys.symbol.star_fill')).fontSize(13).fontColor(['#FFB300'])
            Text(this.stageGoalText()).fontSize(13).fontWeight(FontWeight.Medium).fontColor('#8D5A00')
          }.height(24).padding({ left: 10, right: 12 }).borderRadius(12).backgroundColor('#FFF1CC')
            .accessibilityGroup(true).accessibilityText(this.stageGoalText())
        }
      }.width('100%').justifyContent(FlexAlign.Center).height(this.game().id === 17 ? 38 : 30)
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
            Text('准备！').fontSize(32).fontWeight(FontWeight.Medium).fontColor('#0288D1').margin({ top: 8 })
          }.width('100%').height('100%').justifyContent(FlexAlign.Center).backgroundColor('#AAE9F5FB')
        }
        if (this.screen === 'result') { Row().width('100%').height('100%').backgroundColor('#E5F6FE') }
      }.width('100%').layoutWeight(1)
        .onAreaChange((_old: Area, area: Area) => this.boardHeight = Number.parseFloat(area.height.toString()))
    }.width('100%').height('100%').backgroundColor(this.game().id === 21 ? Color.Transparent : this.gameBackground())
    }.width('100%').height('100%')
  }

  private shellScreen(): boolean { return this.tabScreen() || this.screen === 'tutorial'; }

  @Builder
  private TabItem(target: string, symbol: Resource, label: string) {
    Column({ space: 2 }) {
      SymbolGlyph(symbol).fontSize(24)
        .fontColor([this.screen === target ? $r('app.color.brand') : $r('app.color.text_tertiary')])
      Text(label).fontSize(11).fontWeight(this.screen === target ? FontWeight.Medium : FontWeight.Normal)
        .fontColor(this.screen === target ? $r('app.color.brand') : $r('app.color.text_tertiary'))
    }.width('100%').height('100%').justifyContent(FlexAlign.Center)
    .accessibilityText(label).accessibilitySelected(this.screen === target)
  }

  private brainStats(): GameStat[] {
    return GAMES.map((game: GameInfo) => {
      const record = this.store.record(game.id);
      const stat = new GameStat();
      stat.id = game.id; stat.highest = record.highest; stat.plays = record.plays;
      stat.sessions = record.sessions.slice();
      return stat;
    });
  }

  private syncSystemBar(): void {
    const ink: string = this.darkMode && this.shellScreen() ? '#FFFFFF' : '#000000';
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    window.getLastWindow(context)
      .then((win: window.Window) => win.setWindowSystemBarProperties({ statusBarContentColor: ink, navigationBarContentColor: ink }))
      .catch(() => {});
  }

  build() {
    Stack() {
      Column().width('100%').height('100%')
        .backgroundColor(this.shellScreen() ? $r('app.color.page_background') : this.gameBackground())
        .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
      Column() {
        if (this.storageError.length > 0) {
          Column() {
            Row({ space: 8 }) {
              SymbolGlyph($r('sys.symbol.exclamationmark_circle')).fontSize(18).fontColor([$r('app.color.danger')])
              Text(this.storageError).fontSize(13).fontColor($r('app.color.danger')).layoutWeight(1)
              Button(this.retryingStorage ? '正在重试' : '重试', { type: ButtonType.Capsule })
                .height(30).fontSize(13).fontColor($r('app.color.on_brand')).backgroundColor($r('app.color.brand'))
                .onClick(() => this.retryStorage()).enabled(!this.retryingStorage && !this.purchasing)
            }.width('100%').padding({ left: 12, right: 8, top: 8, bottom: 8 })
              .borderRadius(16).backgroundColor($r('app.color.danger_soft'))
          }.width('100%').padding({ left: 12, right: 12, top: 8 })
        }
        Column() {
          if (this.tabScreen()) {
            Tabs({ barPosition: BarPosition.End, index: TAB_SCREENS.indexOf(this.screen) }) {
              TabContent() {
                StageMap({ stars: this.stageStars, coins: this.coins, current: this.currentStage,
                  onOpen: (index: number) => this.openStage(index) })
              }.tabBar(this.TabItem('map', $r('sys.symbol.flag'), '闯关'))
              TabContent() {
                AndroidHome({ records: this.highestLevels(), lastGame: this.lastGame, coins: this.coins,
                  unlockedIds: this.unlockedIds, onSelect: (index: number) => this.selectGame(index) })
              }.tabBar(this.TabItem('training', $r('sys.symbol.square_grid_2x2'), '训练'))
              TabContent() {
                AbilityGrowth({ stats: this.brainStats(), unlockedIds: this.unlockedIds, darkMode: this.darkMode,
                  active: this.screen === 'growth', onPractice: (index: number) => this.selectGame(index) })
              }.tabBar(this.TabItem('growth', $r('sys.symbol.star'), '成长'))
            }.width('100%').height('100%').barHeight(56).barMode(BarMode.Fixed).scrollable(false)
              .animationDuration(0).barBackgroundColor($r('app.color.card_background'))
              .divider({ strokeWidth: 0.5, color: $r('app.color.divider') })
              .onChange((index: number) => {
                this.screen = TAB_SCREENS[Math.max(0, Math.min(TAB_SCREENS.length - 1, index))];
                this.tab = this.screen;
              })
          } else if (this.screen === 'tutorial') {
            AndroidTutorial({ gameIndex: this.gameIndex, fromPause: this.tutorialFromPause,
              onExit: () => { this.screen = this.tutorialFromPause ? 'game' : this.tab; },
              onPlay: () => this.start() })
          } else {
            Stack() {
              this.GameSurface()
              if (this.paused && this.screen === 'game') {
                AndroidPause({ homeLabel: this.stageMode() ? '返回地图' : '游戏列表', onResume: () => this.resume(),
                  onRestart: () => { this.saveSession(); this.start(); }, onTutorial: () => this.openTutorial(),
                  onHome: () => { this.saveSession(); this.paused = false; this.screen = this.tab; },
                  onEnd: () => this.finish('训练记录已保存') })
              }
              if (this.screen === 'result' && this.stageMode()) {
                StageResult({ stageIndex: this.stageIndex, stars: this.stageEarned, best: this.stageBest,
                  value: this.stageValue, coins: this.stageCoins, hasNext: this.hasNextStage(),
                  onNext: () => this.toMap(true), onRetry: () => this.start(), onMap: () => this.toMap(false) })
              } else if (this.screen === 'result') {
                AndroidResult({ gameIndex: this.gameIndex, level: this.level, best: this.store.record(this.game().id).highest,
                  title: this.resultTitle, onReplay: () => this.start(), onHome: () => { this.screen = this.tab; },
                  onRecommend: (index: number) => this.selectGame(index) })
              }
              if (this.screen === 'failure') {
                AndroidFailure({ gameIndex: this.gameIndex, level: this.level,
                  onRestart: () => { this.saveSession(); this.start(); }, onEnd: () => this.finish('本局结束') })
              }
            }.width('100%').height('100%')
          }
        }.width('100%').layoutWeight(1)
      }.width('100%').height('100%')
      if (this.stageDetail >= 0 && this.screen === 'map') {
        StageDetail({ stageIndex: this.stageDetail, best: this.store.stageStars(this.stageDetail),
          onStart: () => this.playStage(this.stageDetail), onCancel: () => { this.stageDetail = -1; } })
      }
      if (this.discoveryIndex >= 0 && this.screen === 'map') {
        AndroidDiscovery({ gameIndex: this.discoveryIndex, onContinue: () => this.closeDiscovery() })
      }
      if (this.unlockIndex >= 0) {
        AndroidUnlock({ gameIndex: this.unlockIndex, balance: this.coins, busy: this.purchasing,
          errorText: this.purchaseError, onConfirm: () => this.confirmUnlock(), onCancel: () => this.cancelUnlock() })
      }
    }.width('100%').height('100%')
    .onAppear(() => this.syncSystemBar())
  }
}
