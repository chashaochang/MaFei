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
struct PadTopTabItem {
  @Require @Param item: TabItem
  @Require @Param index: number
  @Require @Param selectIndex: number
  @Require @Param nativeSurface: boolean
  @Require @Param onItemClick: (index: number) => void
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private selectedTextColor(): ResourceColor {
    return this.selectIndex === this.index ? Color.Black : $r('app.color.text_primary')
  }

  build() {
    if (this.nativeSurface) {
      Column({ space: 4 }) {
        Text(this.item.title)
          .fontSize(16)
          .fontColor(this.selectedTextColor())
          .fontWeight(FontWeight.Medium)
      }
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .padding({ left: 16, right: 16 })
      .borderRadius(UIConstants.BOTTOM_BAR_HEIGHT / 2)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        this.selectIndex === this.index ? AppThemeMaterialRole.HomeLibraryChipSelected : AppThemeMaterialRole.Floating,
        this.selectIndex === this.index,
        this.appUIState.systemMaterialAvailable
      ))
      .onClick(() => {
        this.onItemClick(this.index)
      })
    } else {
      Column({ space: 4 }) {
        Text(this.item.title)
          .fontSize(16)
          .fontColor(this.selectedTextColor())
          .fontWeight(FontWeight.Medium)
      }
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .padding({ left: 16, right: 16 })
      .backgroundColor(this.selectIndex === this.index ?
        $r('app.color.native_fallback_home_library_chip_selected') : Color.Transparent)
      .borderRadius(UIConstants.BOTTOM_BAR_HEIGHT / 2)
      .onClick(() => {
        this.onItemClick(this.index)
      })
    }
  }
}

@ComponentV2
struct PadTopTabContent {
  @Require @Param nativeSurface: boolean
  @Require @Param selectIndex: number
  @Require @Param list: TabItem[]
  @Require @Param onItemClick: (index: number) => void
  @Param onMenuButtonClick: (() => void) | undefined = undefined

  build() {
    Row() {
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
        PadTopTabItem({
          item,
          index,
          selectIndex: this.selectIndex,
          nativeSurface: this.nativeSurface,
          onItemClick: this.onItemClick
        })
      })
    }
    .height('100%')
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

  build() {
    if (this.useNativeSurface()) {
      Row({ space: 8 }) {
        Row() {
          PadTopTabContent({
            nativeSurface: true,
            selectIndex: this.selectIndex,
            list: this.list,
            onItemClick: this.onItemClick,
            onMenuButtonClick: this.onMenuButtonClick
          })
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
    } else {
      Row({ space: 8 }) {
        Row() {
          PadTopTabContent({
            nativeSurface: false,
            selectIndex: this.selectIndex,
            list: this.list,
            onItemClick: this.onItemClick,
            onMenuButtonClick: this.onMenuButtonClick
          })
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
  }
}
