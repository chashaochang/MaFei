export enum MediaProviderKind {
  Jellyfin = 'jellyfin',
  FeiniuVideo = 'feiniu_video'
}

export function normalizeMediaProviderKind(value?: string | null): MediaProviderKind {
  if (value === MediaProviderKind.FeiniuVideo) {
    return MediaProviderKind.FeiniuVideo
  }
  return MediaProviderKind.Jellyfin
}
