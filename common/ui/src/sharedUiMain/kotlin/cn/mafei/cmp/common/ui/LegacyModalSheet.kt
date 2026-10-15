package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Opaque legacy modal: bottom sheet on a phone, centered on a large window. */
@Composable
fun LegacyModalSheet(title: String, onDismiss: () -> Unit, heightFraction: Float = .65f,
    scrimModifier: Modifier = Modifier, largeWindow: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val large = largeWindow ?: (LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != cn.mafei.cmp.common.design_system.NavigationPlacement.Bottom } == true
    )
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnClickOutside = true, dismissOnBackPress = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(scrimModifier.fillMaxSize().mafeiDismissOnOutsidePointer(onDismiss))
            Column(Modifier.align(if (large) Alignment.Center else Alignment.BottomCenter)
                .widthIn(max = if (large) 560.dp else Dp.Infinity).fillMaxWidth()
                .heightIn(max = maxHeight * heightFraction.coerceIn(.2f, .9f))
                .clip(if (large) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface).padding(bottom = 20.dp)) {
                Text(title, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                content()
            }
        }
    }
}
