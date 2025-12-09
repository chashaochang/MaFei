import { by639_2T } from "iso_language_codes";

const languageNames = new Intl.DisplayNames(['zh-CN'], { type: 'language' });

export class LanguageUtil {
  static getLanguageDisplayNameBy6392T(lang: string): string {
    let iso639_1_name: string | undefined = by639_2T[lang]?.iso639_1
    let language: string = (iso639_1_name ? languageNames.of(iso639_1_name) : '未知') || '未知'
    return language
  }
}