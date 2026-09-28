package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
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
import cn.mafei.cmp.core.domain.ProviderKind
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

enum class SavedAccountDeleteAffordance {
    LongPress,
    Button,
}

data class SavedAccountListColors(
    val groupTitle: Color,
    val container: Color,
    val divider: Color,
    val title: Color,
    val subtitle: Color,
    val trailingIcon: Color,
    val currentBackground: Color,
    val currentContent: Color,
    val passwordBackground: Color,
    val passwordContent: Color,
)

object SavedAccountListDefaults {
    @Composable
    fun colors(
        groupTitle: Color = MaterialTheme.colorScheme.onSurface,
        container: Color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
        divider: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f),
        title: Color = MaterialTheme.colorScheme.onSurface,
        subtitle: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingIcon: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        currentBackground: Color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
        currentContent: Color = MaterialTheme.colorScheme.primary,
        passwordBackground: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .92f),
        passwordContent: Color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
    ) = SavedAccountListColors(
        groupTitle = groupTitle,
        container = container,
        divider = divider,
        title = title,
        subtitle = subtitle,
        trailingIcon = trailingIcon,
        currentBackground = currentBackground,
        currentContent = currentContent,
        passwordBackground = passwordBackground,
        passwordContent = passwordContent,
    )
}

/**
 * The single saved-account list implementation used by signed-out history and signed-in account
 * switching. Route owners supply navigation and confirmation; grouping, row layout and deletion
 * feedback stay here so the two surfaces cannot drift apart.
 */
@Composable
fun MaFeiSavedAccountList(
    items: List<SavedAccountListItem>,
    enabled: Boolean,
    busyKey: String?,
    deletingKey: String?,
    deleteAffordance: SavedAccountDeleteAffordance,
    onAccount: (SavedAccountListItem) -> Unit,
    onDelete: (SavedAccountListItem) -> Unit,
    modifier: Modifier = Modifier,
    showCurrentLabel: Boolean = true,
    showRememberedPassword: Boolean = true,
    colors: SavedAccountListColors = SavedAccountListDefaults.colors(),
    emptyContent: @Composable () -> Unit = {},
) {
    // Persistence refreshes can remove a row before the animation has rendered. Retain one visual
    // snapshot until the shared dissolve animation finishes, then converge to the source list.
    var renderedItems by remember { mutableStateOf(items) }
    var dissolvingKeys by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(items, dissolvingKeys) {
        if (dissolvingKeys.isEmpty()) renderedItems = items
    }
    LaunchedEffect(deletingKey) {
        val key = deletingKey ?: return@LaunchedEffect
        if (renderedItems.none { it.key == key }) {
            dissolvingKeys = dissolvingKeys - key
            renderedItems = items
            return@LaunchedEffect
        }
        dissolvingKeys = dissolvingKeys + key
        delay(DeleteDissolveMillis * 3L)
        if (key in dissolvingKeys) {
            dissolvingKeys = dissolvingKeys - key
            renderedItems = items
        }
    }
    val groups = remember(renderedItems) { savedAccountListGroups(renderedItems) }
    if (groups.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { emptyContent() }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(groups, key = { it.key }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    group.title,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = colors.groupTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(
                    Modifier.fillMaxWidth().background(colors.container, RoundedCornerShape(20.dp)),
                ) {
                    group.items.forEachIndexed { index, item ->
                        SavedAccountRow(
                            item = item,
                            enabled = enabled,
                            busy = busyKey == item.key,
                            dissolving = item.key in dissolvingKeys,
                            deleteAffordance = deleteAffordance,
                            showCurrentLabel = showCurrentLabel,
                            showRememberedPassword = showRememberedPassword,
                            colors = colors,
                            onClick = { onAccount(item) },
                            onDelete = { onDelete(item) },
                            onDissolved = {
                                dissolvingKeys = dissolvingKeys - item.key
                                if (dissolvingKeys.isEmpty()) renderedItems = items
                            },
                        )
                        if (index != group.items.lastIndex) {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp)
                                    .background(colors.divider),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedAccountRow(
    item: SavedAccountListItem,
    enabled: Boolean,
    busy: Boolean,
    dissolving: Boolean,
    deleteAffordance: SavedAccountDeleteAffordance,
    showCurrentLabel: Boolean,
    showRememberedPassword: Boolean,
    colors: SavedAccountListColors,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDissolved: () -> Unit,
) {
    val interaction = remember(item.key) { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dissolve = remember(item.key) { Animatable(0f) }
    LaunchedEffect(dissolving) {
        if (dissolving) {
            dissolve.animateTo(1f, tween(DeleteDissolveMillis, easing = LinearEasing))
            onDissolved()
        }
    }
    val progress = dissolve.value
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
            Modifier.fillMaxWidth().clipToBounds().graphicsLayer {
                alpha = 1f - progress
                translationY = -8.dp.toPx() * progress
                scaleX = 1f - .02f * progress
                scaleY = 1f - .02f * progress
            }.combinedClickable(
                enabled = enabled && !busy,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClickLabel = if (
                    deleteAffordance == SavedAccountDeleteAffordance.LongPress && !item.isCurrent
                ) "删除账号" else null,
                onLongClick = if (
                    deleteAffordance == SavedAccountDeleteAffordance.LongPress && !item.isCurrent
                ) onDelete else null,
            ).drawWithContent {
                drawContent()
                if (hovered && enabled && !busy) drawRect(Color.White.copy(alpha = .045f))
            }.alpha(if (enabled) 1f else .52f).padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(
                    if (item.provider == ProviderKind.Jellyfin) MaFeiLegacyDrawables.jellyfinLogo
                    else MaFeiLegacyDrawables.feiniuLogo,
                ),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.displayName,
                        modifier = Modifier.weight(1f),
                        color = colors.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showCurrentLabel && item.isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        AccountTag(
                            text = "当前账号",
                            background = colors.currentBackground,
                            content = colors.currentContent,
                            contentDescription = "当前账号",
                        )
                    }
                }
                Text(
                    item.address,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            if (showRememberedPassword && item.hasRememberedPassword) {
                AccountTag(
                    text = "密",
                    background = colors.passwordBackground,
                    content = colors.passwordContent,
                    contentDescription = "已保存密码",
                )
                Spacer(Modifier.width(12.dp))
            }
            when {
                busy -> MaFeiLoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    style = MaFeiLoadingStyle.Inline,
                    tint = colors.trailingIcon,
                )

                deleteAffordance == SavedAccountDeleteAffordance.Button && !item.isCurrent -> Box(
                    Modifier.size(32.dp).mafeiInteractive(
                        onClick = { if (enabled) onDelete() },
                        radiusDp = 16f,
                        label = "删除账号",
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.delete),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(colors.trailingIcon),
                    )
                }

                else -> Image(
                    painter = painterResource(MaFeiLegacyDrawables.arrowRight),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).alpha(.50f),
                    colorFilter = ColorFilter.tint(colors.trailingIcon),
                )
            }
        }
        if (progress > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                for (particle in particles) {
                    val life = ((progress - particle.delay) / .55f).coerceIn(0f, 1f)
                    if (life <= 0f) continue
                    drawCircle(
                        color = colors.title.copy(alpha = (1f - life) * particle.alpha),
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

@Composable
private fun AccountTag(
    text: String,
    background: Color,
    content: Color,
    contentDescription: String,
) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private const val DeleteDissolveMillis = 560
private const val DissolveParticleCount = 72

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
