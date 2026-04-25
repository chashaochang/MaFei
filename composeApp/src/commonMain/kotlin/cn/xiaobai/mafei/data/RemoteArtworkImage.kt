package cn.xiaobai.mafei.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
expect fun RemoteArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackLabel: String? = null,
    showPlaceholder: Boolean = true,
    enableCrossfade: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
)
