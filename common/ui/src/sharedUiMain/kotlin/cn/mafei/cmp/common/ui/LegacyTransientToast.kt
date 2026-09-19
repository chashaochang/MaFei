package cn.mafei.cmp.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import kotlinx.coroutines.delay

/** Compose counterpart of the short `promptAction.showToast` feedback used by the ArkUI pages. */
@Composable
fun LegacyTransientToast(
    message: String?,
    modifier: Modifier = Modifier,
    durationMillis: Long = 2_000L,
) {
    var visibleMessage by remember { mutableStateOf<String?>(null) }
    val safeArea = LocalMaFeiSafeArea.current
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            visibleMessage = message
            delay(durationMillis)
            visibleMessage = null
        }
    }
    AnimatedVisibility(
        visible = visibleMessage != null,
        modifier = modifier.padding(
            start = safeArea.start + 24.dp,
            end = safeArea.end + 24.dp,
            bottom = safeArea.bottom + 28.dp,
        ),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6222222)).padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                visibleMessage.orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
