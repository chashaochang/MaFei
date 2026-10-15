import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../theme/AppThemeSurfaceResolver'

@ComponentV2
export struct EmptyView {
  @Param title: string = '暂无数据'
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  build() {
    if (this.useNativeSurface()) {
      Column({ space: 16 }) {
        Stack() {
          SymbolGlyph($r('sys.symbol.xmark_video'))
            .fontSize(38)
            .fontColor([$r('sys.color.font_secondary')])
        }
        .width(88)
        .height(88)
        .borderRadius(24)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.Floating,
          true,
          this.appUIState.systemMaterialAvailable
        ))
        Text(this.title)
          .fontSize(14)
          .fontColor($r('sys.color.font_secondary'))
      }
      .width('100%')
      .height('100%')
      .padding({ bottom: 100 })
      .justifyContent(FlexAlign.Center)
    } else {
      Stack() {
        Image($r('app.media.empty'))
          .width(240)
          .height(240)
        Text(this.title)
          .fontSize(14)
          .fontColor($r('app.color.text_2'))
          .margin({ top: 180 })
      }
      .width('100%')
      .height('100%')
      .padding({ bottom: 100 })
    }
  }
}
