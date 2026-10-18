import { UIAbility } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';

export default class EntryAbility extends UIAbility {
  onWindowStageCreate(windowStage: window.WindowStage): void {
    AppStorage.setOrCreate('appActive', true);
    windowStage.loadContent('pages/Index', (error) => {
      if (error.code) { console.error(`Unable to load game page: ${error.code}`); }
    });
  }

  onForeground(): void {
    AppStorage.setOrCreate('appActive', true);
  }

  onBackground(): void {
    AppStorage.setOrCreate('appActive', false);
  }
}
