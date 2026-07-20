import { AppPreference } from '../data/AppPreference';
import { ThemeStyle } from '../entity/ThemeStyle';

export class ThemePromptService {
  static readonly CURRENT_PROMPT_VERSION: string = '2.0'

  static shouldShow(): boolean {
    return AppPreference.getInstance().getThemePromptVersion() !== ThemePromptService.CURRENT_PROMPT_VERSION
  }

  static complete(style: ThemeStyle): void {
    const preference = AppPreference.getInstance()
    preference.setThemeStyle(style)
    preference.setThemePromptVersion(ThemePromptService.CURRENT_PROMPT_VERSION)
  }
}
