package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaFilter
import cn.mafei.cmp.core.domain.MediaSort
import cn.mafei.cmp.core.domain.MediaSortField
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.SortDirection

/** The shelf and its More page use the same library filter and newest-first order. */
fun MediaCollection.homeLatestRequest(request: PageRequest = PageRequest(pageSize = 30)): PageRequest =
    request.copy(
        sort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
        filter = MediaFilter(mediaTypes = mediaTypes),
    )
