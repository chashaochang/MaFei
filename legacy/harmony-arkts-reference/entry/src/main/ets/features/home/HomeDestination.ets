export enum HomeDestination {
  Search = 'search',
  Home = 'home',
  Chasing = 'chasing',
  Favorite = 'favorite',
  Media = 'media',
  Mine = 'mine'
}

export class HomeDestinationMapper {
  static fromPhoneIndex(index: number): HomeDestination {
    if (index === 1) {
      return HomeDestination.Chasing
    }
    if (index === 2) {
      return HomeDestination.Favorite
    }
    if (index === 3) {
      return HomeDestination.Media
    }
    if (index === 4) {
      return HomeDestination.Mine
    }
    return HomeDestination.Home
  }

  static phoneIndex(destination: HomeDestination): number {
    if (destination === HomeDestination.Chasing) {
      return 1
    }
    if (destination === HomeDestination.Favorite) {
      return 2
    }
    if (destination === HomeDestination.Media) {
      return 3
    }
    if (destination === HomeDestination.Mine) {
      return 4
    }
    return 0
  }

  static fromPadMenuIndex(index: number, isMediaItem: boolean): HomeDestination {
    if (index === 1) {
      return HomeDestination.Chasing
    }
    if (index === 2) {
      return HomeDestination.Favorite
    }
    if (isMediaItem) {
      return HomeDestination.Media
    }
    return HomeDestination.Home
  }

  static padMenuIndex(destination: HomeDestination, selectedMediaIndex: number): number {
    if (destination === HomeDestination.Chasing) {
      return 1
    }
    if (destination === HomeDestination.Favorite) {
      return 2
    }
    if (destination === HomeDestination.Media) {
      return selectedMediaIndex
    }
    if (destination === HomeDestination.Mine) {
      return -1
    }
    return 0
  }

  static contentIndex(destination: HomeDestination, usePadMedia: boolean,
    selectedMediaOrdinal: number): number {
    if (destination === HomeDestination.Media && usePadMedia && selectedMediaOrdinal >= 0) {
      return 5 + selectedMediaOrdinal
    }
    return HomeDestinationMapper.phoneIndex(destination)
  }
}
