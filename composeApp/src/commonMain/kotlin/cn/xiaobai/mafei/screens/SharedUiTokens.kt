package cn.xiaobai.mafei.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppStatusTone {
    Neutral,
    Progress,
    Warning,
}

private object SharedUiDimens {
    val SectionCardRadius = 18.dp
    val StatusRadius = 16.dp
    val PillRadius = 999.dp
    val HeaderGap = 4.dp
    val HeaderSideGap = 10.dp
}

@Composable
fun AppSectionCard(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    shape: Shape = RoundedCornerShape(SharedUiDimens.SectionCardRadius),
    tonalElevation: Dp = if (secondary) 0.dp else 1.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (secondary) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (secondary) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        tonalElevation = tonalElevation,
        content = content,
    )
}

@Composable
fun AppPill(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val usePrimaryTone = emphasized || selected
    val pillModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = pillModifier,
        shape = RoundedCornerShape(SharedUiDimens.PillRadius),
        color = if (usePrimaryTone) {
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            }
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
        },
        contentColor = if (usePrimaryTone) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (usePrimaryTone) {
                MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.22f else 0.14f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            }
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun AppStatusCard(
    message: String,
    modifier: Modifier = Modifier,
    tone: AppStatusTone = AppStatusTone.Neutral,
    title: String? = null,
    supportingText: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val containerColor = when (tone) {
        AppStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        AppStatusTone.Progress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        AppStatusTone.Warning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
    }
    val contentColor = when (tone) {
        AppStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        AppStatusTone.Progress -> MaterialTheme.colorScheme.onSurfaceVariant
        AppStatusTone.Warning -> MaterialTheme.colorScheme.onErrorContainer
    }
    val alignTop = !title.isNullOrBlank() || !supportingText.isNullOrBlank() || action != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(SharedUiDimens.StatusRadius),
        border = BorderStroke(
            width = 1.dp,
            color = when (tone) {
                AppStatusTone.Neutral -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                AppStatusTone.Progress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                AppStatusTone.Warning -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.08f)
            }
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Box(
                        modifier = Modifier.padding(top = if (alignTop) 1.dp else 0.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        leading()
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!supportingText.isNullOrBlank()) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.9f),
                        )
                    }
                }
            }
            if (action != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    action()
                }
            }
        }
    }
}

@Composable
fun AppInlineTip(
    message: String,
    modifier: Modifier = Modifier,
    tone: AppStatusTone = AppStatusTone.Neutral,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val containerColor = when (tone) {
        AppStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        AppStatusTone.Progress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        AppStatusTone.Warning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
    }
    val contentColor = when (tone) {
        AppStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        AppStatusTone.Progress -> MaterialTheme.colorScheme.onSurfaceVariant
        AppStatusTone.Warning -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.dp,
            color = when (tone) {
                AppStatusTone.Neutral -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                AppStatusTone.Progress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                AppStatusTone.Warning -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.08f)
            }
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (action != null) 8.dp else 0.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Box(contentAlignment = Alignment.Center) {
                        leading()
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (action != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    action()
                }
            }
        }
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = if (trailing != null) {
                Modifier.fillMaxWidth(0.74f)
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(SharedUiDimens.HeaderGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.padding(start = SharedUiDimens.HeaderSideGap),
                contentAlignment = Alignment.TopEnd,
            ) {
                trailing()
            }
        }
    }
}

@Composable
fun AppHeaderRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    leadingEmphasized: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = if (trailing != null) {
                Modifier.fillMaxWidth(0.72f)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = if (leadingEmphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                },
                contentColor = if (leadingEmphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (leadingEmphasized) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    }
                ),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(SharedUiDimens.HeaderGap)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.padding(start = SharedUiDimens.HeaderSideGap),
                contentAlignment = Alignment.TopEnd,
            ) {
                trailing()
            }
        }
    }
}
