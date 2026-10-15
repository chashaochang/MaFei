package cn.mafei.cmp.feature.connect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.feature.connect.generated.resources.Res
// 每张海报都是生成的扩展属性，必须显式可见；24 个逐个 import 太啰嗦，用星号。
import cn.mafei.cmp.feature.connect.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一格的**目标**宽度。列数由窗口宽度除它得到（桌面 1180dp 恰好 9 列，手机 390dp 得
 * 3 列）—— 固定 9 列在手机上每格只有 40 多 dp，小得看不出是海报。
 */
private const val PosterTileTargetDp = 130f
private const val PosterColumnsMin = 3
private const val PosterColumnsMax = 16
private const val PosterTileGapRatio = .018f
private const val PosterWallTiltDegrees = -6f
private const val PosterWallOverscan = 1.25f

/**
 * 流动的一个循环走多少格。必须与海报挑选的步长 7 互质地构成周期：挑选用
 * `(row * 11 + column * 7) % 24`，7 与 24 互质 → 图案每 24 格重复一次，
 * 所以位移取 24 格时，循环回到起点画面完全重合，接缝无缝。
 */
private const val PosterFlowTiles = 24f

/** 约 4 秒走一格：看得出在流动，又不至于抢视线。 */
private const val PosterFlowCycleMillis = 4_000 * 24

/**
 * 登录/账号历史共用的氛围背景：一面**真实**的影视墙。
 *
 * 海报来自 Wikimedia Commons，全部为公有领域，且只取 1930 年以前的美国影片海报
 * （依年限已进入公有领域，不依赖「未续展」这类需逐件核对的判断；许可信息是逐件
 * 从 Commons API 的 extmetadata 取回核对的）。每张缩到 260px 宽，24 张合计约
 * 700KB，文件名与来源的对应见 `poster_01..poster_24`（年份 / Commons 文件名）：
 *
 * ```
 * 01 1910 Abraham Lincoln's Clemency Movie Posters   13 1928 Caught in the Fog poster
 * 02 1914 Ach, te spodnie!                           14 1928 Five and ten cent Annie poster
 * 03 1917 Jimmy Dale                                 15 1928 Outcast 1928 poster
 * 04 1925 A Kiss for Cinderella 1925                 16 1928 Show Girl insert card
 * 05 1925 That Man Jack!                             17 1928 State Street Sadie poster
 * 06 1926 Going Crooked poster (restored)            18 1928 Stocks and Blondes
 * 07 1927 Border Blackbirds                          19 1929 Gold Diggers 1929
 * 08 1927 Lightning Lariats                          20 1929 On With The Show (1929) Poster
 * 09 1927 One Hour of Love                           21 1929 Sunny Side Up (1929 film)
 * 10 1927 Silk Legs (film)                           22 1929 The Letter (1929) Poster
 * 11 1928 A race for life poster                     23 1930 The Return of Dr. Fu Manchu
 * 12 1928 Beware of Bachelors poster                 24 1930 The Texan 1930 Poster
 * ```
 *
 * 素材已经**离线烘焙**过调色：先去饱和（saturation = .30），再按通道压暗
 * （R ×.26、G ×.28、B ×.34，蓝通道留得多一些）。烘焙而不是运行时加重滤镜，是因为
 * 墙面现在每帧都在流动 —— 每帧上百个带颜色矩阵的绘制太重。原始图备份在
 * `/tmp/posters_original/`，要改调色就重跑同一套参数。
 *
 * 墙**不模糊**：模糊交给盖在登录表单上的液态玻璃去做。再叠横向与纵向压暗：表单侧
 * 与边角沉下去，右侧让海报保持可见。
 *
 * **画在窗口层，不要画在面板里**：登录页是两栏布局（登录 + 账号历史），墙在整个窗口里
 * 是同一面，各栏只是裁掉自己那块。若在每个面板里各画一遍，格子尺度与相位都会不同，
 * 分界线上就会像两块不同的墙纸拼在一起。调用方把它铺在窗口背景层并
 * `captureGlassBackdrop` 出一个源，再把该源传给各面板当 `pageBackdrop`。
 */
@Composable
fun ConnectAtmosphere(modifier: Modifier = Modifier) {
    val posters = rememberPosterPainters()
    // 流动：一行往右、一行往左。墙本身斜着 -6°，所以横向位移看上去是斜向流动。
    val flow by rememberInfiniteTransition(label = "poster-wall").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PosterFlowCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "poster-wall-flow",
    )
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(AmbientBase))) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                rotationZ = PosterWallTiltDegrees
                scaleX = PosterWallOverscan
                scaleY = PosterWallOverscan
            },
        ) {
            val columns = (size.width / PosterTileTargetDp.dp.toPx())
                .roundToInt().coerceIn(PosterColumnsMin, PosterColumnsMax)
            val tileWidth = size.width / columns
            val tileHeight = tileWidth * 1.48f
            val gap = tileWidth * PosterTileGapRatio
            val rows = ceil(size.height / tileHeight).toInt() + 3
            val travel = flow * PosterFlowTiles * tileWidth
            for (row in -2..rows) {
                // 隔行错半格：像贴满海报的墙，而不是规整表格。
                val stagger = if (row % 2 == 0) 0f else -tileWidth * .42f
                // 相邻两行反向流动。
                val signed = if (row % 2 == 0) travel else -travel
                val steps = floor(signed / tileWidth).toInt()
                val frac = signed - steps * tileWidth
                val top = row * tileHeight + gap
                val height = tileHeight - gap * 2f
                // 多画几格，保证位移到任意相位时两端都不露空。
                for (slot in -3..columns + 3) {
                    // 内容跟「世界列号」走而不是屏幕槽位 —— 必须是 slot + steps：位移
                    // 跨过一整格的瞬间，内容要同步前进一格（不是后退），循环回起点时
                    // 同一格位置上的海报才与出发时完全一致，否则整面墙会跳换一次。
                    val column = slot + steps
                    val left = slot * tileWidth + stagger + gap - frac
                    val width = tileWidth - gap * 2f
                    val painter = posters.pick(row, column)
                    if (painter != null) {
                        drawPosterTile(painter, left, top, width, height)
                    } else {
                        // 兜底：没有海报资源时退回色块，不至于露出空洞。
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = posterPalette(row * 31 + column * 17),
                                startY = top,
                                endY = top + height,
                            ),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(tileWidth * .09f),
                        )
                    }
                }
            }
        }
        // 表单侧与边角的压暗；右侧刻意留亮，让影视墙看得见。
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = .80f),
                        Color.Black.copy(alpha = .52f),
                        Color.Black.copy(alpha = .24f),
                    ),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = .50f),
                        Color.Black.copy(alpha = .12f),
                        Color.Black.copy(alpha = .56f),
                    ),
                ),
            ),
        )
    }
}

/** 海报按格子「覆盖」裁切（等同 Image 的 ContentScale.Crop），不拉伸变形。 */
private fun DrawScope.drawPosterTile(
    painter: Painter,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    val source = painter.intrinsicSize
    if (!source.isSpecified || source.minDimension <= 0f) return
    val scale = max(width / source.width, height / source.height)
    val drawnWidth = source.width * scale
    val drawnHeight = source.height * scale
    withTransform({
        translate(left + (width - drawnWidth) / 2f, top + (height - drawnHeight) / 2f)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        with(painter) {
            draw(Size(source.width, source.height), alpha = 1f)
        }
    }
}

/** 墙上用到的海报，顺序与文件编号一致（来源见类注释里的清单）。 */
private val PosterResources: List<DrawableResource> = listOf(
    Res.drawable.poster_01, Res.drawable.poster_02, Res.drawable.poster_03, Res.drawable.poster_04,
    Res.drawable.poster_05, Res.drawable.poster_06, Res.drawable.poster_07, Res.drawable.poster_08,
    Res.drawable.poster_09, Res.drawable.poster_10, Res.drawable.poster_11, Res.drawable.poster_12,
    Res.drawable.poster_13, Res.drawable.poster_14, Res.drawable.poster_15, Res.drawable.poster_16,
    Res.drawable.poster_17, Res.drawable.poster_18, Res.drawable.poster_19, Res.drawable.poster_20,
    Res.drawable.poster_21, Res.drawable.poster_22, Res.drawable.poster_23, Res.drawable.poster_24,
)

@Composable
private fun rememberPosterPainters(): List<Painter> {
    val painters = mutableListOf<Painter>()
    for (resource in PosterResources) painters += painterResource(resource)
    return painters
}

private fun List<Painter>.pick(row: Int, column: Int): Painter? {
    if (isEmpty()) return null
    // 步长 7 与 24 互质：同款不会贴在一起，而且图案每 24 格精确重复（流动靠这一点无缝）。
    val index = (row * 11 + column * 7) % size
    return this[(index + size) % size]
}

private val AmbientBase = listOf(Color(0xFF0A0C11), Color(0xFF161A26), Color(0xFF090A0E))

/** 兜底色块：低饱和、中低明度，只有上下明暗差。 */
private fun posterPalette(seed: Int): List<Color> = when (((seed % 8) + 8) % 8) {
    0 -> listOf(Color(0xFF1E2A44), Color(0xFF0E1320))
    1 -> listOf(Color(0xFF241C3A), Color(0xFF101020))
    2 -> listOf(Color(0xFF16323C), Color(0xFF0B1418))
    3 -> listOf(Color(0xFF33202A), Color(0xFF150E15))
    4 -> listOf(Color(0xFF33281A), Color(0xFF140F0A))
    5 -> listOf(Color(0xFF1A282E), Color(0xFF0C1215))
    6 -> listOf(Color(0xFF241E36), Color(0xFF0F0D19))
    else -> listOf(Color(0xFF1B2634), Color(0xFF0D1218))
}
