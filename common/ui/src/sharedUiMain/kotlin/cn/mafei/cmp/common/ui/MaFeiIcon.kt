package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

enum class MaFeiGlyph {
    Home, Library, More, Back, Arrow, Chevron, Search, Refresh, Appearance, Account, History,
    Play, Favorite, Watched, Clock, Close,
    PersonFilled,
}

/** Small product-owned outline set. No new icon/font dependency on CPF. */
@Composable
fun MaFeiIcon(
    glyph: MaFeiGlyph,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
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
                // 纯箭头（无横杠）：只有两条斜线组成的 ›。
                MaFeiGlyph.Chevron -> { line(10f, 5f, 17f, 12f); line(17f, 12f, 10f, 19f) }
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
                MaFeiGlyph.History -> {
                    drawArc(
                        color,
                        -48f,
                        300f,
                        false,
                        Offset(4f, 4f),
                        androidx.compose.ui.geometry.Size(16f, 16f),
                        style = Stroke(1.7f),
                    )
                    line(4.4f, 5.2f, 4.4f, 10f)
                    line(4.4f, 5.2f, 9.2f, 5.2f)
                    line(12f, 7.7f, 12f, 12f)
                    line(12f, 12f, 15.4f, 14f)
                }
                MaFeiGlyph.Play -> {
                    val play = Path().apply { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
                    drawPath(play, color)
                }
                MaFeiGlyph.Favorite -> {
                    val heart = Path().apply {
                        moveTo(12f, 20f)
                        cubicTo(10f, 18f, 4f, 14.1f, 4f, 8.8f)
                        cubicTo(4f, 5.7f, 6.3f, 3.5f, 9.1f, 3.5f)
                        cubicTo(10.6f, 3.5f, 11.6f, 4.2f, 12f, 5f)
                        cubicTo(12.4f, 4.2f, 13.4f, 3.5f, 14.9f, 3.5f)
                        cubicTo(17.7f, 3.5f, 20f, 5.7f, 20f, 8.8f)
                        cubicTo(20f, 14.1f, 14f, 18f, 12f, 20f)
                    }
                    drawPath(heart, color, style = Stroke(1.7f, cap = StrokeCap.Round))
                }
                MaFeiGlyph.Watched -> {
                    line(5f, 12.5f, 9.5f, 17f)
                    line(9.5f, 17f, 19f, 7f)
                }
                MaFeiGlyph.Clock -> {
                    drawCircle(color, 8.5f, Offset(12f, 12f), style = Stroke(1.7f))
                    line(12f, 7f, 12f, 12f)
                    line(12f, 12f, 15.5f, 14f)
                }
                MaFeiGlyph.PersonFilled -> {
                    drawCircle(color, 4f, Offset(12f, 8f))
                    val body = Path().apply {
                        moveTo(4.4f, 20f)
                        cubicTo(5.4f, 15.4f, 8.4f, 13.4f, 12f, 13.4f)
                        cubicTo(15.6f, 13.4f, 18.6f, 15.4f, 19.6f, 20f)
                        close()
                    }
                    drawPath(body, color)
                }
                MaFeiGlyph.Close -> {
                    line(7f, 7f, 17f, 17f)
                    line(17f, 7f, 7f, 17f)
                }
            }
        }
    }
}
