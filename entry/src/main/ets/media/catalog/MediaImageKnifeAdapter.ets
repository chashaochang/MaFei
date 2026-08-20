import { HeaderOptions, ImageKnifeOption } from '@ohos/imageknifepro'
import { MediaImageRef, MediaRequestHeader } from './MediaCatalogModels'

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
