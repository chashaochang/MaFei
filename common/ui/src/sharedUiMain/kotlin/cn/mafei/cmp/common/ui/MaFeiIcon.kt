package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

enum class MaFeiGlyph { Home, Library, More, Back, Arrow, Search, Refresh, Appearance, Account }

/** Small product-owned outline set. No new icon/font dependency on CPF. */
@Composable
fun MaFeiIcon(glyph: MaFeiGlyph, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.7f, StrokeCap.Round)
            when (glyph) {
                MaFeiGlyph.Home -> {
                    val roof = Path().apply { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f) }
                    drawPath(roof, color, style = Stroke(1.7f))
                    val house = Path().apply { moveTo(5f, 10f); lineTo(5f, 21f); lineTo(10f, 21f)
                        lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 10f) }
                    drawPath(house, color, style = Stroke(1.7f))
                }
                MaFeiGlyph.Library -> {
                    drawRoundRect(color, Offset(3f, 6f), androidx.compose.ui.geometry.Size(18f, 15f),
                        androidx.compose.ui.geometry.CornerRadius(2f), style = Stroke(1.7f))
                    line(7f, 3f, 17f, 3f); line(8f, 10f, 8f, 17f); line(12f, 10f, 12f, 17f); line(16f, 10f, 16f, 17f)
                }
                MaFeiGlyph.More -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.6f, Offset(it, 12f)) }
                MaFeiGlyph.Back -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
                MaFeiGlyph.Arrow -> { line(4f, 12f, 20f, 12f); line(14f, 6f, 20f, 12f); line(14f, 18f, 20f, 12f) }
                MaFeiGlyph.Search -> { drawCircle(color, 7f, Offset(10f, 10f), style = Stroke(1.7f)); line(15f, 15f, 21f, 21f) }
                MaFeiGlyph.Refresh -> { drawArc(color, 40f, 300f, false, Offset(4f, 4f),
                    androidx.compose.ui.geometry.Size(16f, 16f), style = Stroke(1.7f)); line(20f, 4f, 20f, 10f); line(20f, 10f, 14f, 10f) }
                MaFeiGlyph.Appearance -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = Stroke(1.7f))
                    drawArc(color, -90f, 180f, true, Offset(6f, 6f), androidx.compose.ui.geometry.Size(12f, 12f))
                }
                MaFeiGlyph.Account -> {
                    drawCircle(color, 4f, Offset(12f, 7f), style = Stroke(1.7f))
                    drawArc(color, 180f, 180f, false, Offset(4f, 13f), androidx.compose.ui.geometry.Size(16f, 14f), style = Stroke(1.7f))
                }
            }
        }
    }
}
