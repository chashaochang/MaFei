import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { ManagementNativeSelect } from './ManagementNativeSelect'
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

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
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
      }
      .width('calc(100% - 84vp)')
      .padding({ left: 20, right: 64, top: 16, bottom: 8 })

      Column() {
        Row({ space: 16 }) {
          Text($r('app.string.management_user_filter_status'))
            .fontSize(16)
            .fontColor($r('sys.color.font_primary'))
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          ManagementNativeSelect({
            options: [
              this.getString('management_user_status_all'),
              this.getString('management_user_status_enabled'),
              this.getString('management_user_status_disabled')
            ],
            selectedIndex: this.statusIndex(),
            themeStyle: this.appUIState.themeStyle,
            nativeThemeAvailable: this.appUIState.nativeThemeAvailable,
            onSelect: (index: number) => {
              this.draftStatus = index === 1 ? ManagementUserStatusFilter.Enabled :
                (index === 2 ? ManagementUserStatusFilter.Disabled : ManagementUserStatusFilter.All)
            }
          })
        }
        .width('calc(100% - 28vp)')
        .height(56)
        .padding({ left: 16, right: 12 })
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
        Row({ space: 16 }) {
          Text($r('app.string.management_user_filter_visibility'))
            .fontSize(16)
            .fontColor($r('sys.color.font_primary'))
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          ManagementNativeSelect({
            options: [
              this.getString('management_user_visibility_all'),
              this.getString('management_user_visibility_visible'),
              this.getString('management_user_visibility_hidden')
            ],
            selectedIndex: this.visibilityIndex(),
            themeStyle: this.appUIState.themeStyle,
            nativeThemeAvailable: this.appUIState.nativeThemeAvailable,
            onSelect: (index: number) => {
              this.draftVisibility = index === 1 ? ManagementUserVisibilityFilter.Visible :
                (index === 2 ? ManagementUserVisibilityFilter.Hidden : ManagementUserVisibilityFilter.All)
            }
          })
        }
        .width('calc(100% - 28vp)')
        .height(56)
        .padding({ left: 16, right: 12 })
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
        Row({ space: 16 }) {
          Text($r('app.string.management_user_filter_sort'))
            .fontSize(16)
            .fontColor($r('sys.color.font_primary'))
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          ManagementNativeSelect({
            options: [
              this.getString('management_user_sort_name'),
              this.getString('management_user_sort_activity')
            ],
            selectedIndex: this.sortIndex(),
            themeStyle: this.appUIState.themeStyle,
            nativeThemeAvailable: this.appUIState.nativeThemeAvailable,
            onSelect: (index: number) => {
              this.draftSortField = index === 1 ? ManagementUserSortField.LastActivity :
                ManagementUserSortField.Name
            }
          })
        }
        .width('calc(100% - 28vp)')
        .height(56)
        .padding({ left: 16, right: 12 })
        Divider().margin({ left: 16 }).color($r('sys.color.comp_divider'))
        Row({ space: 16 }) {
          Text($r('app.string.management_user_sort_ascending'))
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
      .width(this.useNativeMaterial() ? '100%' : 'calc(100% - 32vp)')
      .borderRadius(8)
      .clip(true)
      .backgroundColor(this.useNativeMaterial() ?
        Color.Transparent : AppThemeSurfaceResolver.contentGroupBackground())
      .margin({
        left: this.useNativeMaterial() ? 0 : 16,
        right: this.useNativeMaterial() ? 0 : 16,
        top: 8
      })

      Blank().layoutWeight(1)

      Row({ space: 12 }) {
        Button($r('app.string.management_filter_reset'))
          .layoutWeight(1)
          .height(44)
          .borderRadius(22)
          .fontColor($r('sys.color.font_primary'))
          .backgroundColor(Color.Transparent)
          .systemMaterial(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) : undefined)
          .onClick(() => this.reset())
        Button($r('app.string.management_filter_apply'))
          .layoutWeight(1)
          .height(44)
          .borderRadius(22)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(Color.Transparent)
          .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive))
          .onClick(() => this.apply())
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
