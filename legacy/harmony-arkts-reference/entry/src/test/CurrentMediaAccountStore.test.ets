import { describe, expect, it } from '@ohos/hypium'
import {
  CurrentMediaAccountBackend,
  CurrentMediaAccountStore
} from '../main/ets/media/account/CurrentMediaAccountStore'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'

class FakeCurrentMediaAccountBackend implements CurrentMediaAccountBackend {
  value: string = ''
  failNextWrite: boolean = false
  failNextFlush: boolean = false

  read(): string {
    return this.value
  }

  write(value: string): void {
    if (this.failNextWrite) {
      this.failNextWrite = false
      throw new Error('write failed')
    }
    this.value = value
  }

  clear(): void {
    this.value = ''
  }

  flush(): void {
    if (this.failNextFlush) {
      this.failNextFlush = false
      throw new Error('flush failed')
    }
  }
}

export default function currentMediaAccountStoreTest() {
  describe('CurrentMediaAccountStore', () => {
    it('persistsOnlyProviderAndProviderAccountId', 0, () => {
      const backend = new FakeCurrentMediaAccountBackend()
      const store = new CurrentMediaAccountStore(backend)

      store.save({
        provider: MediaProviderKind.Jellyfin,
        providerAccountId: '  jellyfin-account-1  '
      })

      expect(backend.value).assertEqual(
        '{"provider":"jellyfin","providerAccountId":"jellyfin-account-1"}'
      )
      expect(store.load()?.provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(store.load()?.providerAccountId).assertEqual('jellyfin-account-1')
    })

    it('rejectsMalformedUnknownAndEmptyPointers', 0, () => {
      const backend = new FakeCurrentMediaAccountBackend()
      const store = new CurrentMediaAccountStore(backend)

      backend.value = '{bad-json'
      expect(store.load() === undefined).assertTrue()
      backend.value = '{"provider":"unknown","providerAccountId":"account-1"}'
      expect(store.load() === undefined).assertTrue()
      backend.value = '{"provider":"feiniu_video","providerAccountId":"  "}'
      expect(store.load() === undefined).assertTrue()
    })

    it('restoresThePreviousPointerWhenFlushFails', 0, () => {
      const backend = new FakeCurrentMediaAccountBackend()
      const store = new CurrentMediaAccountStore(backend)
      store.save({
        provider: MediaProviderKind.Jellyfin,
        providerAccountId: 'jellyfin-account-1'
      })
      backend.failNextFlush = true

      expect(() => {
        store.save({
          provider: MediaProviderKind.FeiniuVideo,
          providerAccountId: 'feiniu-account-1'
        })
      }).assertThrowError('Failed to persist current media account')

      expect(store.load()?.provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(store.load()?.providerAccountId).assertEqual('jellyfin-account-1')
    })

    it('clearsTheCurrentPointerWithoutTouchingProviderData', 0, () => {
      const backend = new FakeCurrentMediaAccountBackend()
      const store = new CurrentMediaAccountStore(backend)
      store.save({
        provider: MediaProviderKind.FeiniuVideo,
        providerAccountId: 'feiniu-account-1'
      })

      store.clear()

      expect(backend.value).assertEqual('')
      expect(store.load() === undefined).assertTrue()
    })
  })
}
