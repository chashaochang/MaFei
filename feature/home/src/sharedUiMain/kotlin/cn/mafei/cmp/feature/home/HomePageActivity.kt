package cn.mafei.cmp.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive

/** Read activity in a restartable leaf, without invalidating the retained HomeScreen. */
@Composable
internal fun HomePageActivity(content: @Composable (Boolean) -> Unit) {
    content(LocalMaFeiPageActive.current)
}

/** The grid's input gate changes on a tab switch; its content lambda does not. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeFeedGrid(
    columns: GridCells,
    state: LazyGridState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Arrangement.Vertical,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        state = state,
        modifier = modifier,
        overscrollEffect = null,
        userScrollEnabled = LocalMaFeiPageActive.current,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
