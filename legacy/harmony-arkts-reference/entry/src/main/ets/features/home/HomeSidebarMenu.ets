import { AppUIState } from '../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { FeiniuPointLightModifier } from '../../theme/FeiniuPointLightModifier'
import { HomeShellKind } from './HomeShellPolicy'
import { HomeSidebarItem, HomeSidebarItemType } from './HomePadNavigation'

@ComponentV2
struct HomeSidebarEntry {
  @Require @Param item: HomeSidebarItem
  @Require @Param appUIState: AppUIState
  @Param selected: boolean = false
  @Param compact: boolean = false
  @Param activateSelectedItem: boolean = false
  @Param shell: HomeShellKind = HomeShellKind.MediumDrawer
  @Param nativeSurface: boolean = false
  @Event onSelect: (item: HomeSidebarItem) => void = (_item: HomeSidebarItem) => {}

  private itemColor(): ResourceColor {
    return this.selected ? $r('app.color.color_main') : $r('app.color.text_primary')
  }

  build() {
    if (this.item.type === HomeSidebarItemType.Title) {
      if (!this.compact) {
        Text(this.item.title)
          .fontColor($r('app.color.text_1'))
          .fontSize(12)
          .height(20)
          .margin({ top: 20, left: 10 })
      }
    } else {
      Row({ space: this.compact ? 0 : 8 }) {
        if (this.item.symbolIcon !== undefined) {
          SymbolGlyph(this.item.symbolIcon)
            .fontSize(20)
            .fontColor([this.itemColor()])
        } else {
          Image(this.item.icon)
            .fillColor(this.itemColor())
            .width(20)
            .height(20)
        }
        if (!this.compact) {
          Text(this.item.title)
            .fontColor(this.itemColor())
            .fontSize(14)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .layoutWeight(1)
          if (this.item.badge > 0) {
            Text(this.item.badge > 99 ? '99+' : this.item.badge.toString())
              .fontColor(this.itemColor())
              .fontSize(14)
          }
        }
      }
      .backgroundColor(this.selected ? $r('app.color.sidebar_item_selected_bg') : Color.Transparent)
      .width(this.compact ? 40 : '100%')
      .constraintSize({ minHeight: 40 })
      .padding(this.compact ? 0 : 10)
      .borderRadius(this.compact ? 12 : 8)
      .justifyContent(this.compact ? FlexAlign.Center : FlexAlign.Start)
      .accessibilityText(this.item.title)
      .onClick(() => {
        if (!this.selected || this.activateSelectedItem) {
          this.onSelect(this.item)
        }
      })
    }
  }
}

@ComponentV2
export struct HomeSidebarMenu {
  @Require @Param items: HomeSidebarItem[]
  @Param bottomItems: HomeSidebarItem[] = []
  @Require @Param appUIState: AppUIState
  @Param selectedId: string = ''
  @Param compact: boolean = false
  @Param activateSelectedItem: boolean = false
  @Param shell: HomeShellKind = HomeShellKind.MediumDrawer
  @Param showCloseButton: boolean = false
  @Param nativeSurface: boolean = false
  @Event onClose: () => void = () => {}
  @Event onExpand: () => void = () => {}
  @Event onItemSelect: (item: HomeSidebarItem) => void = (_item: HomeSidebarItem) => {}

  private selected(item: HomeSidebarItem): boolean {
    return item.id === this.selectedId
  }

  private visibleItems(): HomeSidebarItem[] {
    if (!this.compact) {
      return this.items
    }
    return this.items.filter((item: HomeSidebarItem) => item.type === HomeSidebarItemType.Item)
  }

  build() {
    Column() {
      if (this.compact) {
        Row() {
          Button() {
            SymbolGlyph($r('sys.symbol.open_sidebar'))
              .fontSize(20)
              .fontColor([$r('app.color.text_primary')])
          }
          .width(40)
          .height(40)
          .borderRadius(20)
          .align(Alignment.Center)
          .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
          .accessibilityText('展开侧边栏')
          .onClick(this.onExpand)
        }
        .width('100%')
        .height(40)
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)
      } else {
        Row() {
          Row({ space: 8 }) {
            Image($r('app.media.app_icon'))
              .width(32)
              .height(32)
            Text($r('app.string.app_name'))
              .fontColor($r('app.color.text_primary'))
              .fontSize(16)
              .fontWeight(FontWeight.Normal)
          }
          .layoutWeight(1)
          .height(40)
          .justifyContent(FlexAlign.Start)
          .alignItems(VerticalAlign.Center)
          if (this.showCloseButton) {
            Button() {
              SymbolGlyph($r('sys.symbol.close_sidebar'))
                .fontSize(20)
                .fontColor([$r('app.color.text_primary')])
            }
            .width(40)
            .height(40)
            .borderRadius(20)
            .align(Alignment.Center)
            .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
            .onClick(this.onClose)
          } else {
            Blank().width(40).height(40)
          }
        }
        .width('100%')
        .height(40)
        .padding({ left: 12, right: 12 })
      }

      List() {
        ForEach(this.visibleItems(), (item: HomeSidebarItem) => {
          ListItem() {
            HomeSidebarEntry({
              item,
              appUIState: this.appUIState,
              selected: this.selected(item),
              compact: this.compact,
              activateSelectedItem: this.activateSelectedItem,
              shell: this.shell,
              nativeSurface: this.nativeSurface,
              onSelect: this.onItemSelect
            })
          }
        }, (item: HomeSidebarItem) => item.id)
      }
      .margin({ top: 12 })
      .padding({ left: this.compact ? 8 : 12, right: this.compact ? 8 : 12 })
      .width('100%')
      .listDirection(Axis.Vertical)
      .scrollBar(BarState.Off)
      .layoutWeight(1)

      if (this.bottomItems.length > 0) {
        Divider()
          .color($r('app.color.border'))
          .strokeWidth(0.5)
          .margin({ left: 12, right: 12, top: 10, bottom: 10 })
        Column() {
          ForEach(this.bottomItems, (item: HomeSidebarItem) => {
            HomeSidebarEntry({
              item,
              appUIState: this.appUIState,
              selected: this.selected(item),
              compact: this.compact,
              activateSelectedItem: this.activateSelectedItem,
              shell: this.shell,
              nativeSurface: this.nativeSurface,
              onSelect: this.onItemSelect
            })
          }, (item: HomeSidebarItem) => item.id)
        }
        .width('100%')
        .padding({ left: this.compact ? 8 : 12, right: this.compact ? 8 : 12 })
      }
    }
    .width(this.compact ? 56 : 240)
    .height('100%')
    .padding({ top: 8, bottom: 8 })
    .borderRadius(20)
    // .backgroundBlurStyle(this.nativeSurface ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THIN)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating,
      this.nativeSurface,
      this.appUIState.systemMaterialAvailable))
    .borderColor(this.nativeSurface ?
      (this.appUIState.systemMaterialAvailable ? Color.Transparent : $r('sys.color.comp_divider')) :
      $r('app.color.border'))
    .borderWidth(this.nativeSurface && this.appUIState.systemMaterialAvailable ? 0 : 1)
    .visualEffect(FeiniuPointLightModifier.build({
      enabled: !this.nativeSurface && this.appUIState.feiniuPointLightEnabled
    }))
  }
}
