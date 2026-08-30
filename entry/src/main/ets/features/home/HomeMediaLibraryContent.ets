import { MediaProviderRegistry } from '../../media/MediaProviderRegistry'
import { MediaLibraryKind } from '../../media/catalog/MediaCatalogModels'
import { MusicLibraryPage } from '../music/MusicLibraryPage'
import { PhotoLibraryPage } from '../photo/PhotoLibraryPage'
import { VideoListPage } from '../videolist/VideoListPage'
import { MediaItem } from './hometab/HomeUIState'

@ComponentV2
export struct HomeMediaLibraryContent {
  @Require @Param mediaItem: MediaItem
  @Param useHomeContentWidth: boolean = false
  @Param contentBottomInset: number = 0

  private musicCapabilityEnabled(): boolean {
    return MediaProviderRegistry.instance().getActiveSession()?.capabilities.music !== false
  }

  private photoCapabilityEnabled(): boolean {
    return MediaProviderRegistry.instance().getActiveSession()?.capabilities.photos !== false
  }

  build() {
    if (this.mediaItem.libraryKind === MediaLibraryKind.Music) {
      if (this.musicCapabilityEnabled()) {
        MusicLibraryPage({
          fromHome: true,
          mediaItem: this.mediaItem
        })
      } else {
        Stack()
      }
    } else if (this.mediaItem.libraryKind === MediaLibraryKind.Photos) {
      if (this.photoCapabilityEnabled()) {
        PhotoLibraryPage({
          fromHome: true,
          mediaItem: this.mediaItem,
          contentBottomInset: this.contentBottomInset
        })
      } else {
        Stack()
      }
    } else {
      VideoListPage({
        fromHome: true,
        useHomeContentWidth: this.useHomeContentWidth,
        mediaItem: this.mediaItem
      })
    }
  }
}
