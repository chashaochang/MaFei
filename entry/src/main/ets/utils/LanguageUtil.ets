import { by639_2T } from "iso_language_codes";

const languageNames = new Intl.DisplayNames(['zh-CN'], { type: 'language' });

export class LanguageUtil {
  static getLanguageDisplayNameBy6392T(lang: string): string {
    const normalized = lang.trim().toLowerCase()
    if (normalized.length === 0 || normalized === 'und' || normalized === 'unknown' || normalized === 'zxx' ||
      normalized === 'qaa-qtz' || normalized === 'zz-unknow' || normalized === '_no_display_') return '未知'

    const displayCode = LanguageUtil.displayCode(normalized)
    return displayCode.length > 0 ? (languageNames.of(displayCode) || '未知') : '未知'
  }

  private static displayCode(language: string): string {
    if (language === 'chs') return 'zh-Hans'
    if (language === 'cht') return 'zh-Hant'
    if (language === 'chi' || language === 'zho') return 'zh'
    if (language === 'jp' || language === 'jpn') return 'ja'
    if (language === 'kor') return 'ko'
    if (language === 'eng') return 'en'
    if (language.length === 2 || language.indexOf('-') > 0) return language
    return by639_2T[language]?.iso639_1 || ''
  }
}
