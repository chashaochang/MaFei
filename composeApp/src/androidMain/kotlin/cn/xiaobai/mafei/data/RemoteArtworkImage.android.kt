package cn.xiaobai.mafei.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.LocalPlatformContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
actual fun RemoteArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    fallbackLabel: String?,
    showPlaceholder: Boolean,
    enableCrossfade: Boolean,
    contentScale: ContentScale,
) {
    val targetUrl = imageUrl?.trim().orEmpty()
    val resolvedFallbackLabel = resolveFallbackLabel(
        fallbackLabel = fallbackLabel,
        contentDescription = contentDescription,
    )
    if (targetUrl.isBlank()) {
        if (showPlaceholder) {
            ArtworkFallback(
                modifier = modifier,
                fallbackLabel = resolvedFallbackLabel,
                isError = false,
            )
        }
        return
    }
    val request = ImageRequest.Builder(LocalPlatformContext.current)
        .data(targetUrl)
        .crossfade(enableCrossfade)
        .build()
    val painter = rememberAsyncImagePainter(model = request)
    val painterState by painter.state.collectAsState()

    Box(modifier = modifier) {
        if (showPlaceholder && painterState !is AsyncImagePainter.State.Success) {
            ArtworkFallback(
                modifier = Modifier.fillMaxSize(),
                fallbackLabel = resolvedFallbackLabel,
                isError = painterState is AsyncImagePainter.State.Error,
            )
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}

@Composable
private fun ArtworkFallback(
    modifier: Modifier,
    fallbackLabel: String,
    isError: Boolean,
) {
    val background = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    }
    val textColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (fallbackLabel.isNotBlank()) {
            Text(
                text = fallbackLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
    }
}

private fun resolveFallbackLabel(
    fallbackLabel: String?,
    contentDescription: String?,
): String {
    val primary = fallbackLabel?.trim().orEmpty()
    if (primary.isNotBlank()) return primary.take(1)

    val secondary = contentDescription?.trim().orEmpty()
    if (secondary.isNotBlank()) return secondary.take(1)

    return ""
}
