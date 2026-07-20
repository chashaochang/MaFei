import { ThemeStyle } from '../entity/ThemeStyle';

export class AppThemeTokens {
  static isNative(style: ThemeStyle): boolean {
    return style === ThemeStyle.Native
  }

  static groupRadius(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 20 : 8
  }

  static groupSpacing(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 14 : 8
  }

  static listRowHeight(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 64 : 52
  }

  static iconWellSize(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 38 : 18
  }

  static iconWellRadius(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 12 : 0
  }

  static profileRadius(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 20 : 8
  }

  static navigationRadius(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 24 : 28
  }

  static navigationItemRadius(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 18 : 24
  }

  static mediaRadius(style: ThemeStyle): number {
    return AppThemeTokens.isNative(style) ? 10 : 6
  }

  static showMineBackground(style: ThemeStyle): boolean {
    return style === ThemeStyle.Feiniu
  }

  static showIconWell(style: ThemeStyle): boolean {
    return AppThemeTokens.isNative(style)
  }

  static useActionButtonSurface(style: ThemeStyle): boolean {
    return AppThemeTokens.isNative(style)
  }

  static useMediaShadow(style: ThemeStyle): boolean {
    return AppThemeTokens.isNative(style)
  }
}
