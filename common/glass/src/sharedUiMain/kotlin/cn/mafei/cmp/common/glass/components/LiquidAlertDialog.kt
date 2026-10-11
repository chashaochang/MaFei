package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.LocalGlassIntensity
import cn.mafei.cmp.common.glass.utils.InteractiveHighlight
import cn.mafei.cmp.common.glass.drawStyledBackdrop as drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Port of the upstream catalog's dialog panel
 * (`app/src/commonMain/kotlin/com/kyant/backdrop/catalog/destinations/DialogContent.kt`).
 *
 * The material is the upstream one, not the app's own: a continuous 48dp
 * rounded rectangle over `colorControls` + `blur` + `lens(depthEffect)`, a plain
 * highlight, and two equal-width capsule actions — the dismiss one a wash of the
 * container, the confirm one filled with [accentColor]. The dim behind the panel
 * belongs to the page, so it stays with the caller.
 *
 * Colors are parameters (like [LiquidButton]): this module owns material, not
 * theme.
 */
@Composable
fun LiquidAlertDialog(
    backdrop: GlassBackdrop,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    containerColor: Color,
    isLightTheme: Boolean,
    modifier: Modifier = Modifier,
    confirmContentColor: Color = Color.White,
) {
    val glassIntensity = LocalGlassIntensity.current
    Column(
        modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop.layer,
                shape = { RoundedRectangle(48f.dp) },
                effects = {
                    colorControls(
                        brightness = if (isLightTheme) .2f else 0f,
                        saturation = 1.5f,
                    )
                    blur((if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx()) * glassIntensity)
                    lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                onDrawSurface = { drawRect(containerColor) },
            ),
    ) {
        BasicText(
            title,
            Modifier.padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 12.dp),
            style = TextStyle(contentColor, 24.sp, FontWeight.Medium),
        )
        BasicText(
            message,
            Modifier
                .then(if (isLightTheme) Modifier else Modifier.graphicsLayer(blendMode = BlendMode.Plus))
                .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 12.dp),
            style = TextStyle(contentColor.copy(alpha = .68f), 15.sp),
            maxLines = 5,
        )
        Row(
            Modifier
                .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlertAction(
                text = dismissText,
                background = containerColor.copy(alpha = .2f),
                textColor = contentColor,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            AlertAction(
                text = confirmText,
                background = accentColor,
                textColor = confirmContentColor,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Upstream action: a 48dp capsule filling half the panel, with the upstream press
 * feedback — the highlight wash `InteractiveHighlight` paints, driven by its own
 * gesture modifier (exactly as `LiquidButton` wires it). A plain `clickable` with
 * no indication would leave the button completely inert under the finger.
 */
@Composable
private fun AlertAction(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier
            .clip(Capsule())
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
            .drawWithContent {
                drawContent()
                if (hovered) drawRect(Color.White.copy(alpha = .045f))
            }
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = TextStyle(textColor, 16.sp))
    }
}
