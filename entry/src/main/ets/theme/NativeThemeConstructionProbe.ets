export class NativeThemeConstructionProbe {
  private static hdsNavigationConstructionCount: number = 0
  private static hdsEffectConstructionCount: number = 0

  static markHdsNavigationConstruction(): void {
    NativeThemeConstructionProbe.hdsNavigationConstructionCount += 1
  }

  static markHdsEffectConstruction(): void {
    NativeThemeConstructionProbe.hdsEffectConstructionCount += 1
  }

  static label(deviceWidth: number, breakpoint: string, shell: string): string {
    return 'mafei-shell-probe width=' + deviceWidth.toString() +
      ' breakpoint=' + breakpoint +
      ' shell=' + shell +
      ' hdsNavigation=' +
      NativeThemeConstructionProbe.hdsNavigationConstructionCount.toString() +
      ' hdsEffect=' +
      NativeThemeConstructionProbe.hdsEffectConstructionCount.toString()
  }
}
