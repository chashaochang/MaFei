import { ThemeStyle } from '../../entity/ThemeStyle';
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ObjectUtil } from '../../utils/ObjectUtil';
import { ManagementNativeSelect } from './ManagementNativeSelect';
import { ManagementUserDetailUIState } from './ManagementUserDetailUIState';
import {
  ManagementAccessScheduleItem,
  ManagementParentalDraft,
  ManagementParentalRatingOption
} from './ManagementUserModels';

const UNRATED_ITEMS: string[] = [
  'Movie', 'Trailer', 'Series', 'Music', 'Book',
  'LiveTvChannel', 'LiveTvProgram', 'ChannelContent', 'Other'
]

const SCHEDULE_DAYS: string[] = [
  'Everyday', 'Weekday', 'Weekend', 'Sunday', 'Monday',
  'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'
]

enum ManagementParentalGroupKind {
  Rating,
  Unrated,
  Tags,
  Schedules
}

@ObservedV2
class ManagementParentalTagDialogState {
  @Trace draft: string = ''
  @Trace blocked: boolean = false
  onCancel: () => void = () => {
  }
  onAdd: (value: string, blocked: boolean) => void = () => {
  }
}

@ComponentV2
struct ManagementParentalGroupHeader {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor

  build() {
    Column({ space: 4 }) {
      Text(this.title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryColor)
        .width('100%')
      Text(this.description)
        .fontSize(12)
        .fontColor(this.secondaryColor)
        .width('100%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementParentalTagList {
  @Require @Param title: ResourceStr
  @Require @Param tags: string[]
  @Require @Param blocked: boolean
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeMaterial: boolean
  @Event onAdd: (blocked: boolean) => void = () => {
  }
  @Event onRemove: (tag: string, blocked: boolean) => void = () => {
  }

  build() {
    Column({ space: 8 }) {
      Row({ space: 10 }) {
        Text(this.title)
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryColor)
          .layoutWeight(1)
        Button() {
          Row({ space: 5 }) {
            SymbolGlyph($r('sys.symbol.plus'))
              .fontSize(14)
              .fontColor([$r('app.color.color_main')])
            Text($r('app.string.management_user_parental_add_tag'))
              .fontSize(12)
              .fontColor($r('app.color.color_main'))
          }
        }
        .height(36)
        .padding({ left: 10, right: 10 })
        .borderRadius(this.nativeMaterial ? 18 : 8)
        .backgroundColor(Color.Transparent)
        .onClick(() => this.onAdd(this.blocked))
      }
      .width('100%')
      if (this.tags.length === 0) {
        Text($r('app.string.management_user_parental_no_tags'))
          .fontSize(12)
          .fontColor(this.secondaryColor)
          .width('100%')
      } else {
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(this.tags, (tag: string) => {
            Button() {
              Row({ space: 6 }) {
                Text(tag)
                  .fontSize(12)
                  .fontColor(this.blocked ? $r('app.color.main_red_color') : this.primaryColor)
                  .maxLines(1)
                SymbolGlyph($r('sys.symbol.xmark'))
                  .fontSize(11)
                  .fontColor([this.secondaryColor])
              }
            }
            .height(34)
            .padding({ left: 10, right: 10 })
            .margin({ right: 8, bottom: 8 })
            .borderRadius(this.nativeMaterial ? 17 : 8)
            .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
            .onClick(() => this.onRemove(tag, this.blocked))
          }, (tag: string) => tag)
        }
        .width('100%')
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementParentalScheduleRow {
  @Require @Param schedule: ManagementAccessScheduleItem
  @Require @Param index: number
  @Require @Param dayOptions: ResourceStr[]
  @Require @Param selectedDayIndex: number
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Require @Param fieldColor: ResourceColor
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param nativeMaterial: boolean
  @Event onDayChange: (index: number, dayIndex: number) => void = () => {
  }
  @Event onRemove: (index: number) => void = () => {
  }
  @Event onStartHourChange: (index: number, value: number) => void = () => {
  }
  @Event onEndHourChange: (index: number, value: number) => void = () => {
  }

  private normalizedHour(text: string): number {
    return Math.max(0, Math.min(24, Number(text) || 0))
  }

  build() {
    Column({ space: 10 }) {
      Row({ space: 10 }) {
        ManagementNativeSelect({
          options: this.dayOptions,
          selectedIndex: this.selectedDayIndex,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          selectorWidth: 148,
          valueTextAlign: TextAlign.Start,
          menuPlacement: Placement.BottomLeft,
          onSelect: (selectedIndex: number) => this.onDayChange(this.index, selectedIndex)
        })
        Blank()
        Button() {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(16)
            .fontColor([$r('app.color.main_red_color')])
        }
        .width(40)
        .height(40)
        .borderRadius(this.nativeMaterial ? 20 : 8)
        .backgroundColor(Color.Transparent)
        .accessibilityText($r('app.string.management_user_parental_remove_schedule'))
        .onClick(() => this.onRemove(this.index))
      }
      .width('100%')
      Row({ space: 12 }) {
        Column({ space: 4 }) {
          Text($r('app.string.management_user_parental_start_hour'))
            .fontSize(11)
            .fontColor(this.secondaryColor)
            .width('100%')
          TextInput({ text: this.schedule.startHour.toString() })
            .type(InputType.Number)
            .height(40)
            .fontSize(14)
            .fontColor(this.primaryColor)
            .textAlign(TextAlign.End)
            .backgroundColor(this.fieldColor)
            .borderRadius(8)
            .onChange((text: string) => this.onStartHourChange(this.index, this.normalizedHour(text)))
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Column({ space: 4 }) {
          Text($r('app.string.management_user_parental_end_hour'))
            .fontSize(11)
            .fontColor(this.secondaryColor)
            .width('100%')
          TextInput({ text: this.schedule.endHour.toString() })
            .type(InputType.Number)
            .height(40)
            .fontSize(14)
            .fontColor(this.primaryColor)
            .textAlign(TextAlign.End)
            .backgroundColor(this.fieldColor)
            .borderRadius(8)
            .onChange((text: string) => this.onEndHourChange(this.index, this.normalizedHour(text)))
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
      }
      .width('100%')
    }
    .width('100%')
    .padding({ top: 10, bottom: 10 })
  }
}

@ComponentV2
struct ManagementParentalGroupSurface {
  @Require @Param kind: ManagementParentalGroupKind
  @Require @Param ui: ManagementUserDetailUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Event onChanged: () => void = () => {
  }
  @Event onOpenTagDialog: (blocked: boolean) => void = () => {
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private copyParental(): ManagementParentalDraft {
    const next = ObjectUtil.Assign({}, this.ui.parental) as ManagementParentalDraft
    next.blockedUnratedItems = this.ui.parental.blockedUnratedItems.slice()
    next.allowedTags = this.ui.parental.allowedTags.slice()
    next.blockedTags = this.ui.parental.blockedTags.slice()
    next.schedules = this.ui.parental.schedules.map((source: ManagementAccessScheduleItem) => {
      const item = new ManagementAccessScheduleItem()
      item.id = source.id
      item.dayOfWeek = source.dayOfWeek
      item.startHour = source.startHour
      item.endHour = source.endHour
      return item
    })
    return next
  }

  private commitParental(next: ManagementParentalDraft): void {
    this.ui.parental = next
    this.onChanged()
  }

  private unratedLabel(value: string): ResourceStr {
    if (value === 'Movie') return $r('app.string.management_user_unrated_movie')
    if (value === 'Trailer') return $r('app.string.management_user_unrated_trailer')
    if (value === 'Series') return $r('app.string.management_user_unrated_series')
    if (value === 'Music') return $r('app.string.management_user_unrated_music')
    if (value === 'Book') return $r('app.string.management_user_unrated_book')
    if (value === 'LiveTvChannel') return $r('app.string.management_user_unrated_live_tv_channel')
    if (value === 'LiveTvProgram') return $r('app.string.management_user_unrated_live_tv_program')
    if (value === 'ChannelContent') return $r('app.string.management_user_unrated_channel_content')
    return $r('app.string.management_user_unrated_other')
  }

  private dayLabel(value: string): ResourceStr {
    if (value === 'Weekday') return $r('app.string.management_user_day_weekday')
    if (value === 'Weekend') return $r('app.string.management_user_day_weekend')
    if (value === 'Sunday') return $r('app.string.management_user_day_sunday')
    if (value === 'Monday') return $r('app.string.management_user_day_monday')
    if (value === 'Tuesday') return $r('app.string.management_user_day_tuesday')
    if (value === 'Wednesday') return $r('app.string.management_user_day_wednesday')
    if (value === 'Thursday') return $r('app.string.management_user_day_thursday')
    if (value === 'Friday') return $r('app.string.management_user_day_friday')
    if (value === 'Saturday') return $r('app.string.management_user_day_saturday')
    return $r('app.string.management_user_day_everyday')
  }

  private ratingOptions(): ResourceStr[] {
    const options: ResourceStr[] = [$r('app.string.management_user_parental_rating_unrestricted')]
    this.ui.parentalRatings.forEach((option: ManagementParentalRatingOption) => options.push(option.name))
    return options
  }

  private ratingIndex(): number {
    if (this.ui.parental.maxParentalRating === null) {
      return 0
    }
    const index = this.ui.parentalRatings.findIndex((option: ManagementParentalRatingOption) =>
      option.value === this.ui.parental.maxParentalRating)
    return index >= 0 ? index + 1 : 0
  }

  private dayOptions(): ResourceStr[] {
    return SCHEDULE_DAYS.map((day: string) => this.dayLabel(day))
  }

  private hasTagConflict(): boolean {
    const allowed = this.ui.parental.allowedTags
      .map((tag: string) => tag.trim()).filter((tag: string) => tag.length > 0)
    const blocked = this.ui.parental.blockedTags
      .map((tag: string) => tag.trim()).filter((tag: string) => tag.length > 0)
    return allowed.some((tag: string) => blocked.includes(tag))
  }

  private hasInvalidSchedule(): boolean {
    return this.ui.parental.schedules.some((schedule: ManagementAccessScheduleItem) =>
      schedule.startHour < 0 || schedule.endHour > 24 || schedule.startHour >= schedule.endHour)
  }

  build() {
    Column({ space: this.kind === ManagementParentalGroupKind.Tags ? 18 : 8 }) {
      if (this.kind === ManagementParentalGroupKind.Rating) {
        ManagementParentalGroupHeader({
          title: $r('app.string.management_user_parental_rating'),
          description: $r('app.string.management_user_parental_rating_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        Row({ space: 12 }) {
          Text($r('app.string.management_user_parental_max_rating'))
            .fontSize(14)
            .fontColor(this.primaryTextColor())
            .layoutWeight(1)
          ManagementNativeSelect({
            options: this.ratingOptions(),
            selectedIndex: this.ratingIndex(),
            themeStyle: this.themeStyle,
            nativeThemeAvailable: this.nativeThemeAvailable,
            onSelect: (index: number) => {
              const next = this.copyParental()
              next.maxParentalRating = index === 0 ? null : this.ui.parentalRatings[index - 1].value
              this.commitParental(next)
            }
          })
        }
        .width('100%')
        .constraintSize({ minHeight: 54 })
      } else if (this.kind === ManagementParentalGroupKind.Unrated) {
        ManagementParentalGroupHeader({
          title: $r('app.string.management_user_parental_unrated'),
          description: $r('app.string.management_user_parental_unrated_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ForEach(UNRATED_ITEMS, (item: string) => {
          Row({ space: 12 }) {
            Toggle({
              type: ToggleType.Checkbox,
              isOn: this.ui.parental.blockedUnratedItems.includes(item)
            })
              .selectedColor($r('app.color.color_main'))
              .onChange((blocked: boolean) => {
                const next = this.copyParental()
                if (blocked && !next.blockedUnratedItems.includes(item)) {
                  next.blockedUnratedItems = next.blockedUnratedItems.concat(item)
                } else if (!blocked) {
                  next.blockedUnratedItems = next.blockedUnratedItems
                    .filter((value: string) => value !== item)
                }
                this.commitParental(next)
              })
            Text(this.unratedLabel(item))
              .fontSize(13)
              .fontColor(this.primaryTextColor())
              .layoutWeight(1)
          }
          .width('100%')
          .constraintSize({ minHeight: 48 })
        }, (item: string) => item)
      } else if (this.kind === ManagementParentalGroupKind.Tags) {
        ManagementParentalGroupHeader({
          title: $r('app.string.management_user_parental_tags'),
          description: $r('app.string.management_user_parental_tags_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        ManagementParentalTagList({
          title: $r('app.string.management_user_parental_allowed_tags'),
          tags: this.ui.parental.allowedTags,
          blocked: false,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          themeStyle: this.themeStyle,
          nativeMaterial: this.useNativeMaterial(),
          onAdd: this.onOpenTagDialog,
          onRemove: (tag: string, blocked: boolean) => {
            const next = this.copyParental()
            if (blocked) {
              next.blockedTags = next.blockedTags.filter((value: string) => value !== tag)
            } else {
              next.allowedTags = next.allowedTags.filter((value: string) => value !== tag)
            }
            this.commitParental(next)
          }
        })
        ManagementParentalTagList({
          title: $r('app.string.management_user_parental_blocked_tags'),
          tags: this.ui.parental.blockedTags,
          blocked: true,
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor(),
          themeStyle: this.themeStyle,
          nativeMaterial: this.useNativeMaterial(),
          onAdd: this.onOpenTagDialog,
          onRemove: (tag: string, blocked: boolean) => {
            const next = this.copyParental()
            if (blocked) {
              next.blockedTags = next.blockedTags.filter((value: string) => value !== tag)
            } else {
              next.allowedTags = next.allowedTags.filter((value: string) => value !== tag)
            }
            this.commitParental(next)
          }
        })
        if (this.hasTagConflict()) {
          Text($r('app.string.management_user_parental_conflicting_tags'))
            .fontSize(12)
            .fontColor($r('app.color.main_red_color'))
            .width('100%')
        }
      } else {
        ManagementParentalGroupHeader({
          title: $r('app.string.management_user_parental_schedule'),
          description: $r('app.string.management_user_parental_schedule_desc'),
          primaryColor: this.primaryTextColor(),
          secondaryColor: this.secondaryTextColor()
        })
        if (this.ui.profile.isAdministrator) {
          Text($r('app.string.management_user_parental_admin_schedule_hidden'))
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .width('100%')
            .padding({ top: 10, bottom: 10 })
        } else {
          ForEach(this.ui.parental.schedules,
            (schedule: ManagementAccessScheduleItem, index: number) => {
              ManagementParentalScheduleRow({
                schedule,
                index,
                dayOptions: this.dayOptions(),
                selectedDayIndex: Math.max(0, SCHEDULE_DAYS.indexOf(schedule.dayOfWeek)),
                primaryColor: this.primaryTextColor(),
                secondaryColor: this.secondaryTextColor(),
                fieldColor: AppThemeSurfaceResolver.interactionOverlay(this.themeStyle),
                themeStyle: this.themeStyle,
                nativeThemeAvailable: this.nativeThemeAvailable,
                nativeMaterial: this.useNativeMaterial(),
                onDayChange: (scheduleIndex: number, dayIndex: number) => {
                  if (dayIndex >= 0 && dayIndex < SCHEDULE_DAYS.length) {
                    const next = this.copyParental()
                    next.schedules[scheduleIndex].dayOfWeek = SCHEDULE_DAYS[dayIndex]
                    this.commitParental(next)
                  }
                },
                onRemove: (scheduleIndex: number) => {
                  const next = this.copyParental()
                  next.schedules.splice(scheduleIndex, 1)
                  this.commitParental(next)
                },
                onStartHourChange: (scheduleIndex: number, value: number) => {
                  const next = this.copyParental()
                  next.schedules[scheduleIndex].startHour = value
                  this.commitParental(next)
                },
                onEndHourChange: (scheduleIndex: number, value: number) => {
                  const next = this.copyParental()
                  next.schedules[scheduleIndex].endHour = value
                  this.commitParental(next)
                }
              })
              if (index < this.ui.parental.schedules.length - 1) {
                Divider().color(AppThemeSurfaceResolver.contentDivider(
                  this.themeStyle, this.nativeThemeAvailable))
              }
            }, (schedule: ManagementAccessScheduleItem, index: number) => `${schedule.id}-${index}`)
          if (this.hasInvalidSchedule()) {
            Text($r('app.string.management_user_parental_invalid_schedule'))
              .fontSize(12)
              .fontColor($r('app.color.main_red_color'))
              .width('100%')
          }
          Button() {
            Row({ space: 6 }) {
              SymbolGlyph($r('sys.symbol.plus'))
                .fontSize(15)
                .fontColor([$r('app.color.color_main')])
              Text($r('app.string.management_user_parental_add_schedule'))
                .fontSize(13)
                .fontColor($r('app.color.color_main'))
            }
          }
          .height(40)
          .padding({ left: 12, right: 12 })
          .borderRadius(this.useNativeMaterial() ? 20 : 8)
          .backgroundColor(Color.Transparent)
          .onClick(() => {
            const next = this.copyParental()
            next.schedules = next.schedules.concat(new ManagementAccessScheduleItem())
            this.commitParental(next)
          })
        }
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .clip(this.useNativeMaterial())
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
struct ManagementParentalTagDialog {
  @Require @Param state: ManagementParentalTagDialogState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column({ space: 16 }) {
      Text(this.state.blocked ?
        $r('app.string.management_user_parental_add_blocked_tag') :
        $r('app.string.management_user_parental_add_allowed_tag'))
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .width('100%')
      TextInput({
        text: this.state.draft,
        placeholder: $r('app.string.management_user_parental_tag_placeholder')
      })
        .height(44)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .borderRadius(8)
        .onChange((value: string) => {
          this.state.draft = value
        })
      Row({ space: 10 }) {
        Button($r('app.string.management_user_cancel'), { type: ButtonType.Normal })
          .height(40)
          .borderRadius(this.useNativeMaterial() ? 20 : 8)
          .layoutWeight(1)
          .fontColor(this.secondaryTextColor())
          .backgroundColor(Color.Transparent)
          .onClick(() => this.state.onCancel())
        Button($r('app.string.management_user_parental_add'), { type: ButtonType.Normal })
          .height(40)
          .layoutWeight(1)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .borderRadius(this.useNativeMaterial() ? 20 : 8)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
          .enabled(this.state.draft.trim().length > 0)
          .onClick(() => this.state.onAdd(this.state.draft, this.state.blocked))
      }
      .width('100%')
    }
    .width(320)
    .constraintSize({ maxWidth: '90%' })
    .padding(20)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(this.useNativeMaterial() ? 12 : 8)
    .clip(this.useNativeMaterial())
    .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, this.useNativeMaterial()))
  }
}

@ComponentV2
export struct ManagementUserParentalSection {
  @Require @Param ui: ManagementUserDetailUIState
  @Require @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
  }
  @Local dialogState: ManagementParentalTagDialogState = new ManagementParentalTagDialogState()
  private tagDialogId: number = -1

  private copyParental(): ManagementParentalDraft {
    const next = ObjectUtil.Assign({}, this.ui.parental) as ManagementParentalDraft
    next.blockedUnratedItems = this.ui.parental.blockedUnratedItems.slice()
    next.allowedTags = this.ui.parental.allowedTags.slice()
    next.blockedTags = this.ui.parental.blockedTags.slice()
    next.schedules = this.ui.parental.schedules.slice()
    return next
  }

  private commitParental(next: ManagementParentalDraft): void {
    this.ui.parental = next
    this.onChanged()
  }

  private closeTagDialog(): void {
    if (this.tagDialogId < 0) {
      return
    }
    try {
      this.getUIContext().getPromptAction().closeCustomDialog(this.tagDialogId)
    } catch (error) {
    }
    this.tagDialogId = -1
  }

  private addTag(value: string, blocked: boolean): void {
    const normalized = value.trim()
    if (!normalized) {
      return
    }
    const next = this.copyParental()
    if (blocked) {
      if (!next.blockedTags.includes(normalized)) {
        next.blockedTags = next.blockedTags.concat(normalized)
      }
    } else if (!next.allowedTags.includes(normalized)) {
      next.allowedTags = next.allowedTags.concat(normalized)
    }
    this.commitParental(next)
    this.closeTagDialog()
  }

  // CustomBuilderAdapter: management-parental-tag-dialog
  @Builder
  private tagDialogBuilder(state: ManagementParentalTagDialogState,
    themeStyle: ThemeStyle, nativeThemeAvailable: boolean) {
    ManagementParentalTagDialog({
      state,
      themeStyle,
      nativeThemeAvailable
    })
  }

  private openTagDialog(blocked: boolean): void {
    this.dialogState.blocked = blocked
    this.dialogState.draft = ''
    this.dialogState.onCancel = () => this.closeTagDialog()
    this.dialogState.onAdd = (value: string, isBlocked: boolean) => this.addTag(value, isBlocked)
    const dialogState = this.dialogState
    const themeStyle = this.themeStyle
    const nativeThemeAvailable = this.nativeThemeAvailable
    this.getUIContext().getPromptAction().openCustomDialog({
      builder: () => {
        this.tagDialogBuilder(dialogState, themeStyle, nativeThemeAvailable)
      },
      alignment: DialogAlignment.Center,
      autoCancel: true,
      backgroundColor: Color.Transparent
    }).then((dialogId: number) => {
      this.tagDialogId = dialogId
    }).catch(() => {
      this.tagDialogId = -1
    })
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      ManagementParentalGroupSurface({
        kind: ManagementParentalGroupKind.Rating,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementParentalGroupSurface({
        kind: ManagementParentalGroupKind.Unrated,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
      ManagementParentalGroupSurface({
        kind: ManagementParentalGroupKind.Tags,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged,
        onOpenTagDialog: (blocked: boolean) => this.openTagDialog(blocked)
      })
      ManagementParentalGroupSurface({
        kind: ManagementParentalGroupKind.Schedules,
        ui: this.ui,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onChanged: this.onChanged
      })
    }
    .width('100%')
  }
}
