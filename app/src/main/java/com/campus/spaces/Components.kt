@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.campus.spaces

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.campus.ui.theme.*
import java.util.Locale

@Composable
fun CampusPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CampusElevation.Hairline, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = CampusElevation.Card,
    ) {
        Column(
            Modifier.padding(CampusSpacing.ExtraLarge),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
            content = content,
        )
    }
}

@Composable
fun SupportingText(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun StatusChip(text: String, warning: Boolean = false) {
    val color = if (warning) CampusTheme.colors.warning else MaterialTheme.colorScheme.primary
    Surface(color = color.copy(alpha = .10f), contentColor = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text,
            Modifier.padding(
                horizontal = CampusSpacing.Medium,
                vertical = CampusSpacing.ExtraSmall,
            ),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit, enabled: Boolean = true) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(CampusSpacing.ExtraLarge),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
        ) {
            Text("加载失败", style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry, enabled = enabled) {
                CampusIcon(CampusSymbol.Refresh)
                Spacer(Modifier.width(CampusSpacing.Small))
                Text("重试")
            }
        }
    }
}

@Composable
fun EmptyPanel(title: String, description: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = CampusSpacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
    ) {
        Box(Modifier.size(CampusComponents.EmptyArt), contentAlignment = Alignment.Center) {
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {}
            CampusIcon(
                CampusSymbol.Leaf,
                Modifier.size(CampusComponents.Avatar),
                tint = MaterialTheme.colorScheme.primary,
            )
            Surface(
                Modifier.align(Alignment.BottomEnd).size(CampusSpacing.MinimumTouchTarget),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CampusIcon(
                        CampusSymbol.Search,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (description.isNotBlank()) Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        CampusTonalButton(
            onClick = onAction,
            modifier = Modifier.heightIn(min = CampusSpacing.MinimumTouchTarget),
        ) {
            Text(action)
            Spacer(Modifier.width(CampusSpacing.Small))
            CampusIcon(CampusSymbol.Arrow)
        }
    }
}

@Composable
fun LoadingCards() {
    val transition = rememberInfiniteTransition(label = "loading")
    val opacity by
        transition.animateFloat(
            .35f,
            .75f,
            infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeleton",
        )
    Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large)) {
        repeat(2) {
            CampusPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
                ) {
                    LoadingIndicator(Modifier.size(CampusSpacing.MinimumTouchTarget))
                    Box(
                        Modifier.fillMaxWidth(.65f)
                            .height(CampusSpacing.ExtraLarge)
                            .alpha(opacity)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.shapes.small,
                            ),
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height(CampusComponents.SkeletonHeight)
                        .alpha(opacity)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.shapes.small,
                        ),
                )
            }
        }
    }
}

@Composable
fun StatusMetrics(status: SpaceStatus, elapsed: Long) {
    val peopleValid = status.peopleRecent(elapsed)
    val noiseValid = status.noiseRecent(elapsed)
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < CampusLayout.MetricsBreakpoint || largeText
        val people: @Composable (Modifier) -> Unit = { modifier ->
            MetricTile(
                "人数",
                if (peopleValid) status.people.toString() else "—",
                if (peopleValid) "/ ${status.capacity}" else "未知",
                CampusSymbol.People,
                modifier,
            )
        }
        val noise: @Composable (Modifier) -> Unit = { modifier ->
            MetricTile(
                "环境",
                if (noiseValid) decimal(status.typicalNoise!!) else "—",
                if (noiseValid) "dB" else if (status.deviceState == "OFFLINE") "离线" else "未知",
                CampusSymbol.Sound,
                modifier,
            )
        }
        if (stacked)
            Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
                people(Modifier.fillMaxWidth())
                noise(Modifier.fillMaxWidth())
            }
        else
            Row(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
                people(Modifier.weight(1f))
                noise(Modifier.weight(1f))
            }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    symbol: CampusSymbol,
    modifier: Modifier,
) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(CampusSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
            ) {
                CampusIcon(symbol, Modifier.size(CampusComponents.SmallIcon))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = CampusSpacing.ExtraSmall),
                )
            }
        }
    }
}

@Composable
fun SpaceCardView(card: SpaceCard, elapsed: Long, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(CampusElevation.Hairline, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CampusElevation.Card),
    ) {
        Column(
            Modifier.padding(CampusSpacing.ExtraLarge),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Box(
                        Modifier.size(CampusSpacing.MinimumTouchTarget),
                        contentAlignment = Alignment.Center,
                    ) {
                        CampusIcon(CampusSymbol.Building, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall),
                ) {
                    Text(
                        card.space.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SupportingText(
                        "${spaceTypes[card.space.type] ?: card.space.type} · ${card.distance.toInt()} m",
                        maxLines = 1,
                    )
                }
                CampusIcon(CampusSymbol.Arrow, tint = MaterialTheme.colorScheme.primary)
            }
            StatusMetrics(card.status, elapsed)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(if (card.openNow) "开放中" else "休息中", warning = !card.openNow)
                if (card.status.stale(elapsed)) StatusChip("待更新", warning = true)
                Text(
                    card.space.facilities.mapNotNull { facilityNames[it] }.joinToString(" · "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.summary.count > 0) {
                    CampusIcon(
                        CampusSymbol.Star,
                        Modifier.size(CampusComponents.SmallIcon),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        decimal(card.summary.environment ?: 0.0),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun PagingFooter(loading: Boolean, hasMore: Boolean, onMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(CampusSpacing.Large), contentAlignment = Alignment.Center) {
        if (loading) LoadingIndicator(Modifier.size(CampusSpacing.MinimumTouchTarget))
        else if (hasMore) TextButton(onClick = onMore) { Text("加载更多") } else Unit
    }
}

fun decimal(value: Double) = String.format(Locale.CHINA, "%.1f", value)
