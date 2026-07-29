import { HMRouterMgr } from "@hadss/hmrouter"
import { UIConstants } from "../common/UIConstants"
import { AppUIState } from "../entity/AppUIState";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../theme/AppThemeTokens";

@ComponentV2
export struct ActionBar {
  @Param title: ResourceStr = ""
  @Param showBackButton: boolean = true
  @Param floating: boolean = false
  @Param onBackClick: () => void = () => {
    HMRouterMgr.pop()
  }
  @Param rightBtnIcon?: Resource = undefined
  @Param rightBtnAccessibilityText?: Resource = undefined
  @Param onRightBtnClick?: () => void = () => {
  }
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private rightButtonAccessibilityText(): Resource {
    return this.rightBtnAccessibilityText || $r('app.string.app_name')
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  @Builder
  nativeActionButton(icon: Resource, onClick: () => void) {
    Button() {
      SymbolGlyph(icon)
        .fontSize(22)
        .fontColor([$r('sys.color.icon_primary')])
    }
    .width(AppThemeTokens.actionSize(this.appUIState.themeStyle))
    .height(AppThemeTokens.actionSize(this.appUIState.themeStyle))
    .borderRadius(20)
    .backgroundColor(Color.Transparent)
    .onClick(onClick)
  }

  @Builder
  nativeBarItems() {
    if (this.showBackButton) {
      this.nativeActionButton($r('sys.symbol.chevron_backward'), () => {
        this.onBackClick()
      })
    }
    Text(this.title)
      .fontSize(AppThemeTokens.titleSize(this.appUIState.themeStyle))
      .fontColor($r('sys.color.font_primary'))
      .fontWeight(FontWeight.Bold)
      .maxLines(1)
      .textOverflow({ overflow: TextOverflow.Ellipsis })
      .layoutWeight(1)
    if (this.rightBtnIcon) {
      Button() {
        Image(this.rightBtnIcon)
          .width(20)
          .height(20)
          .fillColor($r('sys.color.icon_primary'))
      }
      .width(AppThemeTokens.actionSize(this.appUIState.themeStyle))
      .height(AppThemeTokens.actionSize(this.appUIState.themeStyle))
      .borderRadius(20)
      .backgroundColor(Color.Transparent)
      .accessibilityText(this.rightButtonAccessibilityText())
      .onClick(() => {
        this.onRightBtnClick && this.onRightBtnClick()
      })
    }
  }

  @Builder
  nativeBarContent() {
    Row({ space: 4 }) {
      this.nativeBarItems()
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
    .padding({
      top: this.appUIState.safeTop,
      left: AppThemeTokens.pageHorizontalPadding(this.appUIState.themeStyle, this.appUIState.isBigScreen),
      right: AppThemeTokens.pageHorizontalPadding(this.appUIState.themeStyle, this.appUIState.isBigScreen)
    })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  nativeBar() {
    if (this.floating) {
      Row({ space: 4 }) {
        this.nativeBarItems()
      }
      .width('100%')
      .height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
      .padding({
        top: this.appUIState.safeTop,
        left: AppThemeTokens.pageHorizontalPadding(this.appUIState.themeStyle, this.appUIState.isBigScreen),
        right: AppThemeTokens.pageHorizontalPadding(this.appUIState.themeStyle, this.appUIState.isBigScreen)
      })
      .alignItems(VerticalAlign.Center)
      .renderGroup(true)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))
    } else {
      this.nativeBarContent()
    }
  }

  @Builder
  feiniuBar() {
    Stack() {
      Row() {
        SymbolGlyph($r('sys.symbol.arrow_left'))
          .fontSize(20)
          .fontColor([$r('app.color.text_primary')])
          .fontWeight(FontWeight.Bold)
          .align(Alignment.Center)
          .width(48)
          .height(48)
          .padding(16)
          .borderRadius(20)
          .backgroundColor(Color.Transparent)
          .onClick(() => {
            this.onBackClick()
          })
          .visibility(this.showBackButton ? Visibility.Visible : Visibility.Hidden)
        Blank()
        if (this.rightBtnIcon) {
          Image(this.rightBtnIcon)
            .width(48)
            .height(48)
            .padding(16)
            .borderRadius(20)
            .backgroundColor(Color.Transparent)
            .fillColor($r('app.color.text_primary'))
            .accessibilityText(this.rightButtonAccessibilityText())
            .onClick(() => {
              this.onRightBtnClick && this.onRightBtnClick()
            })
        }
      }.width('100%').height('100%')

      Text(this.title)
        .fontSize(18)
        .fontColor($r('app.color.text_primary'))
        .fontWeight(FontWeight.Medium)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .constraintSize({ maxWidth: '70%' })
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
    .padding({ top: this.appUIState.safeTop })
  }

  build() {
    if (this.useNativeSurface()) {
      this.nativeBar()
    } else {
      this.feiniuBar()
    }
  }
}
