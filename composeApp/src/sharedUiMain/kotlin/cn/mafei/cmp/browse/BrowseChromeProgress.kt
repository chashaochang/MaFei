package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import cn.mafei.cmp.session.*

@Composable
internal fun rememberBrowseChromeProgress(
    destination: String,
    homeGridState: LazyGridState,
    chasingListState: androidx.compose.foundation.lazy.LazyListState,
    favoriteGridState: LazyGridState,
    libraryGridState: LazyGridState,
): State<Float> {
    return remember(
        destination,
        homeGridState,
        chasingListState,
        favoriteGridState,
        libraryGridState,
    ) {
        derivedStateOf {
            if (destination == ChasingDestination) {
                if (chasingListState.firstVisibleItemIndex > 0) 1f
                else (chasingListState.firstVisibleItemScrollOffset / 160f).coerceIn(0f, 1f)
            } else {
                val activeGrid = when (destination) {
                    HomeDestination -> homeGridState
                    FavoriteDestination -> favoriteGridState
                    LibraryDestination -> libraryGridState
                    else -> return@derivedStateOf 0f
                }
                if (activeGrid.firstVisibleItemIndex > 0) 1f
                else (activeGrid.firstVisibleItemScrollOffset / 160f).coerceIn(0f, 1f)
            }
        }
    }
}
