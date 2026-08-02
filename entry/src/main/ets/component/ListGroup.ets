import { AppStorageV2 } from '@kit.ArkUI';
import { AppUIState } from '../entity/AppUIState';
import { AppThemeSurfaceResolver } from '../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../theme/AppThemeTokens';

@ComponentV2
export struct ListGroup {
  @Param items: Array<BaseGroupItem> = []
  @Param onItemClick: (index: number) => void = (_index: number) => {
  }
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  @Styles
  pressedStyles(): void {
    .backgroundColor(this.useNativeSurface() ?
      AppThemeSurfaceResolver.interactionOverlay(this.appUIState.themeStyle) : $r('app.color.bg_hover'))
  }

  @Styles
  normalStyles(): void {
    .backgroundColor(Color.Transparent)
  }

  @Builder
  private groupContent() {
    ForEach(this.items, (item: BaseGroupItem, index) => {
      Row({ space: item.hasLeadingIcon() ? 10 : 0 }) {
        Stack() {
          if (this.useNativeSurface() && item.symbol) {
            SymbolGlyph(item.symbol)
              .fontSize(this.useNativeSurface() ? 20 : 18)
              .fontColor([AppThemeTokens.showIconWell(this.appUIState.themeStyle) ?
                $r('sys.color.icon_secondary') : $r('app.color.text_primary')])
          } else {
            Image(item.icon)
              .width(this.useNativeSurface() ? 20 : 18)
              .height(this.useNativeSurface() ? 20 : 18)
              .renderMode(this.useNativeSurface() ? ImageRenderMode.Template : ImageRenderMode.Original)
              .interpolation(ImageInterpolation.High)
              .fillColor(AppThemeTokens.showIconWell(this.appUIState.themeStyle) ?
                $r('sys.color.icon_secondary') : $r('app.color.text_primary'))
          }
        }
        .width(item.hasLeadingIcon() ? AppThemeTokens.iconWellSize(this.appUIState.themeStyle) : 0)
        .height(item.hasLeadingIcon() ? AppThemeTokens.iconWellSize(this.appUIState.themeStyle) : 0)
        .borderRadius(AppThemeTokens.iconWellRadius(this.appUIState.themeStyle))
        .backgroundColor(Color.Transparent)
        .visibility(item.hasLeadingIcon() ? Visibility.Visible : Visibility.None)
        Column() {
          Text(item.name)
            .fontColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
              $r('sys.color.font_primary') : $r('app.color.text_primary'))
            .fontSize(AppThemeTokens.showIconWell(this.appUIState.themeStyle) ? 16 : 13)
            .fontWeight(FontWeight.Medium)
            .width('100%')
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        .justifyContent(FlexAlign.Center)
        Text(item.rightText)
          .fontColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
            $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(AppThemeTokens.isNative(this.appUIState.themeStyle) ? 14 : 13)
          .fontWeight(AppThemeTokens.isNative(this.appUIState.themeStyle) ? FontWeight.Normal : FontWeight.Medium)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(AppThemeTokens.isNative(this.appUIState.themeStyle) ? 22 : 13)
          .fontColor([AppThemeTokens.isNative(this.appUIState.themeStyle) ?
            $r('sys.color.icon_tertiary') : $r('app.color.text_primary')])
          .fontWeight(AppThemeTokens.isNative(this.appUIState.themeStyle) ? FontWeight.Normal : FontWeight.Bold)
          .visibility(item.showArrow ? Visibility.Visible : Visibility.None)
      }
      .width('100%')
      .height(AppThemeTokens.listRowHeight(this.appUIState.themeStyle))
      .padding({
        top: this.useNativeSurface() ? 0 : 12,
        bottom: this.useNativeSurface() ? 0 : 12,
        left: this.useNativeSurface() ? 12 : 16,
        right: this.useNativeSurface() ? 12 : 16
      })
      .stateStyles({ pressed: this.pressedStyles, normal: this.normalStyles })
      .onClick(() => {
        this.onItemClick(index)
      })

      if (index < this.items.length - 1) {
        Divider()
          .height('0.5px')
          .backgroundColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
            $r('sys.color.comp_divider') : $r('app.color.border'))
          .margin({
            left: this.useNativeSurface() && item.hasLeadingIcon() ? 42 : 16,
            right: this.useNativeSurface() ? 12 : 16
          })
      }
    })
  }

  @Builder
  private nativeGroup() {
    Column() {
      this.groupContent()
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuGroup() {
    Column() {
      this.groupContent()
    }
    .width('100%')
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.appUIState.themeStyle) }) {
      if (this.useNativeSurface()) {
        this.nativeGroup()
      } else {
        this.feiniuGroup()
      }
    }
  }
}

export class BaseGroupItem {
  icon: ResourceStr = ''
  symbol: Resource | undefined = undefined
  name: ResourceStr = ''
  rightText: ResourceStr = ''
  showArrow: boolean = true
  description: ResourceStr = ''

  constructor(name: ResourceStr, icon: ResourceStr = '', rightText: ResourceStr = '', showArrow: boolean = true,
    description: ResourceStr = '', symbol: Resource | undefined = undefined) {
    this.name = name
    this.icon = icon
    this.rightText = rightText
    this.showArrow = showArrow
    this.description = description
    this.symbol = symbol
  }

  hasLeadingIcon(): boolean {
    return this.icon !== '' || this.symbol !== undefined
  }
}
