package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.LegacyTransientToast
import cn.mafei.cmp.common.ui.MaFeiActionBarScaffold
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.mafeiInteractive
import org.jetbrains.compose.resources.painterResource

enum class FeiniuConnectionPreference(val title: String) {
    Speed("速度优先"),
    Public("公网优先"),
    Relay("中转优先"),
}

/** Mechanical Compose translation of ArkUI `FeiniuConnectionPreferencePage`. */
@Composable
fun FeiniuConnectionPreferenceScreen(
    selectedPreference: FeiniuConnectionPreference,
    onSelect: (FeiniuConnectionPreference) -> Unit,
    onBack: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val safeArea = LocalMaFeiSafeArea.current
    Box(modifier.fillMaxSize()) {
        MaFeiActionBarScaffold(
            title = "飞牛连接策略",
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .padding(bottom = safeArea.bottom.coerceAtLeast(32.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)),
                ) {
                    FeiniuConnectionPreference.entries.forEachIndexed { index, option ->
                        Row(
                            Modifier.fillMaxWidth().height(56.dp)
                                .mafeiInteractive(
                                    onClick = { onSelect(option) },
                                    radiusDp = 0f,
                                    label = option.title,
                                )
                                .padding(horizontal = 16.dp)
                                .semantics { selected = option == selectedPreference },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option.title,
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (option == selectedPreference) {
                                Image(
                                    painter = painterResource(MaFeiLegacyDrawables.watched),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                )
                            } else {
                                Box(Modifier.size(20.dp))
                            }
                        }
                        if (index < FeiniuConnectionPreference.entries.lastIndex) {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(.5.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)),
                            )
                        }
                    }
                }
            }
        }
        LegacyTransientToast(
            message = errorMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
