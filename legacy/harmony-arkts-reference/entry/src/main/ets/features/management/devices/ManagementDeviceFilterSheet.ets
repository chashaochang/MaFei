import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { ManagementNativeSelect } from '../ManagementNativeSelect'
import { ManagementDeviceSortField } from './ManagementDeviceModels'

@ComponentV2
export struct ManagementDeviceFilterSheet {
  @Require @Param initialRecentDays: number
  @Require @Param initialUsers: string[]
  @Require @Param availableUsers: string[]
  @Require @Param initialSortField: ManagementDeviceSortField
  @Require @Param initialAscending: boolean
  @Require @Param onApply: (recentDays: number, users: string[],
    sortField: ManagementDeviceSortField, ascending: boolean) => void
  @Local draftRecentDays: number = 0
  @Local draftUsers: string[] = []
  @Local draftSortField: ManagementDeviceSortField = ManagementDeviceSortField.LastActivity
  @Local draftAscending: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  aboutToAppear(): void {
    this.draftRecentDays = this.initialRecentDays
    this.draftUsers = this.initialUsers.slice()
    this.draftSortField = this.initialSortField
    this.draftAscending = this.initialAscending
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private recentIndex(): number {
    return this.draftRecentDays === 7 ? 1 : (this.draftRecentDays === 30 ? 2 : 0)
  }

  private sortIndex(): number {
    if (this.draftSortField === ManagementDeviceSortField.Name) return 1
    if (this.draftSortField === ManagementDeviceSortField.App) return 2
    if (this.draftSortField === ManagementDeviceSortField.User) return 3
    return 0
  }

  private toggleUser(userName: string): void {
    if (this.draftUsers.includes(userName)) {
      this.draftUsers = this.draftUsers.filter((value: string) => value !== userName)
    } else {
      this.draftUsers = this.draftUsers.concat(userName)
    }
  }

  private reset(): void {
    this.draftRecentDays = 0
    this.draftUsers = []
    this.draftSortField = ManagementDeviceSortField.LastActivity
    this.draftAscending = false
  }

  build() {
    Column() {
      Row() {
        Text($r('app.string.management_device_filter_title'))
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('sys.color.font_primary'))
          .layoutWeight(1)
      }
      .width('calc(100% - 84vp)')
      .padding({ left: 20, right: 64, top: 16, bottom: 8 })

      Scroll() {
        Column({ space: 12 }) {
          Column() {
            Row({ space: 16 }) {
              Text($r('app.string.management_device_filter_time'))
                .fontSize(16)
                .fontColor($r('sys.color.font_primary'))
                .layoutWeight(1)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              ManagementNativeSelect({
                options: [
                  this.getString('management_device_all_time'),
                  `7 ${this.getString('management_device_days')}`,
                  `30 ${this.getString('management_device_days')}`
                ],
                selectedIndex: this.recentIndex(),
                themeStyle: this.appUIState.themeStyle,
                nativeThemeAvailable: this.appUIState.nativeThemeAvailable,
                onSelect: (index: number) => {
                  this.draftRecentDays = index === 1 ? 7 : (index === 2 ? 30 : 0)
                }
              })
            }
            .width('calc(100% - 28vp)')
            .height(56)
            .padding({ left: 16, right: 12 })
            Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
            Row({ space: 16 }) {
              Text($r('app.string.management_device_filter_sort'))
                .fontSize(16)
                .fontColor($r('sys.color.font_primary'))
                .layoutWeight(1)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              ManagementNativeSelect({
                options: [
                  this.getString('management_device_sort_recent'),
                  this.getString('management_device_sort_name'),
                  this.getString('management_device_sort_app'),
                  this.getString('management_device_sort_user')
                ],
                selectedIndex: this.sortIndex(),
                themeStyle: this.appUIState.themeStyle,
                nativeThemeAvailable: this.appUIState.nativeThemeAvailable,
                onSelect: (index: number) => {
                  this.draftSortField = index === 1 ? ManagementDeviceSortField.Name :
                    (index === 2 ? ManagementDeviceSortField.App :
                      (index === 3 ? ManagementDeviceSortField.User : ManagementDeviceSortField.LastActivity))
                }
              })
            }
            .width('calc(100% - 28vp)')
            .height(56)
            .padding({ left: 16, right: 12 })
            Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
            Row({ space: 16 }) {
              Text($r('app.string.management_device_sort_ascending'))
                .fontSize(16)
                .fontColor($r('sys.color.font_primary'))
                .layoutWeight(1)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              Toggle({ type: ToggleType.Switch, isOn: this.draftAscending })
                .onChange((value: boolean) => this.draftAscending = value)
            }
            .width('calc(100% - 28vp)')
            .height(56)
            .padding({ left: 16, right: 12 })
          }
          .width('100%')
          .borderRadius(8)
          .clip(true)
          .backgroundColor(this.useNativeMaterial() ?
            Color.Transparent : AppThemeSurfaceResolver.contentGroupBackground())

          if (this.availableUsers.length > 0) {
            Column() {
              Row() {
                Text($r('app.string.management_device_filter_user'))
                  .fontSize(14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('sys.color.font_secondary'))
              }
              .width('calc(100% - 32vp)')
              .height(44)
              .padding({ left: 16, right: 16 })
              ForEach(this.availableUsers, (userName: string, index: number) => {
                if (index > 0) {
                  Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
                }
                Row({ space: 16 }) {
                  Text(userName)
                    .fontSize(16)
                    .fontColor($r('sys.color.font_primary'))
                    .layoutWeight(1)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                  Toggle({ type: ToggleType.Checkbox, isOn: this.draftUsers.includes(userName) })
                    .onChange(() => this.toggleUser(userName))
                }
                .width('calc(100% - 28vp)')
                .height(52)
                .padding({ left: 16, right: 12 })
              }, (userName: string) => userName)
            }
            .width('100%')
            .borderRadius(8)
            .clip(true)
            .backgroundColor(this.useNativeMaterial() ?
              Color.Transparent : AppThemeSurfaceResolver.contentGroupBackground())
          }
        }
        .width(this.useNativeMaterial() ? '100%' : 'calc(100% - 32vp)')
        .padding({
          left: this.useNativeMaterial() ? 0 : 16,
          right: this.useNativeMaterial() ? 0 : 16,
          top: 8,
          bottom: this.useNativeMaterial() ? 0 : 16
        })
      }
      .layoutWeight(1)
      .width('100%')
      .scrollBar(BarState.Auto)

      Row({ space: 12 }) {
        Button($r('app.string.management_filter_reset'))
          .layoutWeight(1)
          .height(44)
          .borderRadius(22)
          .fontColor($r('sys.color.font_primary'))
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
          .onClick(() => this.reset())
        Button($r('app.string.management_filter_apply'))
          .layoutWeight(1)
          .height(44)
          .borderRadius(22)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
          .onClick(() => this.onApply(this.draftRecentDays, this.draftUsers.slice(),
            this.draftSortField, this.draftAscending))
      }
      .width('calc(100% - 32vp)')
      .padding({ left: 16, right: 16, top: 10, bottom: Math.max(12, this.appUIState.safeBottom) })
      .backgroundColor(Color.Transparent)
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Start)
    .backgroundColor(Color.Transparent)
  }
}
