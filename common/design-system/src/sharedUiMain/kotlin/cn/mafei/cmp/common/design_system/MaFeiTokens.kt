package cn.mafei.cmp.common.design_system

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared product values, independent of platform, input source and glass backend. */
object MaFeiSpace {
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val regular = 16.dp
    val page = 20.dp
    val section = 24.dp
    val wide = 32.dp
    val touchTarget = 44.dp
}

object MaFeiShape {
    val poster = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(16.dp)
    val panel = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(28.dp)
    val capsule = RoundedCornerShape(50)
}

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

val MaFeiTypography = Typography(
    displayLarge = type(48, 56, FontWeight.Bold),
    displayMedium = type(40, 48, FontWeight.Bold),
    displaySmall = type(36, 44, FontWeight.Bold),
    headlineLarge = type(32, 40, FontWeight.Bold),
    headlineMedium = type(28, 36, FontWeight.SemiBold),
    headlineSmall = type(24, 32, FontWeight.SemiBold),
    titleLarge = type(21, 28, FontWeight.SemiBold),
    titleMedium = type(17, 24, FontWeight.SemiBold),
    titleSmall = type(15, 21, FontWeight.Medium),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 21),
    bodySmall = type(13, 19),
    labelLarge = type(14, 20, FontWeight.Medium),
    labelMedium = type(13, 18, FontWeight.Medium),
    labelSmall = type(12, 17, FontWeight.Medium),
)
