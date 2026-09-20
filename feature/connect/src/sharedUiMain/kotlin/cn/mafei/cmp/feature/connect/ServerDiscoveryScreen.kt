package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiActionBar
import kotlinx.coroutines.CancellationException

@Composable
fun ServerDiscoveryScreen(
    loadSavedServers: suspend () -> List<ServerDiscoverySuggestion>,
    discoverServers: suspend () -> List<ServerDiscoverySuggestion>,
    onBack: () -> Unit,
    onSelectServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeArea = currentMaFeiSafeArea()
    val backdrop = rememberGlassBackdrop()
    var suggestions by remember { mutableStateOf(emptyList<ServerDiscoverySuggestion>()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        errorText = ""
        try {
            val saved = loadSavedServers()
            suggestions = saved
            suggestions = mergeServerDiscoverySuggestions(saved, discoverServers())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorText = "局域网扫描失败，请检查网络权限和当前网络环境"
        } finally {
            loading = false
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().captureGlassBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            ServerDiscoveryContent(
                suggestions = suggestions,
                loading = loading,
                errorText = errorText,
                onSelectServer = onSelectServer,
                modifier = Modifier.fillMaxSize().padding(
                    start = safeArea.start + 16.dp,
                    top = safeArea.top + 64.dp,
                    end = safeArea.end + 16.dp,
                    bottom = safeArea.bottom + 16.dp,
                ),
            )
        }
        MaFeiActionBar(
            title = "局域网内设备",
            onBack = onBack,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun ServerDiscoveryContent(
    suggestions: List<ServerDiscoverySuggestion>,
    loading: Boolean,
    errorText: String,
    onSelectServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "正在扫描局域网设备...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        errorText.isNotEmpty() -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "扫描失败",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                errorText,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        suggestions.isNotEmpty() -> Column(modifier, horizontalAlignment = Alignment.Start) {
            Text(
                "找到${suggestions.size}个设备",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions, key = { "${it.type}:${it.address}" }) { suggestion ->
                    ServerDiscoveryItem(suggestion, onSelectServer)
                }
            }
        }

        else -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "未发现局域网设备",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "请确认设备与 Jellyfin 服务器在同一网络下，或直接手动输入服务器地址。",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ServerDiscoveryItem(
    suggestion: ServerDiscoverySuggestion,
    onSelectServer: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) { onSelectServer(suggestion.address) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            suggestion.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            suggestion.address,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
