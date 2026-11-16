package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CosmeticCatalog
import com.example.data.model.CosmeticCategory
import com.example.data.model.CosmeticItem
import com.example.data.model.UserProfile
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun CyclistShowcaseCard(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    val bike = CosmeticCatalog.findById(profile.equippedBikeId)
    val helmet = CosmeticCatalog.findById(profile.equippedHelmetId)
    val trail = CosmeticCatalog.findById(profile.equippedTrailId)
    val badge = CosmeticCatalog.findById(profile.equippedBadgeId)

    val bikeColor = bike?.primaryColor ?: MaterialTheme.colorScheme.primary
    val bikeAccent = bike?.accentColor ?: MaterialTheme.colorScheme.primaryContainer
    val helmetColor = helmet?.primaryColor ?: MaterialTheme.colorScheme.secondary
    val trailColor = trail?.primaryColor ?: MaterialTheme.colorScheme.primary
    val badgeColor = badge?.primaryColor ?: MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F221A),
        tonalElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0C1E17),
                            bikeColor.copy(alpha = 0.25f),
                            Color(0xFF0E2920),
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.5.dp, trailColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val w = size.width
                        val h = size.height

                        // Equipped map trail wave underneath the wheels
                        val trailPath = Path().apply {
                            moveTo(0f, h * 0.84f)
                            cubicTo(w * 0.28f, h * 0.72f, w * 0.62f, h * 0.94f, w, h * 0.78f)
                        }
                        drawPath(
                            path = trailPath,
                            color = trailColor,
                            style = Stroke(width = 7f, cap = StrokeCap.Round),
                        )

                        // Rear & front wheels
                        val wheelRadius = w * 0.17f
                        val rearCenter = Offset(w * 0.24f, h * 0.68f)
                        val frontCenter = Offset(w * 0.76f, h * 0.68f)

                        drawCircle(
                            color = bikeAccent,
                            radius = wheelRadius,
                            center = rearCenter,
                            style = Stroke(width = 5f),
                        )
                        drawCircle(
                            color = bikeColor,
                            radius = wheelRadius * 0.35f,
                            center = rearCenter,
                        )
                        drawCircle(
                            color = bikeAccent,
                            radius = wheelRadius,
                            center = frontCenter,
                            style = Stroke(width = 5f),
                        )
                        drawCircle(
                            color = bikeColor,
                            radius = wheelRadius * 0.35f,
                            center = frontCenter,
                        )

                        // Bicycle diamond frame
                        val pedalHub = Offset(w * 0.48f, h * 0.68f)
                        val seatPost = Offset(w * 0.38f, h * 0.42f)
                        val headTube = Offset(w * 0.66f, h * 0.40f)
                        val framePath = Path().apply {
                            moveTo(rearCenter.x, rearCenter.y)
                            lineTo(pedalHub.x, pedalHub.y)
                            lineTo(headTube.x, headTube.y)
                            lineTo(seatPost.x, seatPost.y)
                            close()
                            moveTo(seatPost.x, seatPost.y)
                            lineTo(pedalHub.x, pedalHub.y)
                            moveTo(headTube.x, headTube.y)
                            lineTo(frontCenter.x, frontCenter.y)
                        }
                        drawPath(
                            path = framePath,
                            color = bikeColor,
                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )

                        // Stylized cyclist helmet floating above saddle
                        val helmetCenter = Offset(w * 0.54f, h * 0.22f)
                        drawArc(
                            color = helmetColor,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(helmetCenter.x - w * 0.14f, helmetCenter.y - h * 0.11f),
                            size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.22f),
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(helmetCenter.x - w * 0.12f, helmetCenter.y),
                            end = Offset(helmetCenter.x + w * 0.15f, helmetCenter.y),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Text(
                            text = badge?.name ?: "Roue Libre",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA7F3D0),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = bike?.name ?: "Urbain Menthe",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Casque : ${helmet?.name ?: "Classique"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(trailColor)
                        )
                        Text(
                            text = trail?.name ?: "Tracé Émeraude",
                            style = MaterialTheme.typography.labelSmall,
                            color = trailColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CosmeticPreviewThumbnail(
    item: CosmeticItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        item.primaryColor.copy(alpha = 0.22f),
                        item.accentColor.copy(alpha = 0.28f),
                    )
                )
            )
            .border(1.5.dp, item.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (item.category) {
            CosmeticCategory.BIKE -> Icon(
                imageVector = Icons.AutoMirrored.Rounded.DirectionsBike,
                contentDescription = item.name,
                tint = item.primaryColor,
                modifier = Modifier.size(34.dp),
            )
            CosmeticCategory.HELMET -> Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = item.name,
                tint = item.primaryColor,
                modifier = Modifier.size(32.dp),
            )
            CosmeticCategory.TRAIL -> Icon(
                imageVector = Icons.Rounded.Route,
                contentDescription = item.name,
                tint = item.primaryColor,
                modifier = Modifier.size(32.dp),
            )
            CosmeticCategory.BADGE -> Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = item.name,
                tint = item.primaryColor,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun PointsPill(
    points: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "$points pts",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
