import { BaseViewModel } from "../../common/base/BaseViewModel";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import { MediaPerson, MediaSummary } from "../../media/catalog/MediaCatalogModels";
import {
  MediaCatalogProvider,
  MediaCatalogSortDirection,
  MediaCatalogSortField
} from "../../media/catalog/MediaCatalogProvider";
import { MediaRef, MediaRefKind } from "../../media/MediaRef";
import { ToastUtil } from "../../utils/ToastUtil";
import { CastDetailUIState } from "./CastDetailUIState";

export class CastDetailViewModel extends BaseViewModel<CastDetailUIState> {
  private catalog?: MediaCatalogProvider
  private requestGeneration: number = 0

  async getCastDetail(ref: MediaRef): Promise<void> {
    const generation = ++this.requestGeneration
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
    } catch (_error) {
      return
    }
    if (!ref.belongsTo(catalog.session)) {
      return
    }
    this.catalog = catalog
    this.ui.mediaRef = ref
    this.ui.personId = ref.id
    try {
      const person = await catalog.getPerson(ref)
      if (!this.isCurrent(catalog, ref, generation)) {
        return
      }
      this.applyPerson(person)
      const requests: Promise<MediaSummary[]>[] = [
        this.getCastMedias(catalog, ref, MediaRefKind.Movie),
        this.getCastMedias(catalog, ref, MediaRefKind.Episode),
        this.getCastMedias(catalog, ref, MediaRefKind.Series)
      ]
      const results = await Promise.all(requests)
      if (!this.isCurrent(catalog, ref, generation)) {
        return
      }
      this.ui.movies = results[0]
      this.ui.episodes = results[1]
      this.ui.series = results[2]
      this.ui.movieCount = person.movieCount ?? results[0].length
      this.ui.episodeCount = person.episodeCount ?? results[1].length
      this.ui.seriesCount = person.seriesCount ?? results[2].length
    } catch (_error) {
    }
  }

  private applyPerson(person: MediaPerson): void {
    this.ui.name = person.name
    this.ui.image = person.primaryImage?.url || ''
    this.ui.imageRef = person.primaryImage
    this.ui.overview = person.overview
    this.ui.sortName = person.sortName
    this.ui.isFavorite = person.userState.favorite.favorite
  }

  private getCastMedias(catalog: MediaCatalogProvider, ref: MediaRef,
    kind: MediaRefKind): Promise<MediaSummary[]> {
    return catalog.getItems({
      personRef: ref,
      kinds: [kind],
      startIndex: 0,
      limit: 10,
      sortFields: [MediaCatalogSortField.Name],
      sortDirections: [MediaCatalogSortDirection.Ascending],
      recursive: true,
      collapseBoxSetItems: false,
      favoriteOnly: false
    }).then((page): MediaSummary[] => page.items).catch((): MediaSummary[] => [])
  }

  private isCurrent(catalog: MediaCatalogProvider, ref: MediaRef, generation: number): boolean {
    return this.catalog === catalog && generation === this.requestGeneration &&
      this.ui.mediaRef?.equals(ref) === true && ref.belongsTo(catalog.session)
  }

  changeFavorite(): void {
    const catalog = this.catalog
    const ref = this.ui.mediaRef
    if (!catalog || !ref || !ref.belongsTo(catalog.session) || !catalog.session.capabilities.favorites) {
      return
    }
    const favorite = !this.ui.isFavorite
    catalog.setFavorite(ref, favorite).then((state) => {
      if (this.catalog !== catalog || !this.ui.mediaRef?.equals(ref)) {
        return
      }
      this.ui.isFavorite = state.favorite
      ToastUtil.showToast(this.ui.context, state.favorite ? '已收藏' : '已取消收藏')
    }).catch(() => {
      ToastUtil.showToast(this.ui.context, favorite ? '收藏失败' : '取消收藏失败')
    })
  }
}
