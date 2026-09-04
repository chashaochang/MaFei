import { describe, expect, it } from '@ohos/hypium'
import {
  HomeDestination,
  HomeDestinationMapper
} from '../main/ets/features/home/HomeDestination'

export default function homeDestinationTest() {
  describe('HomeDestinationMapper', () => {
    it('roundTripsAllPhoneIndices', 0, () => {
      expect(HomeDestinationMapper.fromPhoneIndex(0)).assertEqual(HomeDestination.Home)
      expect(HomeDestinationMapper.fromPhoneIndex(1)).assertEqual(HomeDestination.Chasing)
      expect(HomeDestinationMapper.fromPhoneIndex(2)).assertEqual(HomeDestination.Favorite)
      expect(HomeDestinationMapper.fromPhoneIndex(3)).assertEqual(HomeDestination.Media)
      expect(HomeDestinationMapper.fromPhoneIndex(4)).assertEqual(HomeDestination.Mine)
      expect(HomeDestinationMapper.phoneIndex(HomeDestination.Mine)).assertEqual(4)
    })

    it('mapsPadCoreAndMediaMenuItemsToSemanticDestinations', 0, () => {
      expect(HomeDestinationMapper.fromPadMenuIndex(0, false)).assertEqual(HomeDestination.Home)
      expect(HomeDestinationMapper.fromPadMenuIndex(1, false)).assertEqual(HomeDestination.Chasing)
      expect(HomeDestinationMapper.fromPadMenuIndex(2, false)).assertEqual(HomeDestination.Favorite)
      expect(HomeDestinationMapper.fromPadMenuIndex(7, true)).assertEqual(HomeDestination.Media)
      expect(HomeDestinationMapper.padMenuIndex(HomeDestination.Media, 7)).assertEqual(7)
      expect(HomeDestinationMapper.padMenuIndex(HomeDestination.Mine, 7)).assertEqual(-1)
    })

    it('keepsMediaSemanticAcrossPhoneAndPadContentMappings', 0, () => {
      expect(HomeDestinationMapper.contentIndex(
        HomeDestination.Media,
        false,
        3
      )).assertEqual(3)
      expect(HomeDestinationMapper.contentIndex(
        HomeDestination.Media,
        true,
        3
      )).assertEqual(8)
      expect(HomeDestinationMapper.phoneIndex(HomeDestination.Media)).assertEqual(3)
    })
  })
}
