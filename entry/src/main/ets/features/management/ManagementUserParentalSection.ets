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

@ComponentV2
export struct ManagementUserParentalSection {
  @Require
  @Param ui: ManagementUserDetailUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onChanged: () => void = () => {
  }
  @Local tagDraft: string = ''
  @Local addingBlockedTag: boolean = false
  private tagDialogId: number = -1

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

  private openTagDialog(blocked: boolean): void {
    this.addingBlockedTag = blocked
    this.tagDraft = ''
    this.getUIContext().getPromptAction().openCustomDialog({
      builder: () => this.tagDialogSurface(),
      alignment: DialogAlignment.Center,
      autoCancel: true,
      backgroundColor: Color.Transparent
    }).then((dialogId: number) => {
      this.tagDialogId = dialogId
    }).catch(() => {
      this.tagDialogId = -1
    })
  }

  private addTag(): void {
    const value = this.tagDraft.trim()
    if (!value) {
      return
    }
    const next = this.copyParental()
    if (this.addingBlockedTag) {
      if (!next.blockedTags.includes(value)) {
        next.blockedTags = next.blockedTags.concat(value)
      }
    } else if (!next.allowedTags.includes(value)) {
      next.allowedTags = next.allowedTags.concat(value)
    }
    this.commitParental(next)
    this.closeTagDialog()
  }

  @Builder
  private groupHeader(title: ResourceStr, description: ResourceStr) {
    Column({ space: 4 }) {
      Text(title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .width('100%')
      Text(description)
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
        .width('100%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private ratingContent() {
    Column({ space: 12 }) {
      this.groupHeader($r('app.string.management_user_parental_rating'),
        $r('app.string.management_user_parental_rating_desc'))
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
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private ratingGroup() {
    Column() { this.ratingContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuRatingGroup() {
    Column() { this.ratingContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private ratingSurface() {
    if (this.useNativeMaterial()) {
      this.ratingGroup()
    } else {
      this.feiniuRatingGroup()
    }
  }

  @Builder
  private unratedContent() {
    Column() {
      this.groupHeader($r('app.string.management_user_parental_unrated'),
        $r('app.string.management_user_parental_unrated_desc'))
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
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private unratedGroup() {
    Column() { this.unratedContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuUnratedGroup() {
    Column() { this.unratedContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private unratedSurface() {
    if (this.useNativeMaterial()) {
      this.unratedGroup()
    } else {
      this.feiniuUnratedGroup()
    }
  }

  @Builder
  private tagChip(tag: string, blocked: boolean) {
    Button() {
      Row({ space: 6 }) {
        Text(tag)
          .fontSize(12)
          .fontColor(blocked ? $r('app.color.main_red_color') : this.primaryTextColor())
          .maxLines(1)
        SymbolGlyph($r('sys.symbol.xmark'))
          .fontSize(11)
          .fontColor([this.secondaryTextColor()])
      }
    }
    .height(34)
    .padding({ left: 10, right: 10 })
    .margin({ right: 8, bottom: 8 })
    .borderRadius(this.useNativeMaterial() ? 17 : 8)
    .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
    .onClick(() => {
      const next = this.copyParental()
      if (blocked) {
        next.blockedTags = next.blockedTags.filter((value: string) => value !== tag)
      } else {
        next.allowedTags = next.allowedTags.filter((value: string) => value !== tag)
      }
      this.commitParental(next)
    })
  }

  @Builder
  private tagList(title: ResourceStr, tags: string[], blocked: boolean) {
    Column({ space: 8 }) {
      Row({ space: 10 }) {
        Text(title)
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
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
        .borderRadius(this.useNativeMaterial() ? 18 : 8)
        .backgroundColor(Color.Transparent)
        .onClick(() => this.openTagDialog(blocked))
      }
      .width('100%')
      if (tags.length === 0) {
        Text($r('app.string.management_user_parental_no_tags'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .width('100%')
      } else {
        Flex({ wrap: FlexWrap.Wrap }) {
          ForEach(tags, (tag: string) => this.tagChip(tag, blocked), (tag: string) => tag)
        }
        .width('100%')
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private tagsContent() {
    Column({ space: 18 }) {
      this.groupHeader($r('app.string.management_user_parental_tags'),
        $r('app.string.management_user_parental_tags_desc'))
      this.tagList($r('app.string.management_user_parental_allowed_tags'),
        this.ui.parental.allowedTags, false)
      this.tagList($r('app.string.management_user_parental_blocked_tags'),
        this.ui.parental.blockedTags, true)
      if (this.hasTagConflict()) {
        Text($r('app.string.management_user_parental_conflicting_tags'))
          .fontSize(12)
          .fontColor($r('app.color.main_red_color'))
          .width('100%')
      }
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private tagsGroup() {
    Column() { this.tagsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuTagsGroup() {
    Column() { this.tagsContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private tagsSurface() {
    if (this.useNativeMaterial()) {
      this.tagsGroup()
    } else {
      this.feiniuTagsGroup()
    }
  }

  @Builder
  private hourInput(label: ResourceStr, value: number, onChange: (value: number) => void) {
    Column({ space: 4 }) {
      Text(label)
        .fontSize(11)
        .fontColor(this.secondaryTextColor())
        .width('100%')
      TextInput({ text: value.toString() })
        .type(InputType.Number)
        .height(40)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.End)
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .borderRadius(8)
        .onChange((text: string) => onChange(Math.max(0, Math.min(24, Number(text) || 0))))
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private scheduleRow(schedule: ManagementAccessScheduleItem, index: number) {
    Column({ space: 10 }) {
      Row({ space: 10 }) {
        ManagementNativeSelect({
          options: this.dayOptions(),
          selectedIndex: Math.max(0, SCHEDULE_DAYS.indexOf(schedule.dayOfWeek)),
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          selectorWidth: 148,
          valueTextAlign: TextAlign.Start,
          menuPlacement: Placement.BottomLeft,
          onSelect: (selectedIndex: number) => {
            if (selectedIndex >= 0 && selectedIndex < SCHEDULE_DAYS.length) {
              const next = this.copyParental()
              next.schedules[index].dayOfWeek = SCHEDULE_DAYS[selectedIndex]
              this.commitParental(next)
            }
          }
        })
        Blank()
        Button() {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(16)
            .fontColor([$r('app.color.main_red_color')])
        }
        .width(40)
        .height(40)
        .borderRadius(this.useNativeMaterial() ? 20 : 8)
        .backgroundColor(Color.Transparent)
        .accessibilityText($r('app.string.management_user_parental_remove_schedule'))
        .onClick(() => {
          const next = this.copyParental()
          next.schedules.splice(index, 1)
          this.commitParental(next)
        })
      }
      .width('100%')
      Row({ space: 12 }) {
        this.hourInput($r('app.string.management_user_parental_start_hour'), schedule.startHour,
          (value: number) => {
            const next = this.copyParental()
            next.schedules[index].startHour = value
            this.commitParental(next)
          })
        this.hourInput($r('app.string.management_user_parental_end_hour'), schedule.endHour,
          (value: number) => {
            const next = this.copyParental()
            next.schedules[index].endHour = value
            this.commitParental(next)
          })
      }
      .width('100%')
    }
    .width('100%')
    .padding({ top: 10, bottom: 10 })
  }

  @Builder
  private schedulesContent() {
    Column({ space: 8 }) {
      this.groupHeader($r('app.string.management_user_parental_schedule'),
        $r('app.string.management_user_parental_schedule_desc'))
      if (this.ui.profile.isAdministrator) {
        Text($r('app.string.management_user_parental_admin_schedule_hidden'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .width('100%')
          .padding({ top: 10, bottom: 10 })
      } else {
        ForEach(this.ui.parental.schedules,
          (schedule: ManagementAccessScheduleItem, index: number) => {
            this.scheduleRow(schedule, index)
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
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private schedulesGroup() {
    Column() { this.schedulesContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuSchedulesGroup() {
    Column() { this.schedulesContent() }
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private schedulesSurface() {
    if (this.useNativeMaterial()) {
      this.schedulesGroup()
    } else {
      this.feiniuSchedulesGroup()
    }
  }

  @Builder
  private tagDialogContent() {
    Column({ space: 16 }) {
      Text(this.addingBlockedTag ?
        $r('app.string.management_user_parental_add_blocked_tag') :
        $r('app.string.management_user_parental_add_allowed_tag'))
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .width('100%')
      TextInput({
        text: this.tagDraft,
        placeholder: $r('app.string.management_user_parental_tag_placeholder')
      })
        .height(44)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.themeStyle))
        .borderRadius(8)
        .onChange((value: string) => {
          this.tagDraft = value
        })
      Row({ space: 10 }) {
        Button($r('app.string.management_user_cancel'), { type: ButtonType.Normal })
          .height(40)
          .borderRadius(this.useNativeMaterial() ? 20 : 8)
          .layoutWeight(1)
          .fontColor(this.secondaryTextColor())
          .backgroundColor(Color.Transparent)
          .onClick(() => this.closeTagDialog())
        Button($r('app.string.management_user_parental_add'), { type: ButtonType.Normal })
          .height(40)
          .layoutWeight(1)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor($r('app.color.color_main'))
          .borderRadius(this.useNativeMaterial() ? 20 : 8)
          .enabled(this.tagDraft.trim().length > 0)
          .onClick(() => this.addTag())
      }
      .width('100%')
    }
    .width('100%')
    .padding(20)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private tagDialog() {
    Column() { this.tagDialogContent() }
      .width(320)
      .constraintSize({ maxWidth: '90%' })
      .borderRadius(12)
      .clip(true)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))
  }

  @Builder
  private feiniuTagDialog() {
    Column() { this.tagDialogContent() }
      .width(320)
      .constraintSize({ maxWidth: '90%' })
      .borderRadius(8)
      .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  private tagDialogSurface() {
    if (this.useNativeMaterial()) {
      this.tagDialog()
    } else {
      this.feiniuTagDialog()
    }
  }

  build() {
    Column({ space: AppThemeTokens.groupSpacing(this.themeStyle) }) {
      this.ratingSurface()
      this.unratedSurface()
      this.tagsSurface()
      this.schedulesSurface()
    }
    .width('100%')
  }
}
