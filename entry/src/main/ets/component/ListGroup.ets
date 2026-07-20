import { AppStorageV2 } from '@kit.ArkUI';
import { AppUIState } from '../entity/AppUIState';
import { AppThemeTokens } from '../theme/AppThemeTokens';

@ComponentV2
export struct ListGroup {
  @Param items: Array<BaseGroupItem> = []
  @Param onItemClick: (index: number) => void = (_index: number) => {
  }
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  @Styles
  pressedStyles(): void {
    .backgroundColor($r('app.color.bg_hover'))
  }

  @Styles
  normalStyles(): void {
    .backgroundColor(Color.Transparent)
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.appUIState.themeStyle) }) {
      Column() {
        ForEach(this.items, (item: BaseGroupItem, index) => {
          Row({ space: 10 }) {
            Stack() {
              Image(item.icon)
                .width(18)
                .height(18)
                .fillColor(AppThemeTokens.showIconWell(this.appUIState.themeStyle) ?
                  $r('app.color.color_main') : $r('app.color.text_primary'))
            }
            .width(item.icon != '' ? AppThemeTokens.iconWellSize(this.appUIState.themeStyle) : 0)
            .height(item.icon != '' ? AppThemeTokens.iconWellSize(this.appUIState.themeStyle) : 0)
            .borderRadius(AppThemeTokens.iconWellRadius(this.appUIState.themeStyle))
            .backgroundColor(AppThemeTokens.showIconWell(this.appUIState.themeStyle) ?
              $r('app.color.primary_light_hover') : Color.Transparent)
            .visibility(item.icon != '' ? Visibility.Visible : Visibility.None)
            Column({ space: 3 }) {
              Text(item.name)
                .fontColor($r('app.color.text_primary'))
                .fontSize(AppThemeTokens.showIconWell(this.appUIState.themeStyle) ? 14 : 13)
                .fontWeight(FontWeight.Medium)
                .width('100%')
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              if (item.description !== '' && AppThemeTokens.showIconWell(this.appUIState.themeStyle)) {
                Text(item.description)
                  .fontColor($r('app.color.text_1'))
                  .fontSize(12)
                  .width('100%')
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
              }
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
            .justifyContent(FlexAlign.Center)
            Text(item.rightText)
              .fontColor($r('app.color.text_1'))
              .fontSize(13)
              .fontWeight(FontWeight.Medium)
            SymbolGlyph($r('sys.symbol.chevron_right'))
              .fontSize(13)
              .fontColor([$r('app.color.text_primary')])
              .fontWeight(FontWeight.Bold)
              .visibility(item.showArrow ? Visibility.Visible : Visibility.None)
          }
          .width('100%')
          .height(AppThemeTokens.listRowHeight(this.appUIState.themeStyle))
          .padding({
            top: 12,
            bottom: 12,
            left: 16,
            right: 16
          })
          .stateStyles({ pressed: this.pressedStyles, normal: this.normalStyles })
          .onClick(() => {
            this.onItemClick(index)
          })

          if (index < this.items.length - 1) {
            Divider()
              .height('0.5px')
              .backgroundColor($r('app.color.border'))
              .margin({
                left: 16,
                right: 16
              })
          }
        })
      }.backgroundColor($r('app.color.bg_1'))
      .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    }
  }
}

export class BaseGroupItem {
  icon: ResourceStr = ''
  name: ResourceStr = ''
  rightText: ResourceStr = ''
  showArrow: boolean = true
  description: ResourceStr = ''

  constructor(name: ResourceStr, icon: ResourceStr = '', rightText: ResourceStr = '', showArrow: boolean = true,
    description: ResourceStr = '') {
    this.name = name
    this.icon = icon
    this.rightText = rightText
    this.showArrow = showArrow
    this.description = description
  }
}
