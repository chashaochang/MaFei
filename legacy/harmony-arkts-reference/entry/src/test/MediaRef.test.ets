import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'

function providerSession(provider: MediaProviderKind, providerAccountId: string,
  userId: string): MediaProviderSession {
  return new MediaProviderSession({
    provider: provider,
    providerAccountId: providerAccountId,
    userId: userId,
    originalAddress: 'https://media.example',
    baseUrl: 'https://media.example'
  })
}

export default function mediaRefTest() {
  describe('MediaRef', () => {
    it('keepsMediaIdentityInsideItsProviderAndAccount', 0, () => {
      const jellyfin = providerSession(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1')
      const same = new MediaRef(MediaProviderKind.Jellyfin, jellyfin.accountScope, 'item-1', MediaRefKind.Movie)
      const copy = new MediaRef(MediaProviderKind.Jellyfin, jellyfin.accountScope, 'item-1', MediaRefKind.Movie)
      const episode = new MediaRef(
        MediaProviderKind.Jellyfin,
        jellyfin.accountScope,
        'item-1',
        MediaRefKind.Episode
      )

      expect(same.equals(copy)).assertTrue()
      expect(same.equals(episode)).assertTrue()
      expect(episode.kind).assertEqual(MediaRefKind.Episode)
      expect(same.belongsTo(jellyfin)).assertTrue()
    })

    it('doesNotCollideAcrossProvidersOrAccounts', 0, () => {
      const jellyfin = providerSession(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1')
      const feiniu = providerSession(MediaProviderKind.FeiniuVideo, 'feiniu-account-2', 'user-1')
      const jellyfinRef = new MediaRef(
        MediaProviderKind.Jellyfin,
        jellyfin.accountScope,
        'shared-id',
        MediaRefKind.Movie
      )
      const feiniuRef = new MediaRef(
        MediaProviderKind.FeiniuVideo,
        feiniu.accountScope,
        'shared-id',
        MediaRefKind.Movie
      )

      expect(jellyfinRef.equals(feiniuRef)).assertFalse()
      expect(jellyfinRef.belongsTo(feiniu)).assertFalse()
    })

    it('doesNotCollideAcrossAccountsWithinTheSameProvider', 0, () => {
      const firstAccount = providerSession(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1')
      const secondAccount = providerSession(MediaProviderKind.Jellyfin, 'jellyfin-account-2', 'user-1')
      const firstRef = new MediaRef(
        MediaProviderKind.Jellyfin,
        firstAccount.accountScope,
        'shared-id',
        MediaRefKind.Movie
      )
      const secondRef = new MediaRef(
        MediaProviderKind.Jellyfin,
        secondAccount.accountScope,
        'shared-id',
        MediaRefKind.Movie
      )

      expect(firstRef.equals(secondRef)).assertFalse()
    })

    it('usesLengthPrefixedIdentityParts', 0, () => {
      const session = providerSession(MediaProviderKind.Jellyfin, 'jellyfin|account', 'user|1')
      const first = new MediaRef(MediaProviderKind.Jellyfin, session.accountScope, 'a|b', MediaRefKind.Movie)
      const second = new MediaRef(MediaProviderKind.Jellyfin, `${session.accountScope}|a`, 'b', MediaRefKind.Movie)

      expect(first.identity === second.identity).assertFalse()
    })
  })
}
