import { media } from '@kit.MediaKit';
import { common } from '@kit.AbilityKit';

export class SoundService {
  private players: (media.AVPlayer | null)[] = [null, null, null, null];
  private names: string[] = ['tap', 'win', 'wrong', 'error'];
  private files: string[] = ['tap.mp3', 'win.mp3', 'wrong.mp3', 'error.mp3'];
  private opened: string[] = [];
  private context: common.UIAbilityContext | null = null;
  private disposed: boolean = false;
  private suppressed: boolean = true;
  enabled: boolean = true;

  async load(context: common.UIAbilityContext): Promise<void> {
    this.context = context;
    for (let index = 0; index < this.names.length; index++) {
      if (this.disposed) { return; }
      try {
        const player = await media.createAVPlayer();
        if (this.disposed) { await player.release(); return; }
        this.players[index] = player;
        player.on('stateChange', async (state: string) => {
          try {
            if (state === 'initialized' && !this.disposed) { await player.prepare(); }
            if (state === 'playing' && (this.disposed || this.suppressed || !this.enabled)) { await player.pause(); }
          } catch (_) { }
        });
        player.on('error', () => {});
        const descriptor = await context.resourceManager.getRawFd(this.files[index]);
        if (this.disposed) { await context.resourceManager.closeRawFd(this.files[index]); return; }
        this.opened.push(this.files[index]);
        player.fdSrc = { fd: descriptor.fd, offset: descriptor.offset, length: descriptor.length };
      } catch (_) { }
    }
  }

  async play(name: string): Promise<void> {
    if (!this.enabled || this.disposed) { return; }
    this.suppressed = false;
    const index = this.names.indexOf(name);
    if (index < 0 || index >= this.players.length) { return; }
    const player = this.players[index];
    if (player === null) { return; }
    try {
      if (player.state === 'completed' || player.state === 'paused' || player.state === 'playing') { player.seek(0); }
      if (player.state === 'prepared' || player.state === 'completed' || player.state === 'paused') { await player.play(); }
    } catch (_) { }
  }

  async silence(): Promise<void> {
    this.suppressed = true;
    for (const player of this.players) {
      try { if (player !== null && player.state === 'playing') { await player.pause(); } } catch (_) { }
    }
  }

  async release(): Promise<void> {
    this.disposed = true;
    for (const player of this.players) { try { if (player !== null) { await player.release(); } } catch (_) { } }
    if (this.context !== null) {
      for (const name of this.opened) {
        try { await this.context.resourceManager.closeRawFd(name); } catch (_) { }
      }
    }
    this.players = [null, null, null, null];
    this.opened = [];
  }
}
