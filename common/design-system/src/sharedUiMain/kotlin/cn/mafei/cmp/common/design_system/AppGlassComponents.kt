package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole

private val LocalReduceTransparency = staticCompositionLocalOf { false }

/** UI-owned preference; platform accessibility/power adapters may supply it later. */
@Composable
fun MaFeiGlassPreferences(reduceTransparency: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceTransparency provides reduceTransparency, content = content)
}

/** Readable reusable content card. Capture source belongs to the surrounding shell. */
@Composable
fun AppGlassCard(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Full,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaFeiSurface(
        MaFeiGlassDefaults.spec(GlassRole.Card, level, LocalReduceTransparency.current),
        modifier, backdrop,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** No raw system insets: the host/shell owns the available content rectangle. */
@Composable
fun AppGlassBar(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Full,
    content: @Composable RowScope.() -> Unit,
) {
    MaFeiSurface(
        MaFeiGlassDefaults.spec(GlassRole.Navigation, level, LocalReduceTransparency.current),
        modifier, backdrop,
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Standard modal window owns dismissal, focus and back semantics. It deliberately
 * has NO parent-window backdrop: cross-window capture has not been validated.
 * Until a dialog-local source exists, all requested levels resolve to opaque.
 */
@Composable
fun AppGlassDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Full,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        MaFeiSurface(
            MaFeiGlassDefaults.spec(GlassRole.Dialog, level, LocalReduceTransparency.current),
            modifier.widthIn(max = 480.dp).fillMaxWidth(),
            backdrop = null,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                content()
                // Vertical actions remain usable in narrow windows and at large font scales.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    confirmButton()
                    dismissButton?.invoke()
                }
            }
        }
    }
}
