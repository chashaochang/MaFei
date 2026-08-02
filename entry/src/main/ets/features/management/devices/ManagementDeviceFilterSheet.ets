import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
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
  @Require @Param onDismiss: () => void
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
        Button() {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(18)
            .fontColor([$r('sys.color.icon_secondary')])
        }
        .width(40)
        .height(40)
        .backgroundColor(Color.Transparent)
        .accessibilityText($r('app.string.management_cancel'))
        .onClick(() => this.onDismiss())
      }
      .width('100%')
      .padding({ left: 20, right: 12, top: 12, bottom: 8 })

      Scroll() {
        Column({ space: 12 }) {
          Column() {
            Row({ space: 16 }) {
              Text($r('app.string.management_device_filter_time'))
                .fontSize(16)
                .fontColor($r('sys.color.font_primary'))
                .layoutWeight(1)
              Select([
                { value: this.getString('management_device_all_time') },
                { value: `7 ${this.getString('management_device_days')}` },
                { value: `30 ${this.getString('management_device_days')}` }
              ])
                .selected(this.recentIndex())
                .onSelect((index: number) => {
                  this.draftRecentDays = index === 1 ? 7 : (index === 2 ? 30 : 0)
                })
            }
            .width('100%')
            .height(56)
            .padding({ left: 16, right: 12 })
            Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
            Row({ space: 16 }) {
              Text($r('app.string.management_device_filter_sort'))
                .fontSize(16)
                .fontColor($r('sys.color.font_primary'))
                .layoutWeight(1)
              Select([
                { value: this.getString('management_device_sort_recent') },
                { value: this.getString('management_device_sort_name') },
                { value: this.getString('management_device_sort_app') },
                { value: this.getString('management_device_sort_user') }
              ])
                .selected(this.sortIndex())
                .onSelect((index: number) => {
                  this.draftSortField = index === 1 ? ManagementDeviceSortField.Name :
                    (index === 2 ? ManagementDeviceSortField.App :
                      (index === 3 ? ManagementDeviceSortField.User : ManagementDeviceSortField.LastActivity))
                })
            }
            .width('100%')
            .height(56)
            .padding({ left: 16, right: 12 })
            Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
            Row({ space: 16 }) {
              Text($r('app.string.management_device_sort_ascending'))
                .fontSize(16)
                .fontColor($r('sys.color.font_primary'))
                .layoutWeight(1)
              Toggle({ type: ToggleType.Switch, isOn: this.draftAscending })
                .onChange((value: boolean) => this.draftAscending = value)
            }
            .width('100%')
            .height(56)
            .padding({ left: 16, right: 12 })
          }
          .width('100%')
          .borderRadius(8)
          .clip(true)
          .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())

          if (this.availableUsers.length > 0) {
            Column() {
              Row() {
                Text($r('app.string.management_device_filter_user'))
                  .fontSize(14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('sys.color.font_secondary'))
              }
              .width('100%')
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
                .width('100%')
                .height(52)
                .padding({ left: 16, right: 12 })
              }, (userName: string) => userName)
            }
            .width('100%')
            .borderRadius(8)
            .clip(true)
            .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
          }
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 8, bottom: 16 })
      }
      .layoutWeight(1)
      .width('100%')
      .scrollBar(BarState.Auto)

      Row({ space: 12 }) {
        Button($r('app.string.management_filter_reset'))
          .layoutWeight(1)
          .height(44)
          .fontColor($r('sys.color.font_primary'))
          .backgroundColor(Color.Transparent)
          .onClick(() => this.reset())
        Button($r('app.string.management_filter_apply'))
          .layoutWeight(1)
          .height(44)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(Color.Transparent)
          .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive))
          .onClick(() => this.onApply(this.draftRecentDays, this.draftUsers.slice(),
            this.draftSortField, this.draftAscending))
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 10, bottom: Math.max(12, this.appUIState.safeBottom) })
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Transparent)
  }
}
