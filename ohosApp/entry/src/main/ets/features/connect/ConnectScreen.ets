import { AppUIState } from "../../entity/AppUIState"
import { AppStorageV2 } from "@kit.ArkUI"
import { ActionBar } from "../../component/ActionBar"
import { ConnectionUIState } from "./ConnectionUIState"
import { ConnectionViewModel } from "./ConnectionViewModel"
import { ServerSelectionMode } from "../../ui/state/ServerSelectionMode"
import { ServerSuggestion } from "../serverdiscovery/ServerSuggestion"
import { KeyCode } from "@kit.InputKit"
import { CheckUrlStateType, Unchecked, Error } from "../../ui/state/CheckUrlState"
import { inputMethod } from "@kit.IMEKit"
import { HMRouterMgr } from "@hadss/hmrouter"
import { RouterConsts } from "../../common/RouterConsts"

@ComponentV2
export struct ConnectScreen {
  @Param isFromAddAccount: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local ui: ConnectionUIState = new ConnectionUIState(this.getUIContext())
  vm: ConnectionViewModel = new ConnectionViewModel(this.ui)
  @Consumer() showExternalConnectionError: boolean = false

  aboutToAppear(): void {
    this.ui.externalError = this.showExternalConnectionError
    if (!this.isFromAddAccount) {
      this.vm.init()
    }
  }

  build() {
    Scroll() {
      Column() {
        ActionBar({
          title: '',
          showBackButton: this.isFromAddAccount
        })
        LogoHeader()
        Column() {
          AddressSelection({
            text: this.ui.hostname,
            errorText: this.showExternalConnectionError ? '无法建立连接。 请检查主机名和你的网络连接。' :
              (this.ui.checkUrlState.type === CheckUrlStateType.Error ? (this.ui.checkUrlState as Error).message :
                undefined),
            loading: this.ui.checkUrlState.type === CheckUrlStateType.Pending,
            onTextChange: (value: string) => {
              this.ui.externalError = false
              this.ui.checkUrlState = new Unchecked()
              this.ui.hostname = value
            },
            onDiscoveryClick: () => {
              this.ui.externalError = false
              inputMethod.getController().hideTextInput()
            },
            onSubmit: () => {

            }
          })
        }.alignItems(HorizontalAlign.Start).width('100%').margin({ top: 30 })
        .constraintSize({
          maxWidth: 375,
        })

        Blank().height(16)
        Column() {
          // Text('用户名').padding({ bottom: 8 }).fontSize(14).fontColor(Color.White).fontWeight(FontWeight.Medium)
          AuthTextField({
            text: this.ui.username,
            // icon: $r('app.media.ic_user'),
            placeholder: '请输入用户名',
            onTextChanged: (v: string) => {
              this.ui.username = v
            }
          })
          Blank().height(16)
          AuthTextField({
            text: this.ui.password,
            // icon: $r('app.media.ic_pwd'),
            placeholder: '请输入密码',
            password: true,
            onTextChanged: (v: string) => {
              this.ui.password = v
            }
          })
          AnimatedErrorText({ errorText: this.ui.errorText }).padding({ top: 4 })
          Row({ space: 4 }) {
            Toggle({ type: ToggleType.Checkbox, isOn: this.ui.rememberPwd })
              .width(16)
              .onChange((isOn) => {
                this.ui.rememberPwd = isOn
              })
            Text("记住密码")
              .fontSize(12)
              .fontColor(Color.Gray)
          }.onClick(() => {
            this.ui.rememberPwd = !this.ui.rememberPwd;
          }).margin({
            top: 4
          })

          if (!this.ui.loading) {
            Blank().height(20)
            StyledTextButton({
              text: '登录',
              enable: this.ui.username.length > 0 && this.ui.password.length > 0,
              onclick: (): void => {
                this.vm.onLogin(this.isFromAddAccount)
              }
            })
          } else {
            // Simple loading indicator placeholder
            Row() {
              LoadingProgress().width(32).height(32)
              Text('正在登录...').fontColor(Color.Gray).padding({ left: 12 })
            }.padding({ top: 24 })
          }
          Text("发现局域网内的设备").fontSize(12).fontColor($r('app.color.text_2')).padding({ top: 24, bottom: 8 })
            .onClick(() => {
              HMRouterMgr.to(RouterConsts.ServerDiscoveryPage)
                .onResult((res) => {
                  if (res && res.result) {
                    this.ui.hostname = res.result as string
                    inputMethod.getController().showTextInput()
                  }
                })
                .push()
            })
        }.width('100%').alignItems(HorizontalAlign.Start)
        .constraintSize({
          maxWidth: 375,
        })
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Start)
      .padding({ left: 16, right: 16 })
    }
    .height('100%')
    .width('100%')
    .scrollable(ScrollDirection.Vertical)
    .align(Alignment.Top)
    .backgroundColor($r('app.color.bg_main'))
  }
}

@ComponentV2
export struct LogoHeader {
  build() {
    Column() {
      Image($r('app.media.app_icon')).height(70)
      Text($r('app.string.app_name')).fontColor($r('app.color.text_primary')).fontSize(18).fontWeight(FontWeight.Medium)
    }.padding({ bottom: 2 })
  }
}

// Reusable text field for username / password
@Component
struct AuthTextField {
  @Prop text: string
  // @Prop icon: ResourceStr
  @Prop placeholder: string
  @Prop password: boolean = false
  onTextChanged: (val: string) => void = () => {
  }
  onSubmit: () => void = () => {
  }

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
        .borderWidth(1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(8)
        .height(56)
        .placeholderColor(Color.Gray)
        .fontColor($r('app.color.text_primary'))
        .backgroundColor($r('app.color.component_bg'))
      // Image(this.icon).height(24).margin({ left: 16 })
    }.height(56).alignContent(Alignment.Start)
  }
}


@Component
export struct AnimatedErrorText {
  @Prop errorText: string

  build() {
    if (this.errorText && this.errorText.length > 0) {
      Text(this.errorText).width('100%').fontColor(Color.Red)
    } else {
      Blank()
    }
  }
}

@Component
export struct StyledTextButton {
  @Prop text: string
  @Prop enable: boolean
  onclick: () => void = () => {
  }

  build() {
    Button(this.text, { type: ButtonType.Normal, stateEffect: true })
      .borderRadius(8)
      .backgroundColor(0x317aff)
      .width('100%')
      .height(48)
      .onClick(() => this.onclick())
      .enabled(this.enable)
      .padding({ top: 4, bottom: 4 })
      .margin({ top: 24 })
  }
}

@Component
struct AddressSelection {
  @Prop text: string
  @Prop errorText: string
  @Prop loading: boolean
  onTextChange: (x: string) => void = () => {
  }
  onDiscoveryClick: () => void = () => {
  }
  onSubmit: () => void = () => {
  }

  build() {
    Column() {
      ServerUrlField({
        text: this.text,
        onTextChanged: this.onTextChange,
        onSubmit: this.onSubmit
      })
      AnimatedErrorText({
        errorText: this.errorText
      })
      // if (!this.loading) {
      //   Blank().height(12)
      //   StyledTextButton({
      //     text: '连接服务器',
      //     enable: this.text.length > 0,
      //     onclick: this.onSubmit,
      //   })
      //   // StyledTextButton({
      //   //   text: '选择服务器',
      //   //   onclick: this.onDiscoveryClick
      //   // })
      // } else {
      //
      // }
    }
  }
}

@Component
struct ServerUrlField {
  @Prop text: string
  onTextChanged: (val: string) => void = () => {
  }
  onSubmit: () => void = () => {
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
        .borderWidth(1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(8)
        .height(56)
        .placeholderColor(Color.Gray)
        .fontColor($r('app.color.text_primary'))
        .backgroundColor($r('app.color.component_bg'))
      Image($r('app.media.history')).height(20).fillColor($r('app.color.text_primary')).margin({ right: 12 })
        .onClick(() => {
          HMRouterMgr.to(RouterConsts.AccountPage).withParam({
            fromLogin: true
          }).push()
        })
    }.height(56).alignContent(Alignment.End)
  }
}