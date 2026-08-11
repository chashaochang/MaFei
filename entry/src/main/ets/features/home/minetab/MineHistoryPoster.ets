import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { MineHistoryItem, MineHistoryMediaType } from './MineHistoryPlanner'

@ComponentV2
export struct MineHistoryPoster {
  @Require @Param item: MineHistoryItem
  @Require @Param nativeSurface: boolean

  private titleText(): ResourceStr {
    if (this.item.name.length === 0) {
      return $r('app.string.mine_history_unnamed')
    }
    if (this.item.mediaType === MineHistoryMediaType.Episode && this.item.episodeNumber !== undefined) {
      if (this.item.seriesHasMultipleSeasons && this.item.seasonNumber !== undefined) {
        return $r('app.string.mine_history_episode_title_season', this.item.name,
          this.item.seasonNumber, this.item.episodeNumber)
      }
      return $r('app.string.mine_history_episode_title_single', this.item.name, this.item.episodeNumber)
    }
    return this.item.name
  }

  private badgeText(): ResourceStr {
    if (this.item.mediaType === MineHistoryMediaType.Movie) {
      return this.item.communityRating === undefined ? '' : this.item.communityRating.toFixed(1)
    }
    if (this.item.latestEpisodeNumber === undefined) {
      return ''
    }
    if (this.item.seriesHasMultipleSeasons && this.item.latestSeasonNumber !== undefined) {
      return $r('app.string.mine_history_updated_to_season_episode',
        this.item.latestSeasonNumber, this.item.latestEpisodeNumber)
    }
    return $r('app.string.mine_history_updated_to_episode', this.item.latestEpisodeNumber)
  }

  private hasBadge(): boolean {
    if (this.item.mediaType === MineHistoryMediaType.Movie) {
      return this.item.communityRating !== undefined
    }
    return this.item.latestEpisodeNumber !== undefined
  }

  private hasProgress(): boolean {
    return !this.item.played && this.item.percent > 0
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  build() {
    Column({ space: 8 }) {
      Stack({ alignContent: Alignment.BottomEnd }) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.item.image,
            objectFit: ImageFit.Cover,
            border: { radius: 10 },
            placeholderSrc: $r('app.media.placeholder_video'),
            placeholderObjectFit: ImageFit.CENTER
          }
        })
          .width('100%')
          .height('100%')
          .borderRadius(10)
          .backgroundColor(this.nativeSurface ?
            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
        if (this.hasBadge()) {
          Text(this.badgeText())
            .fontSize(9)
            .fontColor(Color.White)
            .fontWeight(FontWeight.Medium)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .constraintSize({ maxWidth: 'calc(100% - 16vp)' })
            .padding({ left: 4, right: 4, top: 2, bottom: 2 })
            .margin({
              right: 4,
              bottom: this.hasProgress() ? 7 : 4
            })
            .backgroundColor($r('app.color.media_badge_background'))
            .borderRadius(5)
        }
        if (this.hasProgress()) {
          Progress({ value: this.item.percent, total: 100, type: ProgressType.Linear })
            .width('100%')
            .height(3)
            .color(this.nativeSurface ? $r('sys.color.icon_primary') : $r('app.color.color_main'))
            .backgroundColor($r('app.color.media_progress_track_on_image'))
            .style({
              strokeWidth: 3,
              strokeRadius: 1.5,
              enableSmoothEffect: false,
              enableScanEffect: false
            })
        }
      }
      .width('100%')
      .aspectRatio(2 / 3)
      .borderRadius(10)
      .clip(true)

      Text(this.titleText())
        .fontSize(12)
        .lineHeight(16)
        .fontColor(this.primaryTextColor())
        .width('100%')
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
