import { FeiniuVideoDetailPresentationAdapter } from '../../feiniuvideo/presentation/FeiniuVideoDetailPresentationAdapter'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { JellyfinVideoDetailPresentationAdapter } from '../../../media/jellyfin/JellyfinVideoDetailPresentationAdapter'
import { VideoDetailPresentationAdapter } from '../../../media/presentation/VideoDetailPresentationModels'

export class VideoDetailPresentationAdapterRegistry {
  private static shared?: VideoDetailPresentationAdapterRegistry
  private adapters: Map<MediaProviderKind, VideoDetailPresentationAdapter> = new Map()

  private constructor() {
    this.register(new JellyfinVideoDetailPresentationAdapter())
    this.register(new FeiniuVideoDetailPresentationAdapter())
  }

  static instance(): VideoDetailPresentationAdapterRegistry {
    if (!VideoDetailPresentationAdapterRegistry.shared) {
      VideoDetailPresentationAdapterRegistry.shared = new VideoDetailPresentationAdapterRegistry()
    }
    return VideoDetailPresentationAdapterRegistry.shared
  }

  register(adapter: VideoDetailPresentationAdapter): void {
    this.adapters.set(adapter.provider, adapter)
  }

  resolve(provider: MediaProviderKind): VideoDetailPresentationAdapter {
    const adapter = this.adapters.get(provider)
    if (!adapter) throw new Error(`Video detail presentation adapter is not registered: ${provider}`)
    return adapter
  }
}
