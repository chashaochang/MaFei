import { HMRouterMgr } from "@hadss/hmrouter"
import { UIConstants } from "../common/UIConstants"
import { AppUIState } from "../entity/AppUIState";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../theme/AppThemeTokens";
import { HomeContentNavigationController } from "../features/home/HomeContentNavigationController";

@ComponentV2
struct NativeActionBarItems {
  @Require @Param title: ResourceStr
  @Require @Param showBackButton: boolean
  @Require @Param rightBtnAccessibilityText: Resource
  @Require @Param appUIState: AppUIState
  @Require @Param safeTop: number
  @Param rightBtnIcon?: Resource = undefined
  @Event onBackPressed: () => void = () => {}
  @Event onRightPressed: () => void = () => {}

  build() {
    Row({ space: 4 }) {
      if (this.showBackButton) {
        Button() {
          SymbolGlyph($r('sys.symbol.chevron_backward'))
            .fontSize(22)
            .fontColor([$r('sys.color.icon_primary')])
        }
        .width(AppThemeTokens.actionSize(this.appUIState.themeStyle))
        .height(AppThemeTokens.actionSize(this.appUIState.themeStyle))
        .borderRadius(20)
        .backgroundColor(Color.Transparent)
        .onClick(() => this.onBackPressed())
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
        .accessibilityText(this.rightBtnAccessibilityText)
        .onClick(() => this.onRightPressed())
      }
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.safeTop)
    .padding({
      top: this.safeTop,
      left: AppThemeTokens.pageHorizontalPadding(this.appUIState.themeStyle, this.appUIState.isBigScreen),
      right: AppThemeTokens.pageHorizontalPadding(this.appUIState.themeStyle, this.appUIState.isBigScreen)
    })
    .alignItems(VerticalAlign.Center)
  }
}

@ComponentV2
struct FeiniuActionBar {
  @Require @Param title: ResourceStr
  @Require @Param showBackButton: boolean
  @Require @Param safeTop: number
  @Require @Param rightBtnAccessibilityText: Resource
  @Param rightBtnIcon?: Resource = undefined
  @Event onBackPressed: () => void = () => {}
  @Event onRightPressed: () => void = () => {}

  build() {
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
          .onClick(() => this.onBackPressed())
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
            .accessibilityText(this.rightBtnAccessibilityText)
            .onClick(() => this.onRightPressed())
        }
      }
      .width('100%')
      .height('100%')

      Text(this.title)
        .fontSize(18)
        .fontColor($r('app.color.text_primary'))
        .fontWeight(FontWeight.Medium)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .constraintSize({ maxWidth: '70%' })
    }
    .width('100%')
    .height(UIConstants.ACTION_BAR_HEIGHT + this.safeTop)
    .padding({ top: this.safeTop })
  }
}

@ComponentV2
export struct ActionBar {
  @Param title: ResourceStr = ""
  @Param showBackButton: boolean = true
  @Param floating: boolean = false
  @Event onBackClick: () => void = () => {
    if (HomeContentNavigationController.popHomeContentIfNeeded()) {
      return
    }
    HMRouterMgr.pop()
  }
  @Param rightBtnIcon?: Resource = undefined
  @Param rightBtnAccessibilityText?: Resource = undefined
  @Event onRightBtnClick: () => void = () => {}
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private rightButtonAccessibilityText(): Resource {
    return this.rightBtnAccessibilityText || $r('app.string.app_name')
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private effectiveSafeTop(): number {
    return this.appUIState.homeContentNavigationVisible ? 0 : this.appUIState.safeTop
  }

  build() {
    if (this.useNativeSurface()) {
      if (this.floating) {
        NativeActionBarItems({
          title: this.title,
          showBackButton: this.showBackButton,
          rightBtnIcon: this.rightBtnIcon,
          rightBtnAccessibilityText: this.rightButtonAccessibilityText(),
          appUIState: this.appUIState,
          safeTop: this.effectiveSafeTop(),
          onBackPressed: () => this.onBackClick(),
          onRightPressed: () => this.onRightBtnClick()
        })
          .renderGroup(true)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.Floating,
            true,
            this.appUIState.systemMaterialAvailable
          ))
      } else {
        NativeActionBarItems({
          title: this.title,
          showBackButton: this.showBackButton,
          rightBtnIcon: this.rightBtnIcon,
          rightBtnAccessibilityText: this.rightButtonAccessibilityText(),
          appUIState: this.appUIState,
          safeTop: this.effectiveSafeTop(),
          onBackPressed: () => this.onBackClick(),
          onRightPressed: () => this.onRightBtnClick()
        })
      }
    } else {
      FeiniuActionBar({
        title: this.title,
        showBackButton: this.showBackButton,
        safeTop: this.effectiveSafeTop(),
        rightBtnIcon: this.rightBtnIcon,
        rightBtnAccessibilityText: this.rightButtonAccessibilityText(),
        onBackPressed: () => this.onBackClick(),
        onRightPressed: () => this.onRightBtnClick()
      })
    }
  }
}
