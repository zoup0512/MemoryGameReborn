import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { requiresUnlock, unlockCost } from '../core/Economy';

export class GameRecord {
  id: number = 0;
  highest: number = 1;
  bestScore: number = 0;
  sessions: number[] = [];
  tutorialSeen: boolean = false;
  plays: number = 0;
}

class SaveData {
  version: number = 2;
  sound: boolean = true;
  records: GameRecord[] = [];
  pendingId: number = 0;
  pendingLevel: number = 1;
  pendingScore: number = 0;
  coins: number = 60;
  unlocked: number[] = [];
}

function validId(id: number): boolean {
  return Number.isInteger(id) && id >= 1 && id <= 23;
}

function nonnegative(value: number): boolean {
  return Number.isSafeInteger(value) && value >= 0;
}

// A malformed v2 save must not silently become a fresh wallet.
function validSave(saved: SaveData): boolean {
  if (saved === null || saved.version !== 2 || typeof saved.sound !== 'boolean' ||
    !nonnegative(saved.coins) || !Array.isArray(saved.unlocked) ||
    !Array.isArray(saved.records) || !nonnegative(saved.pendingId) || saved.pendingId > 23 ||
    !nonnegative(saved.pendingScore) || !Number.isSafeInteger(saved.pendingLevel) ||
    saved.pendingLevel < 1) { return false; }
  const owned: number[] = [];
  for (const id of saved.unlocked) {
    if (!requiresUnlock(id) || owned.indexOf(id) >= 0) { return false; }
    owned.push(id);
  }
  const ids: number[] = [];
  for (const record of saved.records) {
    if (record === null || !validId(record.id) || ids.indexOf(record.id) >= 0 ||
      !Number.isSafeInteger(record.highest) || record.highest < 1 ||
      !nonnegative(record.bestScore) || !nonnegative(record.plays) ||
      typeof record.tutorialSeen !== 'boolean' || !Array.isArray(record.sessions) ||
      record.sessions.length > 7) { return false; }
    for (const level of record.sessions) {
      if (!Number.isSafeInteger(level) || level < 1) { return false; }
    }
    ids.push(record.id);
  }
  return true;
}

// Preferences instances share their namespace cache. If rollback itself fails,
// no Store in this process may trust that cache until the owner repairs it.
let recoveryOwner: ProgressStore | null = null;
const RECOVERY_ERROR: string = '购买保存恢复失败，金币和解锁暂不可用，请在原页面重试保存';

export class ProgressStore {
  private database: preferences.Preferences | null = null;
  private data: SaveData = new SaveData();
  private writes: Promise<void> = Promise.resolve();
  private loading: Promise<void> | null = null;
  private loaded: boolean = false;
  error: string = '';

  load(context: common.UIAbilityContext): Promise<void> {
    if (this.loading !== null) { return this.loading; }
    if (this.loaded && recoveryOwner === null) { return Promise.resolve(); }
    this.loading = this.read(context);
    return this.loading;
  }

  private async read(context: common.UIAbilityContext): Promise<void> {
    try {
      if (recoveryOwner !== null) {
        if (!await recoveryOwner.saveQueued()) {
          this.error = RECOVERY_ERROR;
          return;
        }
        if (this.loaded) { this.error = ''; return; }
      }
      this.database = await preferences.getPreferences(context, { name: 'memory_training_v2' });
      const raw = await this.database.get('progress', '') as string;
      if (typeof raw !== 'string') { throw new Error('Invalid progress value'); }
      const saved: SaveData = raw.length === 0 ? new SaveData() : JSON.parse(raw) as SaveData;
      if (!validSave(saved)) { throw new Error('Invalid progress data'); }
      this.data = saved;
      this.loaded = true;
      this.error = '';
      if (this.data.pendingId > 0) {
        // Recover only the record, never a reward. finish clears pending before saving.
        this.finish(this.data.pendingId, this.data.pendingLevel, this.data.pendingScore);
      } else if (raw.length === 0) {
        // Persist the initial 60 even if the user never starts a game.
        this.flush();
      }
      await this.settled();
    } catch (_) {
      this.loaded = false;
      this.database = null;
      this.error = '本地记录读取失败，金币和解锁暂不可用，可重试';
    } finally {
      this.loading = null;
    }
  }

  ready(): boolean { return this.loaded && recoveryOwner === null; }

  coins(): number { return this.data.coins; }

  isUnlocked(id: number): boolean {
    return validId(id) && (!requiresUnlock(id) || this.data.unlocked.indexOf(id) >= 0);
  }

  unlockedGames(): number[] {
    const result: number[] = [];
    for (let id = 1; id <= 23; id++) {
      if (this.isUnlocked(id)) { result.push(id); }
    }
    return result;
  }

  // Credit synchronously so gameplay can display the reward immediately. A failed
  // save retains the credit in memory; retry flush, NOT earnCoins, to persist it.
  earnCoins(amount: number): Promise<boolean> {
    if (!this.ready() || !nonnegative(amount) || amount === 0 ||
      !Number.isSafeInteger(this.data.coins + amount)) { return Promise.resolve(false); }
    this.data.coins += amount;
    return this.saveQueued();
  }

  purchase(id: number): Promise<string> {
    if (!validId(id)) { return Promise.resolve('invalid'); }
    if (!this.ready()) { return Promise.resolve('error'); }
    const result: Promise<string> = this.writes.then(async (): Promise<string> => {
      if (!this.ready()) { return 'error'; }
      if (this.isUnlocked(id)) { return 'owned'; }
      const cost = unlockCost(id);
      if (this.data.coins < cost) { return 'insufficient'; }
      const candidate = JSON.parse(JSON.stringify(this.data)) as SaveData;
      candidate.coins -= cost;
      candidate.unlocked.push(id);
      if (!await this.persist(candidate)) {
        // put() may already have changed the shared Preferences cache even when
        // flush() failed. Restore the current, uncharged data before releasing
        // the queue, including any rewards/records received during the await.
        recoveryOwner = this;
        if (await this.persist(this.data)) {
          this.error = '购买保存失败，未扣款或解锁，请重试';
        }
        return 'error';
      }
      // Do not replace the live snapshot: synchronous rewards/record updates may
      // have arrived while put/flush awaited. They have their own queued saves.
      this.data.coins -= cost;
      this.data.unlocked.push(id);
      return 'purchased';
    });
    this.writes = result.then((): void => {});
    return result;
  }

  record(id: number): GameRecord {
    let record = this.data.records.find((item: GameRecord) => item.id === id);
    if (record === undefined) {
      record = new GameRecord(); record.id = id;
      this.data.records.push(record);
    }
    return record;
  }

  startingLevel(id: number, adaptive: boolean): number {
    if (!adaptive) { return 1; }
    const recent = this.record(id).sessions.slice(-7);
    if (recent.length === 0) { return 1; }
    const average = recent.reduce((sum: number, level: number) => sum + level, 0) / recent.length;
    return average > 6 ? Math.max(1, Math.round(average) - 5) : 1;
  }

  reached(id: number, level: number): void {
    const record = this.record(id);
    if (level > record.highest) {
      record.highest = level;
      this.flush();
    }
  }

  checkpoint(id: number, level: number, score: number): void {
    this.data.pendingId = id;
    this.data.pendingLevel = level;
    this.data.pendingScore = score;
    const record = this.record(id);
    record.highest = Math.max(record.highest, level);
    record.bestScore = Math.max(record.bestScore, score);
    this.flush();
  }

  finish(id: number, level: number, score: number): void {
    this.data.pendingId = 0;
    const record = this.record(id);
    record.highest = Math.max(record.highest, level);
    record.bestScore = Math.max(record.bestScore, score);
    record.sessions = record.sessions.slice(-6);
    record.sessions.push(level);
    record.plays++;
    this.flush();
  }

  seenTutorial(id: number): void {
    this.record(id).tutorialSeen = true;
    this.flush();
  }

  soundEnabled(): boolean { return this.data.sound; }

  setSound(enabled: boolean): void {
    this.data.sound = enabled;
    this.flush();
  }

  private async persist(data: SaveData): Promise<boolean> {
    if (recoveryOwner !== null && recoveryOwner !== this) {
      this.error = RECOVERY_ERROR;
      return false;
    }
    if (!this.loaded || this.database === null) { return false; }
    try {
      await this.database.put('progress', JSON.stringify(data));
      await this.database.flush();
      if (recoveryOwner === this) { recoveryOwner = null; }
      this.error = '';
      return true;
    } catch (_) {
      this.error = recoveryOwner === this ? RECOVERY_ERROR : '本地记录保存失败，请重试';
      return false;
    }
  }

  private saveQueued(): Promise<boolean> {
    if (!this.loaded) { return Promise.resolve(false); }
    // Snapshot only when this operation reaches the queue head. Capturing at
    // flush() call time could overwrite an earlier, still-pending purchase.
    const result: Promise<boolean> = this.writes.then((): Promise<boolean> => this.persist(this.data));
    this.writes = result.then((): void => {});
    return result;
  }

  flush(): void { this.saveQueued(); }

  async settled(): Promise<void> { await this.writes; }
}
