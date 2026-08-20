import {
  registerMediaCatalogProviderFactory
} from '../../../media/MediaCatalogAccess'
import {
  MediaCatalogProviderFactory
} from '../../../media/catalog/MediaCatalogProviderResolver'
import { MediaCatalogProvider } from '../../../media/catalog/MediaCatalogProvider'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import {
  FeiniuVideoSessionContextRegistry
} from '../account/FeiniuVideoSessionContextRegistry'
import { FeiniuVideoCatalogProvider } from './FeiniuVideoCatalogProvider'

export class FeiniuVideoCatalogProviderFactory implements MediaCatalogProviderFactory {
  readonly provider: MediaProviderKind = MediaProviderKind.FeiniuVideo
  private readonly contexts: FeiniuVideoSessionContextRegistry

  constructor(contexts: FeiniuVideoSessionContextRegistry =
    FeiniuVideoSessionContextRegistry.instance()) {
    this.contexts = contexts
  }

  create(session: MediaProviderSession): MediaCatalogProvider {
    const context = this.contexts.get(session)
    if (!context) {
      throw new Error(`No authenticated Feiniu Video context for ${session.accountScope}`)
    }
    return FeiniuVideoCatalogProvider.fromClient(session, context.apiClient, context)
  }
}

export function registerFeiniuVideoCatalogProvider(
  contexts: FeiniuVideoSessionContextRegistry = FeiniuVideoSessionContextRegistry.instance()
): void {
  registerMediaCatalogProviderFactory(new FeiniuVideoCatalogProviderFactory(contexts))
}
