import { MediaCatalogSortField } from './MediaCatalogProvider'

export function mediaCatalogSortField(value: string): MediaCatalogSortField {
  if (value === 'Name') {
    return MediaCatalogSortField.Name
  }
  if (value === 'DateCreated') {
    return MediaCatalogSortField.DateCreated
  }
  if (value === 'PremiereDate') {
    return MediaCatalogSortField.PremiereDate
  }
  if (value === 'DatePlayed') {
    return MediaCatalogSortField.DatePlayed
  }
  if (value === 'CommunityRating') {
    return MediaCatalogSortField.CommunityRating
  }
  if (value === 'CriticRating') {
    return MediaCatalogSortField.CriticRating
  }
  if (value === 'ProductionYear') {
    return MediaCatalogSortField.ProductionYear
  }
  if (value === 'PlayCount') {
    return MediaCatalogSortField.PlayCount
  }
  if (value === 'Runtime') {
    return MediaCatalogSortField.Runtime
  }
  if (value === 'OfficialRating') {
    return MediaCatalogSortField.OfficialRating
  }
  if (value === 'Random') {
    return MediaCatalogSortField.Random
  }
  if (value === 'Album') {
    return MediaCatalogSortField.Album
  }
  if (value === 'Artist') {
    return MediaCatalogSortField.Artist
  }
  if (value === 'AlbumArtist') {
    return MediaCatalogSortField.AlbumArtist
  }
  if (value === 'AiredEpisodeOrder') {
    return MediaCatalogSortField.AiredEpisodeOrder
  }
  if (value === 'SeriesSortName') {
    return MediaCatalogSortField.SeriesSortName
  }
  if (value === 'Studio') {
    return MediaCatalogSortField.Studio
  }
  if (value === 'IsFavoriteOrLiked') {
    return MediaCatalogSortField.IsFavoriteOrLiked
  }
  if (value === 'DateLastContentAdded') {
    return MediaCatalogSortField.DateLastContentAdded
  }
  if (value === 'IsUnplayed') {
    return MediaCatalogSortField.IsUnplayed
  }
  if (value === 'IsPlayed') {
    return MediaCatalogSortField.IsPlayed
  }
  return MediaCatalogSortField.Default
}
