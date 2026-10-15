import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import {
  MediaInfoCardViewData,
  MediaInfoRowViewData,
  MediaInfoSectionViewData
} from '../../../media/presentation/VideoDetailPresentationModels'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'

@ComponentV2
export struct VideoInfoDetail {
  @Require @Param sections: MediaInfoSectionViewData[]
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private groupBackground(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      Color.Transparent : $r('app.color.bg_2')
  }

  private headingTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      $r('sys.color.font_primary') : $r('app.color.text_1')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
      $r('sys.color.font_secondary') : $r('app.color.text_2')
  }

  build() {
    Column({ space: 16 }) {
      ForEach(this.sections, (section: MediaInfoSectionViewData) => {
        Column({ space: 8 }) {
          Text(section.title)
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.headingTextColor())
          ForEach(section.cards, (card: MediaInfoCardViewData) => {
            Column({ space: 16 }) {
              Text(card.title)
                .fontSize(13)
                .fontWeight(FontWeight.Medium)
                .fontColor(this.primaryTextColor())
              ForEach(card.rows, (row: MediaInfoRowViewData) => {
                if (row.dividerBefore) {
                  Divider()
                }
                Row() {
                  Text(row.title)
                    .fontSize(12)
                    .fontColor(this.secondaryTextColor())
                  Blank().layoutWeight(1)
                  Text(row.value)
                    .fontSize(12)
                    .fontColor(this.secondaryTextColor())
                    .textAlign(TextAlign.End)
                }
                .width('100%')
              }, (row: MediaInfoRowViewData): string => `${row.title}:${row.value}`)
            }
            .alignItems(HorizontalAlign.Start)
            .width('100%')
            .backgroundColor(this.groupBackground())
            .borderRadius(12)
            .padding(12)
          }, (card: MediaInfoCardViewData): string => card.id)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
      }, (section: MediaInfoSectionViewData): string => section.id)
    }
    .width('100%')
    .constraintSize({ maxWidth: 720 })
    .alignSelf(ItemAlign.Center)
    .alignItems(HorizontalAlign.Start)
  }
}
