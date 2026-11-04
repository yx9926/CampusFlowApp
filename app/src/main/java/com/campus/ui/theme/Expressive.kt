package com.campus.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

// 图标使用统一的24单位坐标系，尺寸和笔触随组件缩放。
enum class CampusSymbol {
    Search,
    Pin,
    Grid,
    Person,
    Review,
    Arrow,
    Back,
    Settings,
    Refresh,
    Close,
    Plus,
    Check,
    Server,
    Building,
    Device,
    Shield,
    History,
    Sun,
    Logout,
    Clock,
    Star,
    People,
    Sound,
    Filter,
    Leaf,
    Down,
    Info,
}

@Composable
fun CampusIcon(
    symbol: CampusSymbol,
    modifier: Modifier = Modifier,
    description: String? = null,
    tint: Color = LocalContentColor.current,
) {
    Canvas(
        modifier.size(CampusComponents.Icon).semantics {
            if (description != null) contentDescription = description
        },
    ) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(
                    tint,
                    Offset(x, y),
                    Offset(x2, y2),
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Round,
                )
            fun box(x: Float, y: Float, width: Float, height: Float, radius: Float = 2f) =
                drawRoundRect(
                    tint,
                    Offset(x, y),
                    Size(width, height),
                    androidx.compose.ui.geometry.CornerRadius(radius),
                    style = stroke,
                )
            fun curve(vararg points: Float) {
                val path =
                    Path().apply {
                        moveTo(points[0], points[1])
                        var index = 2
                        while (index < points.size) {
                            lineTo(points[index], points[index + 1])
                            index += 2
                        }
                    }
                drawPath(path, tint, style = stroke)
            }
            when (symbol) {
                CampusSymbol.Info -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    drawCircle(tint, 1f, Offset(12f, 7f))
                    line(12f, 11f, 12f, 17f)
                }
                CampusSymbol.Search -> {
                    drawCircle(tint, 6.8f, Offset(10f, 10f), style = stroke)
                    line(15f, 15f, 21f, 21f)
                }
                CampusSymbol.Pin -> {
                    val path =
                        Path().apply {
                            moveTo(12f, 22f)
                            cubicTo(9f, 18f, 4f, 13f, 4f, 9f)
                            cubicTo(4f, -1f, 20f, -1f, 20f, 9f)
                            cubicTo(20f, 13f, 15f, 18f, 12f, 22f)
                        }
                    drawPath(path, tint, style = stroke)
                    drawCircle(tint, 2.5f, Offset(12f, 9f), style = stroke)
                }
                CampusSymbol.Grid -> {
                    box(3f, 3f, 7f, 7f)
                    box(14f, 3f, 7f, 7f)
                    box(3f, 14f, 7f, 7f)
                    box(14f, 14f, 7f, 7f)
                }
                CampusSymbol.Person,
                CampusSymbol.People -> {
                    drawCircle(tint, 3.5f, Offset(11f, 7f), style = stroke)
                    drawArc(
                        tint,
                        180f,
                        180f,
                        false,
                        Offset(3f, 13f),
                        Size(16f, 15f),
                        style = stroke,
                    )
                    if (symbol == CampusSymbol.People) {
                        drawArc(
                            tint,
                            -65f,
                            130f,
                            false,
                            Offset(14f, 3f),
                            Size(7f, 7f),
                            style = stroke,
                        )
                        line(20f, 15f, 22f, 20f)
                    }
                }
                CampusSymbol.Review -> {
                    curve(4f, 4f, 20f, 4f, 20f, 16f, 9f, 16f, 4f, 21f, 4f, 4f)
                    line(8f, 8f, 16f, 8f)
                    line(8f, 12f, 13f, 12f)
                }
                CampusSymbol.Arrow -> {
                    line(4f, 12f, 20f, 12f)
                    curve(14f, 6f, 20f, 12f, 14f, 18f)
                }
                CampusSymbol.Back -> {
                    line(4f, 12f, 20f, 12f)
                    curve(10f, 6f, 4f, 12f, 10f, 18f)
                }
                CampusSymbol.Down -> curve(6f, 9f, 12f, 15f, 18f, 9f)
                CampusSymbol.Settings,
                CampusSymbol.Sun -> {
                    drawCircle(
                        tint,
                        if (symbol == CampusSymbol.Sun) 4f else 3f,
                        Offset(12f, 12f),
                        style = stroke,
                    )
                    for (index in 0..7) {
                        val angle = index * Math.PI / 4
                        val inner = if (symbol == CampusSymbol.Sun) 8f else 7f
                        line(
                            12f + kotlin.math.cos(angle).toFloat() * inner,
                            12f + kotlin.math.sin(angle).toFloat() * inner,
                            12f + kotlin.math.cos(angle).toFloat() * 10f,
                            12f + kotlin.math.sin(angle).toFloat() * 10f,
                        )
                    }
                    if (symbol == CampusSymbol.Settings)
                        drawCircle(tint, 7f, Offset(12f, 12f), style = stroke)
                }
                CampusSymbol.Refresh,
                CampusSymbol.History -> {
                    drawArc(tint, -45f, 285f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    curve(20f, 3f, 20f, 9f, 14f, 9f)
                    if (symbol == CampusSymbol.History) curve(12f, 8f, 12f, 13f, 15f, 15f)
                }
                CampusSymbol.Close -> {
                    line(6f, 6f, 18f, 18f)
                    line(18f, 6f, 6f, 18f)
                }
                CampusSymbol.Plus -> {
                    line(5f, 12f, 19f, 12f)
                    line(12f, 5f, 12f, 19f)
                }
                CampusSymbol.Check -> curve(5f, 12f, 10f, 17f, 20f, 6f)
                CampusSymbol.Server -> {
                    box(3f, 3f, 18f, 8f)
                    box(3f, 14f, 18f, 8f)
                    drawCircle(tint, 1f, Offset(7f, 7f))
                    drawCircle(tint, 1f, Offset(7f, 18f))
                    line(12f, 7f, 17f, 7f)
                    line(12f, 18f, 17f, 18f)
                }
                CampusSymbol.Building -> {
                    box(5f, 3f, 14f, 18f)
                    line(2f, 21f, 22f, 21f)
                    for (x in listOf(9f, 15f)) for (y in listOf(7f, 12f)) line(x, y, x, y + 1f)
                    curve(10f, 21f, 10f, 17f, 14f, 17f, 14f, 21f)
                }
                CampusSymbol.Device -> {
                    box(6f, 5f, 12f, 14f)
                    for (v in listOf(9f, 15f)) {
                        line(v, 2f, v, 5f)
                        line(v, 19f, v, 22f)
                        line(3f, v, 6f, v)
                        line(18f, v, 21f, v)
                    }
                    drawCircle(tint, 2f, Offset(12f, 12f), style = stroke)
                }
                CampusSymbol.Shield -> {
                    curve(12f, 2f, 21f, 6f, 20f, 15f, 12f, 22f, 4f, 15f, 3f, 6f, 12f, 2f)
                    curve(8f, 12f, 11f, 15f, 16f, 9f)
                }
                CampusSymbol.Logout -> {
                    curve(10f, 3f, 4f, 3f, 4f, 21f, 10f, 21f)
                    line(9f, 12f, 21f, 12f)
                    curve(16f, 7f, 21f, 12f, 16f, 17f)
                }
                CampusSymbol.Clock -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    curve(12f, 6f, 12f, 12f, 16f, 14f)
                }
                CampusSymbol.Star -> {
                    val path = Path()
                    for (i in 0..9) {
                        val a = -Math.PI / 2 + i * Math.PI / 5
                        val r = if (i % 2 == 0) 10f else 4.5f
                        val x = 12 + kotlin.math.cos(a).toFloat() * r
                        val y = 12 + kotlin.math.sin(a).toFloat() * r
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, tint, style = stroke)
                }
                CampusSymbol.Sound -> {
                    curve(3f, 9f, 7f, 9f, 12f, 5f, 12f, 19f, 7f, 15f, 3f, 15f, 3f, 9f)
                    drawArc(tint, -60f, 120f, false, Offset(8f, 6f), Size(12f, 12f), style = stroke)
                    drawArc(tint, -60f, 120f, false, Offset(6f, 2f), Size(20f, 20f), style = stroke)
                }
                CampusSymbol.Filter -> {
                    line(3f, 6f, 21f, 6f)
                    line(3f, 12f, 21f, 12f)
                    line(3f, 18f, 21f, 18f)
                    drawCircle(tint, 2f, Offset(8f, 6f))
                    drawCircle(tint, 2f, Offset(16f, 12f))
                    drawCircle(tint, 2f, Offset(10f, 18f))
                }
                CampusSymbol.Leaf -> {
                    val path =
                        Path().apply {
                            moveTo(3f, 21f)
                            cubicTo(2f, 5f, 9f, 2f, 21f, 3f)
                            cubicTo(22f, 17f, 14f, 22f, 3f, 21f)
                        }
                    drawPath(path, tint, style = stroke)
                    line(4f, 20f, 15f, 9f)
                }
            }
        }
    }
}

@Composable
fun ScreenHeading(
    title: String,
    eyebrow: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall),
        ) {
            if (eyebrow != null)
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ExpressiveHero(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = CampusSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
