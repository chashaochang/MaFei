import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import {
  ManagementUserSortField,
  ManagementUserStatusFilter,
  ManagementUserVisibilityFilter
} from './ManagementUserModels'
import { ManagementUserQueryOptions } from './ManagementUserQuery'

@ComponentV2
export struct ManagementUserFilterSheet {
  @Require @Param initialStatus: ManagementUserStatusFilter
  @Require @Param initialVisibility: ManagementUserVisibilityFilter
  @Require @Param initialSortField: ManagementUserSortField
  @Require @Param initialAscending: boolean
  @Require @Param onApply: (options: ManagementUserQueryOptions) => void
  @Require @Param onDismiss: () => void
  @Local draftStatus: ManagementUserStatusFilter = ManagementUserStatusFilter.All
  @Local draftVisibility: ManagementUserVisibilityFilter = ManagementUserVisibilityFilter.All
  @Local draftSortField: ManagementUserSortField = ManagementUserSortField.Name
  @Local draftAscending: boolean = true
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  aboutToAppear(): void {
    this.draftStatus = this.initialStatus
    this.draftVisibility = this.initialVisibility
    this.draftSortField = this.initialSortField
    this.draftAscending = this.initialAscending
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private statusIndex(): number {
    return this.draftStatus === ManagementUserStatusFilter.Enabled ? 1 :
      (this.draftStatus === ManagementUserStatusFilter.Disabled ? 2 : 0)
  }

  private visibilityIndex(): number {
    return this.draftVisibility === ManagementUserVisibilityFilter.Visible ? 1 :
      (this.draftVisibility === ManagementUserVisibilityFilter.Hidden ? 2 : 0)
  }

  private sortIndex(): number {
    return this.draftSortField === ManagementUserSortField.LastActivity ? 1 : 0
  }

  private reset(): void {
    this.draftStatus = ManagementUserStatusFilter.All
    this.draftVisibility = ManagementUserVisibilityFilter.All
    this.draftSortField = ManagementUserSortField.Name
    this.draftAscending = true
  }

  private apply(): void {
    const options = new ManagementUserQueryOptions()
    options.status = this.draftStatus
    options.visibility = this.draftVisibility
    options.sortField = this.draftSortField
    options.ascending = this.draftAscending
    this.onApply(options)
  }

  build() {
    Column() {
      Row() {
        Text($r('app.string.management_user_filter_title'))
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
        .accessibilityText($r('app.string.management_user_cancel'))
        .onClick(() => this.onDismiss())
      }
      .width('100%')
      .padding({ left: 20, right: 12, top: 12, bottom: 8 })

      Column() {
        Row({ space: 16 }) {
          Text($r('app.string.management_user_filter_status'))
            .fontSize(16)
            .fontColor($r('sys.color.font_primary'))
            .layoutWeight(1)
          Select([
            { value: this.getString('management_user_status_all') },
            { value: this.getString('management_user_status_enabled') },
            { value: this.getString('management_user_status_disabled') }
          ])
            .selected(this.statusIndex())
            .onSelect((index: number) => {
              this.draftStatus = index === 1 ? ManagementUserStatusFilter.Enabled :
                (index === 2 ? ManagementUserStatusFilter.Disabled : ManagementUserStatusFilter.All)
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: 16, right: 12 })
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
        Row({ space: 16 }) {
          Text($r('app.string.management_user_filter_visibility'))
            .fontSize(16)
            .fontColor($r('sys.color.font_primary'))
            .layoutWeight(1)
          Select([
            { value: this.getString('management_user_visibility_all') },
            { value: this.getString('management_user_visibility_visible') },
            { value: this.getString('management_user_visibility_hidden') }
          ])
            .selected(this.visibilityIndex())
            .onSelect((index: number) => {
              this.draftVisibility = index === 1 ? ManagementUserVisibilityFilter.Visible :
                (index === 2 ? ManagementUserVisibilityFilter.Hidden : ManagementUserVisibilityFilter.All)
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: 16, right: 12 })
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
        Row({ space: 16 }) {
          Text($r('app.string.management_user_filter_sort'))
            .fontSize(16)
            .fontColor($r('sys.color.font_primary'))
            .layoutWeight(1)
          Select([
            { value: this.getString('management_user_sort_name') },
            { value: this.getString('management_user_sort_activity') }
          ])
            .selected(this.sortIndex())
            .onSelect((index: number) => {
              this.draftSortField = index === 1 ? ManagementUserSortField.LastActivity :
                ManagementUserSortField.Name
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: 16, right: 12 })
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
        Row({ space: 16 }) {
          Text($r('app.string.management_user_sort_ascending'))
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
      .margin({ left: 16, right: 16, top: 8 })

      Blank().layoutWeight(1)

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
          .onClick(() => this.apply())
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
