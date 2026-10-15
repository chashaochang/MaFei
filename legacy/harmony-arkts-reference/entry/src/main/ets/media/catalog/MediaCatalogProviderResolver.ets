import { MediaProviderKind } from '../MediaProviderKind'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaCatalogProvider } from './MediaCatalogProvider'

export interface MediaProviderSessionSource {
  currentSession(): MediaProviderSession | undefined
}

export interface MediaCatalogProviderFactory {
  readonly provider: MediaProviderKind
  create(session: MediaProviderSession): MediaCatalogProvider
}

export class MediaCatalogProviderResolver {
  private sessionSource: MediaProviderSessionSource
  private factories: MediaCatalogProviderFactory[] = []

  constructor(sessionSource: MediaProviderSessionSource, factories: MediaCatalogProviderFactory[] = []) {
    this.sessionSource = sessionSource
    for (const factory of factories) {
      this.register(factory)
    }
  }

  register(factory: MediaCatalogProviderFactory): void {
    const index = this.factories.findIndex((item: MediaCatalogProviderFactory): boolean =>
      item.provider === factory.provider)
    if (index >= 0) {
      this.factories[index] = factory
      return
    }
    this.factories.push(factory)
  }

  resolve(): MediaCatalogProvider {
    const session = this.sessionSource.currentSession()
    if (!session) {
      throw new Error('No active media provider session')
    }
    const factory = this.factories.find((item: MediaCatalogProviderFactory): boolean =>
      item.provider === session.provider)
    if (!factory) {
      throw new Error(`No catalog provider registered for ${session.provider}`)
    }
    return factory.create(session)
  }
}
