import { describe, expect, it } from '@ohos/hypium'
import {
  createFeiniuVideoCredentialAlias,
  extractFeiniuVideoTokenFromCookie,
  FeiniuVideoCredentialBackend,
  FeiniuVideoCredentialStore,
  mergeFeiniuVideoCookie
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoCredentialStore'

class FakeFeiniuVideoCredentialBackend implements FeiniuVideoCredentialBackend {
  values: Map<string, string> = new Map()

  read(alias: string): string {
    return this.values.get(alias) || ''
  }

  write(alias: string, value: string): void {
    this.values.set(alias, value)
  }

  remove(alias: string): void {
    this.values.delete(alias)
  }
}

export default function feiniuVideoCredentialStoreTest() {
  describe('FeiniuVideoCredentialStore', () => {
    it('isolatesCredentialsByProviderAccountId', 0, () => {
      const backend = new FakeFeiniuVideoCredentialBackend()
      const store = new FeiniuVideoCredentialStore(backend)

      store.save('account-a', { token: 'token-a', cookie: 'route=lan' })
      store.save('account-b', { token: 'token-b', cookie: 'route=relay' })

      expect(store.load('account-a')?.token).assertEqual('token-a')
      expect(store.load('account-a')?.cookie).assertEqual('route=lan; Trim-MC-token=token-a')
      expect(store.load('account-b')?.token).assertEqual('token-b')
      expect(backend.values.size).assertEqual(2)
    })

    it('derivesTheTokenFromCookieAndNeverStoresAPasswordField', 0, () => {
      const backend = new FakeFeiniuVideoCredentialBackend()
      const store = new FeiniuVideoCredentialStore(backend)

      store.save('account-a', {
        token: '',
        cookie: 'session=abc; Trim-MC-token=cookie-token'
      })

      expect(store.load('account-a')?.token).assertEqual('cookie-token')
      const raw = backend.values.get(createFeiniuVideoCredentialAlias('account-a')) || ''
      expect(raw.includes('password')).assertFalse()
      expect(raw.includes('cookie-token')).assertTrue()
    })

    it('clearsOnlyTheMatchingAccount', 0, () => {
      const backend = new FakeFeiniuVideoCredentialBackend()
      const store = new FeiniuVideoCredentialStore(backend)
      store.save('account-a', { token: 'token-a', cookie: '' })
      store.save('account-b', { token: 'token-b', cookie: '' })

      store.clear('account-a')

      expect(store.load('account-a') === undefined).assertTrue()
      expect(store.load('account-b')?.token).assertEqual('token-b')
    })

    it('ignoresMalformedOrUnsupportedStoredValues', 0, () => {
      const backend = new FakeFeiniuVideoCredentialBackend()
      const store = new FeiniuVideoCredentialStore(backend)
      const alias = createFeiniuVideoCredentialAlias('account-a')

      backend.values.set(alias, '{bad-json')
      expect(store.load('account-a') === undefined).assertTrue()
      backend.values.set(alias, '{"version":2,"token":"token","cookie":""}')
      expect(store.load('account-a') === undefined).assertTrue()
    })

    it('normalizesCookieTokenWithoutDuplicatingTheCookieName', 0, () => {
      expect(extractFeiniuVideoTokenFromCookie(
        'session=abc; Trim-MC-token=old-token')).assertEqual('old-token')
      expect(mergeFeiniuVideoCookie(
        'Trim-MC-token=old-token; session=abc', 'new-token'))
        .assertEqual('Trim-MC-token=new-token; session=abc')
    })

    it('rejectsEmptyProviderAccountIds', 0, () => {
      const store = new FeiniuVideoCredentialStore(new FakeFeiniuVideoCredentialBackend())
      expect(() => store.load('  ')).assertThrowError('providerAccountId must not be empty')
    })
  })
}
