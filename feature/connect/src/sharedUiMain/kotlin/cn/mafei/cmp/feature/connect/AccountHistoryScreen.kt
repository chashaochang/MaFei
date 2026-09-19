package cn.mafei.cmp.feature.connect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.connect.generated.resources.Res
import cn.mafei.cmp.feature.connect.generated.resources.provider_feiniu
import cn.mafei.cmp.feature.connect.generated.resources.provider_jellyfin
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

@Composable
fun AccountHistoryScreen(
    items: List<ConnectionHistoryItem>,
    loading: Boolean,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelected: (ConnectionHistoryItem) -> Unit,
    /** Non-null while that row's deletion is in flight. */
    deletingKey: String?,
    /**
     * The user asked to delete that row (long press). Confirmation and the delete
     * itself belong to the owner of the window, so the material stays the
     * window's and the dialog cannot be trapped inside a pane.
     */
    onRequestDelete: (ConnectionHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 窗口背景层的源（含影视墙），由调用方铺好并捕获。两栏布局必须传：本栏宽度会随
     * 布局动画变化，各自捕获本栏内容既会让两栏的墙错位、也会把模糊拉花。为 null 时
     * 本面板自己铺一层墙并捕获。
     */
    pageBackdrop: GlassBackdrop? = null,
) {
    val safeArea = currentMaFeiSafeArea()
    val ownBackdrop = if (pageBackdrop == null) rememberGlassBackdrop() else null
    val windowBackdrop = pageBackdrop ?: requireNotNull(ownBackdrop)
    // 删除时的粒子消散。数据会先一步把行删掉，所以这里留一份渲染快照：正在消散的行
    // 即使已从数据里消失，也继续按快照渲染，等粒子放完再对齐真实数据 —— 否则动画还
    // 没开始，行就已经没了。
    var renderedRows by remember { mutableStateOf(items) }
    var dissolvingKeys by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(items, dissolvingKeys) {
        if (dissolvingKeys.isEmpty()) renderedRows = items
    }
    LaunchedEffect(deletingKey) {
        val key = deletingKey ?: return@LaunchedEffect
        if (renderedRows.none { it.key == key }) {
            // 数据比动画先一步删掉了行：没有可消散的对象，直接对齐，不要把快照冻住。
            dissolvingKeys = dissolvingKeys - key
            renderedRows = items
            return@LaunchedEffect
        }
        dissolvingKeys = dissolvingKeys + key
        // 保险：行没有回调（例如中途被移除）也不能把快照永久冻住。
        delay(DeleteDissolveMillis * 3L)
        if (key in dissolvingKeys) {
            dissolvingKeys = dissolvingKeys - key
            renderedRows = items
        }
    }
    Box(modifier.fillMaxSize()) {
        if (ownBackdrop != null) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(ownBackdrop)) {
                ConnectAtmosphere()
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)))
            }
        }
        Column(
            Modifier.fillMaxSize().padding(
                start = safeArea.start,
                // 让位的必须是 `MaFeiActionBar` 的整条高度（64dp），不是 56dp：少让 8dp
                // 会让列表第一行的顶端塞到标题栏底下。
                top = safeArea.top + 64.dp,
                end = safeArea.end,
                bottom = safeArea.bottom,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HistoryContent(
                items = renderedRows,
                loading = loading,
                error = error,
                enabled = !busy && deletingKey == null,
                dissolvingKeys = dissolvingKeys,
                backdrop = windowBackdrop,
                onRetry = onRetry,
                onSelected = onSelected,
                onRequestDelete = onRequestDelete,
                onDissolved = { key ->
                    dissolvingKeys = dissolvingKeys - key
                    if (dissolvingKeys.isEmpty()) renderedRows = items
                },
                modifier = Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        MaFeiActionBar(
            title = "账号历史",
            onBack = onBack,
            backdrop = windowBackdrop,
            actionCount = 1,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (busy || deletingKey != null) {
                        CircularProgressIndicator(
                            Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun HistoryContent(
    items: List<ConnectionHistoryItem>,
    loading: Boolean,
    error: String?,
    enabled: Boolean,
    dissolvingKeys: Set<String>,
    backdrop: GlassBackdrop,
    onRetry: () -> Unit,
    onSelected: (ConnectionHistoryItem) -> Unit,
    onRequestDelete: (ConnectionHistoryItem) -> Unit,
    onDissolved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.size(28.dp),
                strokeWidth = 2.4.dp,
                color = Color.White.copy(alpha = .82f),
            )
        }

        error != null -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                error,
                color = Color.White.copy(alpha = .78f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))
            LiquidButton(onClick = onRetry, backdrop = backdrop) {
                Text("重试", color = Color.White.copy(alpha = .94f), fontWeight = FontWeight.Medium)
            }
        }

        items.isEmpty() -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(MaFeiLegacyDrawables.history),
                contentDescription = null,
                modifier = Modifier.size(42.dp).alpha(.36f),
                colorFilter = ColorFilter.tint(Color.White),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "暂无历史账号",
                color = Color.White.copy(alpha = .60f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        else -> {
            val groups = remember(items) {
                items.groupBy { item ->
                    HistoryGroupKey(
                        provider = item.provider,
                        address = item.address,
                        title = item.serverName.ifBlank { item.address },
                    )
                }.entries.toList()
            }
            LazyColumn(
                modifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(groups, key = { "${it.key.provider}:${it.key.address}" }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            group.key.title,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = Color.White.copy(alpha = .70f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Color.Black.copy(alpha = .30f), RoundedCornerShape(20.dp)),
                        ) {
                            group.value.forEachIndexed { index, item ->
                                HistoryRow(
                                    item = item,
                                    enabled = enabled,
                                    dissolving = item.key in dissolvingKeys,
                                    onClick = { onSelected(item) },
                                    onLongClick = { onRequestDelete(item) },
                                    onDissolved = { onDissolved(item.key) },
                                )
                                if (index != group.value.lastIndex) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp)
                                            .background(Color.White.copy(alpha = .10f)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HistoryGroupKey(
    val provider: ProviderKind,
    val address: String,
    val title: String,
)

/**
 * Long press asks to delete this saved account. The account in use is the one the
 * screen is signed in as, so its row only offers opening it.
 *
 * A confirmed deletion dissolves the row instead of blinking it away: the content
 * fades and lifts, a burst of dust drifts up out of it, and the row's height then
 * collapses so the list closes the gap. [onDissolved] releases the screen's
 * snapshot for this key once the particles are gone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    item: ConnectionHistoryItem,
    enabled: Boolean,
    dissolving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDissolved: () -> Unit,
) {
    val interaction = remember(item.key) { MutableInteractionSource() }
    val dissolve = remember(item.key) { Animatable(0f) }
    LaunchedEffect(dissolving) {
        if (dissolving) {
            dissolve.animateTo(1f, tween(DeleteDissolveMillis, easing = LinearEasing))
            onDissolved()
        }
    }
    val progress = dissolve.value
    // 高度最后才收：收得太早会先看到内容被裁掉、粒子被压扁。
    val collapse = ((progress - .82f) / .18f).coerceIn(0f, 1f)
    val particles = remember(item.key) { dissolveParticles(item.key) }
    Box(
        Modifier.fillMaxWidth().then(
            if (collapse <= 0f) Modifier else Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * (1f - collapse)).toInt()) {
                    placeable.place(0, 0)
                }
            },
        ),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clipToBounds()
                .graphicsLayer {
                    alpha = 1f - progress
                    translationY = -8.dp.toPx() * progress
                    scaleX = 1f - .02f * progress
                    scaleY = 1f - .02f * progress
                }
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClickLabel = if (item.isCurrent) null else "删除账号",
                    onLongClick = if (item.isCurrent) null else onLongClick,
                )
                .alpha(if (enabled) 1f else .52f)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Image(
            painter = painterResource(
                if (item.provider == ProviderKind.Jellyfin) Res.drawable.provider_jellyfin
                else Res.drawable.provider_feiniu,
            ),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.displayName,
                color = Color.White.copy(alpha = .95f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.address,
                color = Color.White.copy(alpha = .55f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (item.hasRememberedPassword) {
            RememberedPasswordTag()
            Spacer(Modifier.width(12.dp))
        }
        Image(
            painter = painterResource(MaFeiLegacyDrawables.arrowRight),
            contentDescription = null,
            modifier = Modifier.size(18.dp).alpha(.50f),
            colorFilter = ColorFilter.tint(Color.White),
        )
        }
        if (progress > 0f) {
            Canvas(Modifier.matchParentSize()) {
                for (particle in particles) {
                    // 每颗粒子错峰起跑，越往后越淡。
                    val life = ((progress - particle.delay) / .55f).coerceIn(0f, 1f)
                    if (life <= 0f) continue
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - life) * particle.alpha),
                        radius = particle.radius * density,
                        center = Offset(
                            x = particle.x * size.width + particle.drift * life,
                            y = particle.y * size.height - particle.travel * life,
                        ),
                    )
                }
            }
        }
    }
}

private const val DeleteDissolveMillis = 560
private const val DissolveParticleCount = 72

/** 相对坐标的一粒尘埃；种子固定，所以同一行的粒子每次都一样、不会闪。 */
private data class DissolveParticle(
    val x: Float,
    val y: Float,
    val travel: Float,
    val drift: Float,
    val radius: Float,
    val delay: Float,
    val alpha: Float,
)

private fun dissolveParticles(key: String): List<DissolveParticle> {
    val random = Random(key.hashCode())
    return List(DissolveParticleCount) {
        DissolveParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            travel = 20f + random.nextFloat() * 52f,
            drift = (random.nextFloat() - .5f) * 34f,
            radius = 1f + random.nextFloat() * 1.7f,
            delay = random.nextFloat() * .38f,
            alpha = .22f + random.nextFloat() * .48f,
        )
    }
}

@Composable
private fun RememberedPasswordTag() {
    // 存了密码的账号打一个「密」字标签。
    // 原来是一个白圆点 + 对号：对号在这套界面里已经被「已看 / 已收藏」占用，用户读不出来
    // 它表示"密码已保存"；而没存密码时的空圈更是零信息量，只会让人以为是没勾选的复选框。
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = .92f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "已保存密码" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "密",
            color = Color.Black.copy(alpha = .88f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
