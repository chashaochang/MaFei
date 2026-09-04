import { CacheStrategy, HeaderOptions, ImageKnifeOption } from '@ohos/imageknifepro'
import { MediaImageRef, MediaRequestHeader } from './MediaCatalogModels'

export function mediaImageRequestFingerprint(image: MediaImageRef | undefined,
  fallbackSource: string | Resource = ''): string {
  const source = image && image.url.length > 0 ? image.url :
    (typeof fallbackSource === 'string' ? fallbackSource.trim() : 'resource')
  const headers = image ? image.headers.map((header: MediaRequestHeader): string => {
    return `${header.name.length}:${header.name}:${header.value.length}:${header.value}`
  }).sort().join('|') : ''
  return `${source.length}:${source}|${image?.cacheKey || ''}|${headers}`
}

export function createMediaImageKnifeOption(image: MediaImageRef | undefined,
  fallbackSource: string | Resource = ''): ImageKnifeOption {
  const option = new ImageKnifeOption()
  option.loadSrc = image && image.url.length > 0 ? image.url : fallbackSource
  if (image) {
    option.headerOption = image.headers.map((header: MediaRequestHeader): HeaderOptions => {
      return {
        key: header.name,
        value: header.value
      }
    })
    option.signature = image.cacheKey
  }
  return option
}

export function createMediaImageNetworkFallbackOption(image: MediaImageRef | undefined,
  fallbackSource: string | Resource, requestFingerprint: string, attempt: number, nonce: number): ImageKnifeOption {
  const option = createMediaImageKnifeOption(image, fallbackSource)
  const baseSignature = option.signature || requestFingerprint
  option.signature = `${baseSignature}:network-fallback:${attempt}:${nonce}`
  option.writeCacheStrategy = CacheStrategy.NONE
  return option
}
