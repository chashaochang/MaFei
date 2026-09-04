import { describe, expect, it } from '@ohos/hypium'
import { BaseItemDto, BaseItemKind } from 'sdk/src/main/ets/generated-client/models'
import { MusicMapper } from '../main/ets/features/music/MusicMapper'

export default function musicMapperTest() {
  describe('MusicMapper', () => {
    const mapper = new MusicMapper('https://music.example')

    it('mapsAlbumMetadataAndTicks', 0, () => {
      const item: BaseItemDto = {
        Id: 'album-1',
        Type: BaseItemKind.MusicAlbum,
        Name: 'Album',
        AlbumArtist: 'Artist',
        ProductionYear: 2026,
        ChildCount: 12,
        CumulativeRunTimeTicks: 900000,
        ImageTags: { Primary: 'tag-1' },
        UserData: { IsFavorite: true }
      }

      const album = mapper.album(item)

      expect(album.id).assertEqual('album-1')
      expect(album.artist).assertEqual('Artist')
      expect(album.trackCount).assertEqual(12)
      expect(album.durationMs).assertEqual(90)
      expect(album.favorite).assertTrue()
      expect(album.imageUrl.length > 0).assertTrue()
    })

    it('mapsTrackAlbumArtAndPosition', 0, () => {
      const item: BaseItemDto = {
        Id: 'track-1',
        Type: BaseItemKind.Audio,
        Name: 'Track',
        Artists: ['Artist A', 'Artist B'],
        Album: 'Album',
        AlbumId: 'album-1',
        AlbumPrimaryImageTag: 'album-tag',
        ParentIndexNumber: 2,
        IndexNumber: 7,
        RunTimeTicks: 150000,
        UserData: { PlaybackPositionTicks: 20000 }
      }

      const track = mapper.track(item)

      expect(track.artist).assertEqual('Artist A / Artist B')
      expect(track.discNumber).assertEqual(2)
      expect(track.trackNumber).assertEqual(7)
      expect(track.durationMs).assertEqual(15)
      expect(track.playbackPositionMs).assertEqual(2)
      expect(track.imageUrl.length > 0).assertTrue()
    })

    it('keepsMissingOptionalFieldsStable', 0, () => {
      const track = mapper.track({ Id: 'track-empty', Type: BaseItemKind.Audio })

      expect(track.title).assertEqual('')
      expect(track.durationMs).assertEqual(0)
      expect(track.favorite).assertFalse()
    })
  })
}
