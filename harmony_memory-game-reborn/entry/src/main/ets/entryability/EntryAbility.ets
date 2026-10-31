import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';

function isDark(colorMode: ConfigurationConstant.ColorMode | undefined): boolean {
  return colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
}

export default class EntryAbility extends UIAbility {
  onCreate(_want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    AppStorage.setOrCreate('darkMode', isDark(this.context.config.colorMode));
  }

  onConfigurationUpdate(config: Configuration): void {
    AppStorage.setOrCreate('darkMode', isDark(config.colorMode));
  }

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
