package cn.mafei.cmp.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

enum class PlayerChromeGlyph {
    More,
    Play,
    Pause,
    Retry,
    Fullscreen,
    ExitFullscreen,
    WindowFullscreen,
    WindowRestore,
    Lock,
    Unlock,
    Previous,
    Next,
    Info,
    Orientation,
    Brightness,
    Volume,
    Muted,
}

@Composable
fun ChromeGlyph(glyph: PlayerChromeGlyph, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val stroke = size.minDimension * .09f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = stroke) {
            drawLine(
                color,
                Offset(size.width * x1, size.height * y1),
                Offset(size.width * x2, size.height * y2),
                width,
                StrokeCap.Round,
            )
        }
        when (glyph) {
            PlayerChromeGlyph.More -> {
                val radius = size.minDimension * .075f
                listOf(.24f, .5f, .76f).forEach { drawCircle(color, radius, Offset(size.width * it, size.height * .5f)) }
            }
            PlayerChromeGlyph.Play -> drawPath(Path().apply {
                moveTo(size.width * .3f, size.height * .16f)
                lineTo(size.width * .84f, size.height * .5f)
                lineTo(size.width * .3f, size.height * .84f)
                close()
            }, color)
            PlayerChromeGlyph.Pause -> {
                drawRoundRect(color, Offset(size.width * .22f, size.height * .16f), Size(size.width * .2f, size.height * .68f))
                drawRoundRect(color, Offset(size.width * .58f, size.height * .16f), Size(size.width * .2f, size.height * .68f))
            }
            PlayerChromeGlyph.Retry -> {
                drawArc(color, -55f, 285f, false, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawPath(Path().apply {
                    moveTo(size.width * .7f, size.height * .05f)
                    lineTo(size.width * .92f, size.height * .18f)
                    lineTo(size.width * .68f, size.height * .3f)
                    close()
                }, color)
            }
            PlayerChromeGlyph.Fullscreen -> {
                line(.12f, .38f, .12f, .12f); line(.12f, .12f, .38f, .12f)
                line(.62f, .12f, .88f, .12f); line(.88f, .12f, .88f, .38f)
                line(.12f, .62f, .12f, .88f); line(.12f, .88f, .38f, .88f)
                line(.62f, .88f, .88f, .88f); line(.88f, .62f, .88f, .88f)
            }
            PlayerChromeGlyph.ExitFullscreen -> {
                line(.12f, .38f, .38f, .38f); line(.38f, .38f, .38f, .12f)
                line(.62f, .12f, .62f, .38f); line(.62f, .38f, .88f, .38f)
                line(.12f, .62f, .38f, .62f); line(.38f, .62f, .38f, .88f)
                line(.62f, .88f, .62f, .62f); line(.62f, .62f, .88f, .62f)
            }
            PlayerChromeGlyph.WindowFullscreen, PlayerChromeGlyph.WindowRestore -> {
                drawRoundRect(color, Offset(size.width * .12f, size.height * .22f),
                    Size(size.width * .76f, size.height * .56f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .05f), style = Stroke(stroke))
                if (glyph == PlayerChromeGlyph.WindowFullscreen) {
                    line(.26f, .4f, .26f, .64f); line(.26f, .64f, .52f, .64f)
                    line(.48f, .36f, .74f, .36f); line(.74f, .36f, .74f, .60f)
                } else {
                    line(.24f, .48f, .44f, .48f); line(.44f, .48f, .44f, .3f)
                    line(.56f, .7f, .56f, .52f); line(.56f, .52f, .76f, .52f)
                }
            }
            PlayerChromeGlyph.Orientation -> {
                // Screen-rotation glyph: a small device frame makes this
                // distinct from the generic refresh/sync icon at a glance.
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * .36f, size.height * .27f),
                    size = Size(size.width * .28f, size.height * .46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .05f),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color,
                    startAngle = 35f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color,
                    startAngle = 215f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawPath(Path().apply {
                    moveTo(size.width * .80f, size.height * .22f)
                    lineTo(size.width * .90f, size.height * .20f)
                    lineTo(size.width * .86f, size.height * .30f)
                    close()
                }, color)
                drawPath(Path().apply {
                    moveTo(size.width * .20f, size.height * .78f)
                    lineTo(size.width * .10f, size.height * .80f)
                    lineTo(size.width * .14f, size.height * .70f)
                    close()
                }, color)
            }
            PlayerChromeGlyph.Lock, PlayerChromeGlyph.Unlock -> {
                drawRoundRect(
                    color,
                    Offset(size.width * .22f, size.height * .43f),
                    Size(size.width * .56f, size.height * .45f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .1f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawPath(Path().apply {
                    moveTo(size.width * (if (glyph == PlayerChromeGlyph.Unlock) .38f else .3f), size.height * .43f)
                    cubicTo(
                        size.width * (if (glyph == PlayerChromeGlyph.Unlock) .3f else .3f), size.height * .13f,
                        size.width * .7f, size.height * .13f,
                        size.width * .7f, size.height * .43f,
                    )
                }, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            PlayerChromeGlyph.Previous -> {
                drawPath(Path().apply {
                    moveTo(size.width * .82f, size.height * .18f)
                    lineTo(size.width * .34f, size.height * .5f)
                    lineTo(size.width * .82f, size.height * .82f)
                    close()
                }, color)
                line(.24f, .18f, .24f, .82f)
            }
            PlayerChromeGlyph.Next -> {
                drawPath(Path().apply {
                    moveTo(size.width * .18f, size.height * .18f)
                    lineTo(size.width * .66f, size.height * .5f)
                    lineTo(size.width * .18f, size.height * .82f)
                    close()
                }, color)
                line(.76f, .18f, .76f, .82f)
            }
            PlayerChromeGlyph.Brightness -> {
                drawCircle(color, size.minDimension * .2f, Offset(size.width * .5f, size.height * .5f))
                listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { degrees ->
                    val radians = degrees * kotlin.math.PI.toFloat() / 180f
                    val dx = kotlin.math.cos(radians)
                    val dy = kotlin.math.sin(radians)
                    line(
                        .5f + dx * .32f, .5f + dy * .32f,
                        .5f + dx * .46f, .5f + dy * .46f,
                        stroke * .8f,
                    )
                }
            }
            PlayerChromeGlyph.Volume, PlayerChromeGlyph.Muted -> {
                drawPath(Path().apply {
                    moveTo(size.width * .12f, size.height * .38f)
                    lineTo(size.width * .3f, size.height * .38f)
                    lineTo(size.width * .52f, size.height * .18f)
                    lineTo(size.width * .52f, size.height * .82f)
                    lineTo(size.width * .3f, size.height * .62f)
                    lineTo(size.width * .12f, size.height * .62f)
                    close()
                }, color)
                if (glyph == PlayerChromeGlyph.Muted) {
                    line(.65f, .35f, .9f, .65f); line(.9f, .35f, .65f, .65f)
                } else drawArc(
                    color, -55f, 110f, false,
                    topLeft = Offset(size.width * .42f, size.height * .3f),
                    size = Size(size.width * .3f, size.height * .4f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (glyph != PlayerChromeGlyph.Muted) drawArc(
                    color, -55f, 110f, false,
                    topLeft = Offset(size.width * .5f, size.height * .2f),
                    size = Size(size.width * .42f, size.height * .6f),
                    style = Stroke(stroke * .8f, cap = StrokeCap.Round),
                )
            }
            PlayerChromeGlyph.Info -> {
                drawCircle(color, size.minDimension * .46f, Offset(size.width * .5f, size.height * .5f), style = Stroke(stroke))
                drawRoundRect(
                    color,
                    Offset(size.width * .47f, size.height * .43f),
                    Size(size.width * .06f, size.height * .3f),
                    androidx.compose.ui.geometry.CornerRadius(size.width * .03f),
                )
                drawCircle(color, size.minDimension * .045f, Offset(size.width * .5f, size.height * .32f))
            }
        }
    }
}
