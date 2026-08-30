import { AppUIState } from "../../entity/AppUIState"
import { AppStorageV2 } from "@kit.ArkUI"
import { ActionBar } from "../../component/ActionBar"
import { ConnectionUIState } from "./ConnectionUIState"
import { ConnectionViewModel } from "./ConnectionViewModel"
import { KeyCode } from "@kit.InputKit"
import { CheckUrlStateType, Unchecked, Error } from "../../ui/state/CheckUrlState"
import { inputMethod } from "@kit.IMEKit"
import { HMRouterMgr } from "@hadss/hmrouter"
import { RouterConsts } from "../../common/RouterConsts"
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver"
import { MediaProviderKind } from "../../media/MediaProviderKind"
import { FeiniuVideoConnectScreen } from "../feiniuvideo/account/FeiniuVideoConnectPage"
import { ManagementNativeSelect } from "../management/ManagementNativeSelect"
import { ThemeStyle } from "../../entity/ThemeStyle"

@ComponentV2
export struct ConnectScreen {
  @Param isFromAddAccount: boolean = false
  @Param showRouteActionBar: boolean = true
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local ui: ConnectionUIState = new ConnectionUIState(this.getUIContext())
  vm: ConnectionViewModel = new ConnectionViewModel(this.ui)
  @Local selectedProvider: MediaProviderKind = MediaProviderKind.Jellyfin
  @Consumer() showExternalConnectionError: boolean = false

  aboutToAppear(): void {
    this.ui.externalError = this.showExternalConnectionError
    if (!this.isFromAddAccount) {
      this.vm.init()
    }
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private selectInitialProvider(index: number): void {
    const nextProvider = index === 1 ? MediaProviderKind.FeiniuVideo : MediaProviderKind.Jellyfin
    if (this.selectedProvider === nextProvider) {
      return
    }
    this.selectedProvider = nextProvider
    inputMethod.getController().hideTextInput()
  }

  build() {
    Scroll() {
      Column() {
        if (this.showRouteActionBar) {
          ActionBar({
            title: '',
            showBackButton: this.isFromAddAccount
          })
        }
        if (!this.isFromAddAccount) {
          InitialProviderLoginPanel({
            selectedProvider: this.selectedProvider,
            ui: this.ui,
            themeStyle: this.appUIState.themeStyle,
            nativeThemeAvailable: this.appUIState.nativeThemeAvailable,
            showExternalConnectionError: this.showExternalConnectionError,
            onProviderSelect: (index: number): void => {
              this.selectInitialProvider(index)
            },
            onJellyfinLogin: (): void => {
              this.vm.onLogin(false)
            }
          })
        } else {
          LogoHeader({ nativeSurface: this.useNativeSurface() })
          JellyfinConnectionForm({
            ui: this.ui,
            nativeSurface: this.useNativeSurface(),
            showExternalConnectionError: this.showExternalConnectionError,
            onLogin: (): void => {
              this.vm.onLogin(true)
            }
          })
        }
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Start)
      .padding({ left: 16, right: 16 })
      .backgroundColor(this.showRouteActionBar && this.useNativeSurface() ?
        $r('app.color.native_canvas_background') : Color.Transparent)
    }
    .height('100%')
    .width('100%')
    .scrollable(ScrollDirection.Vertical)
    .align(Alignment.Top)
    .backgroundColor(this.showRouteActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@ComponentV2
struct InitialProviderLoginPanel {
  @Require @Param selectedProvider: MediaProviderKind
  @Require @Param ui: ConnectionUIState
  @Require @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Param showExternalConnectionError: boolean = false
  @Event onProviderSelect: (index: number) => void = () => {}
  @Event onJellyfinLogin: () => void = () => {}

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private selectedProviderIndex(): number {
    return this.selectedProvider === MediaProviderKind.FeiniuVideo ? 1 : 0
  }

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column() {
        LogoHeader({ nativeSurface: this.useNativeSurface() })
        if (this.selectedProvider === MediaProviderKind.Jellyfin) {
          JellyfinConnectionForm({
            ui: this.ui,
            nativeSurface: this.useNativeSurface(),
            showExternalConnectionError: this.showExternalConnectionError,
            onLogin: this.onJellyfinLogin
          })
        } else {
          FeiniuVideoConnectScreen({
            showRouteActionBar: false,
            embedded: true
          })
        }
      }
      .width('100%')
      Row() {
        ManagementNativeSelect({
          options: [
            $r('app.string.provider_jellyfin_name'),
            $r('app.string.feiniu_video_service_name')
          ],
          selectedIndex: this.selectedProviderIndex(),
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          selectorWidth: 136,
          valueTextAlign: TextAlign.Center,
          menuPlacement: Placement.BottomRight,
          onSelect: this.onProviderSelect
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.End)
      .margin({ top: 4, right: 2 })
    }
    .width('100%')
  }
}

@ComponentV2
struct JellyfinConnectionFields {
  @Require @Param ui: ConnectionUIState
  @Param nativeSurface: boolean = false
  @Param showExternalConnectionError: boolean = false
  @Event onLogin: () => void = () => {}

  private connectionErrorText(): string {
    if (this.showExternalConnectionError) {
      return '无法建立连接。 请检查主机名和你的网络连接。'
    }
    if (this.ui.checkUrlState.type === CheckUrlStateType.Error) {
      return (this.ui.checkUrlState as Error).message ?? ''
    }
    return ''
  }

  private openServerDiscovery(): void {
    HMRouterMgr.to(RouterConsts.ServerDiscoveryPage)
      .onResult((res) => {
        if (res && res.result) {
          this.ui.hostname = res.result as string
          inputMethod.getController().showTextInput()
        }
      })
      .push()
  }

  build() {
    Column() {
      AddressSelection({
      text: this.ui.hostname,
      errorText: this.connectionErrorText(),
      nativeSurface: this.nativeSurface,
      onTextChange: (value: string): void => {
        this.ui.externalError = false
        this.ui.checkUrlState = new Unchecked()
        this.ui.hostname = value
      }
    })

    Blank().height(16)
    AuthTextField({
      text: this.ui.username,
      placeholder: '请输入用户名',
      nativeSurface: this.nativeSurface,
      onTextChanged: (value: string): void => {
        this.ui.username = value
      }
    })
    Blank().height(16)
    AuthTextField({
      text: this.ui.password,
      placeholder: '请输入密码',
      password: true,
      nativeSurface: this.nativeSurface,
      onTextChanged: (value: string): void => {
        this.ui.password = value
      }
    })
    AnimatedErrorText({
      errorText: this.ui.errorText,
      nativeSurface: this.nativeSurface
    }).padding({ top: 4 })
    Row({ space: 4 }) {
      Toggle({ type: ToggleType.Checkbox, isOn: this.ui.rememberPwd })
        .width(16)
        .onChange((isOn: boolean): void => {
          this.ui.rememberPwd = isOn
        })
      Text('记住密码')
        .fontSize(12)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
    }
    .onClick((): void => {
      this.ui.rememberPwd = !this.ui.rememberPwd
    })
    .margin({ top: 4 })

    if (!this.ui.loading) {
      Blank().height(20)
      StyledTextButton({
        text: '登录',
        enable: this.ui.username.length > 0 && this.ui.password.length > 0,
        nativeSurface: this.nativeSurface,
        onPressed: this.onLogin
      })
    } else {
      Row() {
        LoadingProgress().width(32).height(32)
        Text('正在登录...')
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
          .padding({ left: 12 })
      }.padding({ top: 24 })
    }
      Text('发现局域网内的设备')
        .fontSize(12)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .padding({ top: 24, bottom: 8 })
        .onClick((): void => {
          this.openServerDiscovery()
        })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct JellyfinConnectionForm {
  @Require @Param ui: ConnectionUIState
  @Param nativeSurface: boolean = false
  @Param showExternalConnectionError: boolean = false
  @Event onLogin: () => void = () => {}

  build() {
    if (this.nativeSurface) {
      Column() {
        JellyfinConnectionFields({
          ui: this.ui,
          nativeSurface: true,
          showExternalConnectionError: this.showExternalConnectionError,
          onLogin: this.onLogin
        })
      }
      .width('100%')
      .constraintSize({ maxWidth: 375 })
      .alignItems(HorizontalAlign.Start)
      .padding({ top: 16, bottom: 16, left: 12, right: 12 })
      .margin({ top: 20 })
    } else {
      Column() {
        JellyfinConnectionFields({
          ui: this.ui,
          nativeSurface: false,
          showExternalConnectionError: this.showExternalConnectionError,
          onLogin: this.onLogin
        })
      }
      .width('100%')
      .constraintSize({ maxWidth: 375 })
      .alignItems(HorizontalAlign.Start)
      .margin({ top: 30 })
    }
  }
}

@ComponentV2
export struct LogoHeader {
  @Param nativeSurface: boolean = false

  build() {
    Column() {
      Image($r('app.media.app_icon'))
        .height(70)
      Text($r('app.string.app_name'))
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
    }.padding({ top: 24, bottom: 2 })
  }
}

// Reusable text field for username / password
@ComponentV2
struct AuthTextField {
  @Param text: string = ''
  @Param placeholder: string = ''
  @Param password: boolean = false
  @Param nativeSurface: boolean = false
  @Event onTextChanged: (val: string) => void = () => {}
  @Event onSubmit: () => void = () => {}

  build() {
    Stack() {
      TextInput({
        text: this.text,
        placeholder: this.placeholder,
      })
        .onChange((v) => this.onTextChanged(v))
        .width('100%')
        .padding({ left: 16, right: 16 })
        .onSubmit(() => this.onSubmit())
        .type(this.password ? InputType.Password : InputType.Normal)
        .maxLines(1)
        .borderWidth(this.nativeSurface ? 0 : 1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(this.nativeSurface ? 14 : 8)
        .height(this.nativeSurface ? 52 : 56)
        .placeholderColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.component_bg'))
    }
    .height(this.nativeSurface ? 52 : 56)
    .borderRadius(this.nativeSurface ? 14 : 8)
    .clip(true)
    .alignContent(Alignment.Start)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.AdaptiveControl,
      this.nativeSurface
    ))
  }
}


@ComponentV2
export struct AnimatedErrorText {
  @Param errorText: string = ''
  @Param nativeSurface: boolean = false

  build() {
    if (this.errorText && this.errorText.length > 0) {
      Text(this.errorText)
        .width('100%')
        .fontColor(this.nativeSurface ? $r('app.color.status_warning') : Color.Red)
    } else {
      Blank()
    }
  }
}

@ComponentV2
export struct StyledTextButton {
  @Require @Param text: ResourceStr
  @Param enable: boolean = false
  @Param nativeSurface: boolean = false
  @Event onPressed: () => void = () => {}

  build() {
    Button(this.text, { type: ButtonType.Normal, stateEffect: true })
      .borderRadius(this.nativeSurface ? 24 : 8)
      .backgroundColor(this.nativeSurface ? Color.Transparent : 0x317aff)
      .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : Color.White)
      .width('100%')
      .height(48)
      .onClick(() => this.onPressed())
      .enabled(this.enable)
      .padding({ top: 4, bottom: 4 })
      .margin({ top: 24 })
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl,
        this.nativeSurface
      ))
  }
}

@ComponentV2
struct AddressSelection {
  @Param text: string = ''
  @Param errorText: string = ''
  @Param nativeSurface: boolean = false
  @Event onTextChange: (x: string) => void = () => {}
  @Event onSubmit: () => void = () => {}

  build() {
    Column() {
      ServerUrlField({
        text: this.text,
        nativeSurface: this.nativeSurface,
        onTextChanged: this.onTextChange,
        onSubmit: this.onSubmit
      })
      AnimatedErrorText({
        errorText: this.errorText,
        nativeSurface: this.nativeSurface
      })
    }
  }
}

@ComponentV2
struct ServerUrlField {
  @Param text: string = ''
  @Param nativeSurface: boolean = false
  @Event onTextChanged: (val: string) => void = () => {}
  @Event onSubmit: () => void = () => {}

  private openAccountHistory(): void {
    HMRouterMgr.to(RouterConsts.AccountPage).withParam({
      fromLogin: true
    }).push()
  }

  build() {
    Stack() {
      TextInput({
        text: this.text,
        placeholder: '请输入IP地址/域名',
      })
        .onChange((v) => {
          this.onTextChanged(v)
        })
        .width('100%')
        .padding({ left: 16, right: 48 })
        .onKeyEvent((event) => {
          if (event.keyCode === KeyCode.KEYCODE_ENTER) {
            this.onSubmit()
          }
        })
        .type(InputType.URL)
        .onSubmit(() => {
          this.onSubmit()
        })
        .maxLines(1)
        .borderWidth(this.nativeSurface ? 0 : 1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(this.nativeSurface ? 14 : 8)
        .height(this.nativeSurface ? 52 : 56)
        .placeholderColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.component_bg'))
      if (this.nativeSurface) {
        SymbolGlyph($r('sys.symbol.externaldrive'))
          .fontSize(20)
          .fontColor([$r('sys.color.icon_primary')])
          .margin({ right: 14 })
          .onClick(() => this.openAccountHistory())
      } else {
        Image($r('app.media.history'))
          .height(20)
          .fillColor($r('app.color.text_primary'))
          .margin({ right: 12 })
          .onClick(() => this.openAccountHistory())
      }
    }
    .height(this.nativeSurface ? 52 : 56)
    .borderRadius(this.nativeSurface ? 14 : 8)
    .clip(true)
    .alignContent(Alignment.End)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.AdaptiveControl,
      this.nativeSurface
    ))
  }
}
