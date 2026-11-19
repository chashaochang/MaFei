package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.core.domain.ProviderKind
import org.jetbrains.compose.resources.painterResource

@Composable
fun ProviderMenuOptions(
    onProvider: (ProviderKind) -> Unit,
    modifier: Modifier = Modifier,
    selectedProvider: ProviderKind? = null,
    rowHeight: Dp = 44.dp,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .then(if (selectedProvider == null) Modifier else Modifier.selectableGroup())
            .padding(vertical = 4.dp),
    ) {
        ProviderKind.entries.forEach { provider ->
            val current = provider == selectedProvider
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = rowHeight)
                    .mafeiInteractive(
                        role = if (selectedProvider == null) Role.Button else Role.RadioButton,
                        radiusDp = 6f,
                        onClick = { onProvider(provider) },
                    )
                    .semantics { if (selectedProvider != null) selected = current }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(providerLogo(provider)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = if (provider == ProviderKind.Feiniu) ColorFilter.tint(colors.onSurface) else null,
                )
                Text(
                    providerName(provider),
                    modifier = Modifier.weight(1f),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (current) {
                    Image(
                        painterResource(MaFeiLegacyDrawables.watched),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(colors.primary),
                    )
                }
            }
        }
    }
}
