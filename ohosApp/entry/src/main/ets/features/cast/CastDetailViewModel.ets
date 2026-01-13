import { AxiosResponse } from "@ohos/axios";
import { getItemsApi, getUserLibraryApi } from "sdk";
import { BaseItemDto, BaseItemDtoQueryResult, BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl } from "../../common/CommonFunc";
import { ApiClient } from "../../network/ApiClient";
import { ToastUtil } from "../../utils/ToastUtil";
import { CastDetailUIState } from "./CastDetailUIState";

export class CastDetailViewModel extends BaseViewModel<CastDetailUIState> {
  getCastDetail() {
    getUserLibraryApi(ApiClient.Instance()).getItem({
      userId: ApiClient.Instance().userId,
      itemId: this.ui.personId
    }).then((res: AxiosResponse<BaseItemDto>) => {
      if (res && res.data) {
        let item = res.data
        this.ui.name = item.Name || ''
        this.ui.image = getItemImageUrl(item)
        this.ui.overview = item.Overview || ''
        this.ui.sortName = item.SortName || ''
        this.ui.isFavorite = item.UserData?.IsFavorite || false
        if (item.MovieCount != undefined && item.MovieCount != null && item.MovieCount > 0) {
          this.ui.movieCount = item.MovieCount
          this.getCastMedias(BaseItemKind.Movie)
        }
        if (item.EpisodeCount != undefined && item.EpisodeCount != null && item.EpisodeCount > 0) {
          this.ui.episodeCount = item.EpisodeCount
          this.getCastMedias(BaseItemKind.Episode)
        }
        if (item.SeriesCount != undefined && item.SeriesCount != null && item.SeriesCount > 0) {
          this.ui.seriesCount = item.SeriesCount
          this.getCastMedias(BaseItemKind.Series)
        }
      }
    })
  }

  getCastMedias(type: BaseItemKind) {
    getItemsApi(ApiClient.Instance()).getItems({
      includeItemTypes: [type],
      recursive: true,
      fields: ['ParentId', 'PrimaryImageAspectRatio', 'ParentId', 'PrimaryImageAspectRatio'],
      startIndex: 0,
      limit: 10,
      collapseBoxSetItems: false,
      sortBy: ['SortName'],
      sortOrder: ['Ascending'],
      personIds: [this.ui.personId]
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        if (type === BaseItemKind.Movie) {
          this.ui.movies = res.data.Items
        } else if (type === BaseItemKind.Episode) {
          this.ui.episodes = res.data.Items
        } else if (type === BaseItemKind.Series) {
          this.ui.series = res.data.Items
        }
      }
    })
  }

  changeFavorite() {
    if (this.ui.isFavorite) {
      getUserLibraryApi(ApiClient.Instance()).unmarkFavoriteItem({
        userId: ApiClient.Instance().userId,
        itemId: this.ui.personId,
      }).then(() => {
        this.ui.isFavorite = false
        ToastUtil.showToast(this.ui.context, '已取消收藏')
      }).catch(() => {
        ToastUtil.showToast(this.ui.context, '取消收藏失败')
      })
    } else {
      getUserLibraryApi(ApiClient.Instance()).markFavoriteItem({
        userId: ApiClient.Instance().userId,
        itemId: this.ui.personId,
      }).then(() => {
        this.ui.isFavorite = true
        ToastUtil.showToast(this.ui.context, '已收藏')
      }).catch(() => {
        ToastUtil.showToast(this.ui.context, '收藏失败')
      })
    }
  }
}