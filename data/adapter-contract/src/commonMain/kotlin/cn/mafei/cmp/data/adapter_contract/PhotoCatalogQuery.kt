package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.MediaId

data class PhotoCatalogQuery(val parentId: MediaId, val albums: Boolean = false)
