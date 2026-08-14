import { describe, expect, it } from '@ohos/hypium'
import { BaseItemKind, ItemSortBy } from 'sdk/src/main/ets/generated-client/models'
import { MusicLibrarySection } from '../main/ets/features/music/MusicModels'
import { MusicQueryPlanner } from '../main/ets/features/music/MusicQueryPlanner'

export default function musicQueryPlannerTest() {
  describe('MusicQueryPlanner', () => {
    it('normalizesPagingAndLibraryId', 0, () => {
      const request = MusicQueryPlanner.normalize({
        libraryId: ' music-library ',
        section: MusicLibrarySection.Albums,
        startIndex: -3,
        limit: 500
      })

      expect(request.libraryId).assertEqual('music-library')
      expect(request.startIndex).assertEqual(0)
      expect(request.limit).assertEqual(100)
    })

    it('mapsSectionsToJellyfinItemKinds', 0, () => {
      expect(MusicQueryPlanner.includeItemTypes(MusicLibrarySection.Albums)[0])
        .assertEqual(BaseItemKind.MusicAlbum)
      expect(MusicQueryPlanner.includeItemTypes(MusicLibrarySection.Artists)[0])
        .assertEqual(BaseItemKind.MusicArtist)
      expect(MusicQueryPlanner.includeItemTypes(MusicLibrarySection.Tracks)[0])
        .assertEqual(BaseItemKind.Audio)
    })

    it('usesStableTrackOrdering', 0, () => {
      const sort = MusicQueryPlanner.sortBy(MusicLibrarySection.Tracks)

      expect(sort.length).assertEqual(4)
      expect(sort[0]).assertEqual(ItemSortBy.AlbumArtist)
      expect(sort[2]).assertEqual(ItemSortBy.ParentIndexNumber)
      expect(sort[3]).assertEqual(ItemSortBy.IndexNumber)
    })
  })
}
