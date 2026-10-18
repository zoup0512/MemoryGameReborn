import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';

export class GameRecord {
  id: number = 0;
  highest: number = 1;
  bestScore: number = 0;
  sessions: number[] = [];
  tutorialSeen: boolean = false;
  plays: number = 0;
}

class SaveData {
  version: number = 1;
  sound: boolean = true;
  records: GameRecord[] = [];
  pendingId: number = 0;
  pendingLevel: number = 1;
  pendingScore: number = 0;
}

export class ProgressStore {
  private database: preferences.Preferences | null = null;
  private data: SaveData = new SaveData();
  private writes: Promise<void> = Promise.resolve();
  error: string = '';

  async load(context: common.UIAbilityContext): Promise<void> {
    try {
      this.database = await preferences.getPreferences(context, { name: 'memory_training_v1' });
      const raw = await this.database.get('progress', '') as string;
      if (raw.length > 0) {
        const saved = JSON.parse(raw) as SaveData;
        if (saved.version === 1 && Array.isArray(saved.records)) {
          this.data = saved;
          if (this.data.pendingId > 0) {
            this.finish(this.data.pendingId, this.data.pendingLevel, this.data.pendingScore);
          }
        }
      }
    } catch (_) {
      this.error = '本地记录读取失败，本次仍可训练';
    }
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

  flush(): void {
    const snapshot = JSON.stringify(this.data);
    this.writes = this.writes.then(async () => {
      if (this.database === null) { return; }
      try {
        await this.database.put('progress', snapshot);
        await this.database.flush();
      } catch (_) { this.error = '本地记录保存失败'; }
    });
  }

  async settled(): Promise<void> { await this.writes; }
}
