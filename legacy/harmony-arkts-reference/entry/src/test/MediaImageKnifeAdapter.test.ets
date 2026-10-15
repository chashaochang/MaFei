import { describe, expect, it } from '@ohos/hypium'
import { CacheStrategy } from '@ohos/imageknifepro'
import {
  createMediaImageNetworkFallbackOption,
  mediaImageRequestFingerprint
} from '../main/ets/media/catalog/MediaImageKnifeAdapter'
import {
  MediaImageKind,
  MediaImageRef
} from '../main/ets/media/catalog/MediaCatalogModels'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'

function image(url: string, token: string): MediaImageRef {
  const owner = new MediaRef(
    MediaProviderKind.FeiniuVideo,
    'account-1',
    'movie-1',
    MediaRefKind.Movie
  )
  return new MediaImageRef({
    owner: owner,
    kind: MediaImageKind.Primary,
    url: url,
    requestPath: '/v/api/v1/sys/img/poster.jpg?w=640',
    variant: 'poster-640',
    revision: 'poster.jpg',
    headers: [{ name: 'Authorization', value: token }]
  })
}

export default function mediaImageKnifeAdapterTest(): void {
  describe('MediaImageKnifeAdapter', () => {
    it('keepsTheFingerprintStableAcrossEquivalentImageObjects', 0, () => {
      const first = image('https://media.example/poster.jpg', 'token-1')
      const second = image('https://media.example/poster.jpg', 'token-1')

      expect(mediaImageRequestFingerprint(first))
        .assertEqual(mediaImageRequestFingerprint(second))
    })

    it('changesTheFingerprintWhenRouteOrCredentialsChange', 0, () => {
      const baseline = mediaImageRequestFingerprint(
        image('https://relay.example/poster.jpg', 'token-1'))
      const changedRoute = mediaImageRequestFingerprint(
        image('https://public.example/poster.jpg', 'token-1'))
      const changedToken = mediaImageRequestFingerprint(
        image('https://relay.example/poster.jpg', 'token-2'))

      expect(changedRoute === baseline).assertFalse()
      expect(changedToken === baseline).assertFalse()
    })

    it('networkFallbackBypassesTheOriginalCacheAndKeepsAuthentication', 0, () => {
      const source = image('https://media.example/poster.jpg', 'token-1')
      const fingerprint = mediaImageRequestFingerprint(source)
      const option = createMediaImageNetworkFallbackOption(source, '', fingerprint, 1, 1234)

      expect(option.loadSrc).assertEqual(source.url)
      expect(option.signature === source.cacheKey).assertFalse()
      expect(option.signature).assertContain(':network-fallback:1:1234')
      expect(option.writeCacheStrategy).assertEqual(CacheStrategy.NONE)
      expect(option.headerOption?.length).assertEqual(1)
      expect(option.headerOption?.[0].key).assertEqual('Authorization')
      expect(option.headerOption?.[0].value).assertEqual('token-1')
    })
  })
}
