import { AppUIState } from "../../entity/AppUIState"
import { AppStorageV2 } from "@kit.ArkUI"
import { ActionBar } from "../../component/ActionBar"
import { ConnectionUIState } from "./ConnectionUIState"
import { ConnectionViewModel } from "./ConnectionViewModel"
import { ServerSelectionMode } from "../../ui/state/ServerSelectionMode"
import { ServerSuggestion } from "./ServerSuggestion"
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
          if (this.ui.serverSelectionMode === ServerSelectionMode.ADDRESS) {
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
                this.ui.serverSelectionMode = ServerSelectionMode.AUTO_DISCOVERY
              },
              onSubmit: () => {

              }
            })
          } else {
            ServerDiscoveryList({
              serverSuggestions: this.ui.serverSuggestions,
              onGoBack: () => {
                this.ui.serverSelectionMode = ServerSelectionMode.ADDRESS
              },
              onSelectServer: (url: string) => {
                this.ui.hostname = url
                this.ui.serverSelectionMode = ServerSelectionMode.ADDRESS
                //this.onSubmit()
              },
            }).height('100%')
          }
        }.alignItems(HorizontalAlign.Start).width('100%').margin({ top: 30 })

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
        }.width('100%').alignItems(HorizontalAlign.Start)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Start)
      .padding({ left: 16, right: 16 })
      .backgroundColor($r('app.color.bg_main'))
    }.height('100%').width('100%').scrollable(ScrollDirection.Vertical)
    .align(Alignment.Top)
  }
}

@ComponentV2
export struct LogoHeader {
  build() {
    Column() {
      Image($r('app.media.app_icon')).height(100)
      Text($r('app.string.app_name')).fontColor($r('app.color.text_primary')).fontSize(24).fontWeight(FontWeight.Bold)
    }.padding({ top: 10, bottom: 10 })
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
      .height(56)
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

@ComponentV2
struct ServerDiscoveryList {
  @Param @Require serverSuggestions: ServerSuggestion[]
  @Param @Require onGoBack: () => void
  @Param @Require onSelectServer: (url: string) => void

  build() {
    Column() {
      Row() {
        Button() {
          Image($r('app.media.icon_back')).width(40).height(40)
        }.backgroundColor(Color.Transparent).onClick(() => {
          this.onGoBack()
        }).size({ width: 40, height: 40 })

        Text('现有服务器').layoutWeight(1).padding(8).fontColor(Color.White)
        LoadingProgress().margin({
          left: 12, right: 12
        }).width(24).height(24)
      }

      Blank().height(8)
      Column() {
        Repeat<ServerSuggestion>(this.serverSuggestions).each((obj: RepeatItem<ServerSuggestion>) => {
          ServerDiscoveryItem({
            serverSuggestion: obj.item,
            onClickServer: () => {
              this.onSelectServer(obj.item.address)
            }
          })
        })
      }
      .width('100%')
      .layoutWeight(1)
      .margin({ bottom: 16 })
      .backgroundColor('#363636')
      .borderRadius(4)
    }.height('100%')
  }
}


@ComponentV2
struct ServerDiscoveryItem {
  @Param @Require serverSuggestion: ServerSuggestion
  @Param @Require onClickServer: () => void

  build() {
    Column() {
      Text(this.serverSuggestion.name).fontColor(Color.White).fontSize(18)
      Text(this.serverSuggestion.address).fontColor(Color.Gray).fontSize(16)
    }.width('100%')
    .padding(10).alignItems(HorizontalAlign.Start).onClick(() => {
      this.onClickServer()
    })
  }
}
