package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.session.*

internal class BrowseSelectionState {
    var pendingDeleteMedia by mutableStateOf<List<MediaId>?>(null)
    var librarySelectedIds by mutableStateOf(emptySet<String>())
    val librarySelectionMode get() = librarySelectedIds.isNotEmpty()
}
