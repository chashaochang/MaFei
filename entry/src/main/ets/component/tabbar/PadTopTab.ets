import { UIConstants } from '../../common/UIConstants'
import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'

export class TabItem {
  title: string
  selectedImg: Resource
  normalImg: Resource

  constructor(title: string, selectedImg: Resource, normalImg: Resource) {
    this.title = title
    this.selectedImg = selectedImg
    this.normalImg = normalImg
  }
}

@ComponentV2
export struct PadTopTab {
  @Require @Param selectIndex: number
  @Require @Param onItemClick: (index: number) => void
  @Param onMenuButtonClick: (() => void) | undefined = undefined
  @Param list: TabItem[] = []
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  @Builder
  private tabContent(nativeSurface: boolean) {
    if (this.onMenuButtonClick) {
      Button() {
        SymbolGlyph($r('sys.symbol.open_sidebar'))
          .fontSize(18)
          .fontColor([$r('app.color.text_primary')])
      }
      .width(40)
      .height(40)
      .fontWeight(FontWeight.Bold)
      .borderRadius(20)
      .align(Alignment.Center)
      .backgroundColor(Color.Transparent)
      .onClick(() => {
        this.onMenuButtonClick?.()
      })
    }
    ForEach(this.list, (item: TabItem, index: number) => {
      Column({ space: 4 }) {
        Text(item.title)
          .fontSize(16)
          .fontColor(this.selectIndex === index ? $r('app.color.color_main') : $r('app.color.text_primary'))
          .fontWeight(FontWeight.Medium)
      }
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .padding({
        left: 16,
        right: 16
      })
      .backgroundColor(nativeSurface ?
        (this.selectIndex === index ?
          AppThemeSurfaceResolver.interactionOverlay(this.appUIState.themeStyle) : Color.Transparent) :
        (this.selectIndex === index ? $r('app.color.bg_tab') : Color.Transparent))
      .borderRadius(UIConstants.BOTTOM_BAR_HEIGHT / 2)
      .onClick(() => {
        this.onItemClick(index)
      })
    })
  }

  @Builder
  private nativeTab() {
    Row({ space: 8 }) {
      Row() {
        this.tabContent(true)
      }
      .padding(3)
      .height(UIConstants.PAD_TOP_BAR_HEIGHT)
      .borderRadius(UIConstants.PAD_TOP_BAR_HEIGHT / 2)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.Floating,
        true,
        this.appUIState.systemMaterialAvailable
      ))
    }
  }

  @Builder
  private feiniuTab() {
    Row({ space: 8 }) {
      Row() {
        this.tabContent(false)
      }
      .padding(3)
      .height(UIConstants.PAD_TOP_BAR_HEIGHT)
      .backgroundEffect({
        radius: 12,
        color: $r('app.color.bg_tab_item')
      })
      .borderRadius(UIConstants.PAD_TOP_BAR_HEIGHT / 2)
      .borderWidth(1.5)
      .borderColor($r('app.color.tab'))
      .shadow({
        radius: 50,
        color: 'rgba(0,0,0,0.1)',
      })
    }
  }

  build() {
    if (this.useNativeSurface()) {
      this.nativeTab()
    } else {
      this.feiniuTab()
    }
  }
}
