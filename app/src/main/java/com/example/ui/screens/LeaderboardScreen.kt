package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LeaderboardCategory
import com.example.data.model.LeaderboardEntry
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun LeaderboardScreen(
    entriesState: UiState<List<LeaderboardEntry>>,
    selectedCategory: LeaderboardCategory,
    onSelectCategory: (LeaderboardCategory) -> Unit,
    currentUserId: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize()
                .testTag("leaderboard_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LeaderboardHeaderCard(
                    entries = (entriesState as? UiState.Success)?.data.orEmpty(),
                    selectedCategory = selectedCategory,
                    currentUserId = currentUserId,
                )
            }

            item {
                CategoryFilterRow(
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory,
                )
            }

            when (entriesState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = entriesState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val entries = entriesState.data
                    if (entries.isEmpty()) {
                        item {
                            EmptyLeaderboardCard(category = selectedCategory)
                        }
                    } else {
                        if (entries.size >= 2) {
                            item {
                                PodiumCard(
                                    topEntries = entries.take(3),
                                    category = selectedCategory,
                                    currentUserId = currentUserId,
                                )
                            }
                        }

                        itemsIndexed(
                            items = entries,
                            key = { _, entry -> entry.userId.ifBlank { entry.id } },
                        ) { index, entry ->
                            LeaderboardRowCard(
                                rank = index + 1,
                                entry = entry,
                                category = selectedCategory,
                                isCurrentUser = entry.userId == currentUserId,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: LeaderboardCategory,
    onSelectCategory: (LeaderboardCategory) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeaderboardCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            val icon = when (category) {
                LeaderboardCategory.ALL -> Icons.Rounded.EmojiEvents
                LeaderboardCategory.CYCLING -> Icons.AutoMirrored.Rounded.DirectionsBike
                LeaderboardCategory.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
            }
            FilterChip(
                selected = selected,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        text = category.labelFr,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("leaderboard_tab_${category.name}"),
            )
        }
    }
}

@Composable
private fun LeaderboardHeaderCard(
    entries: List<LeaderboardEntry>,
    selectedCategory: LeaderboardCategory,
    currentUserId: String,
) {
    val myIndex = entries.indexOfFirst { it.userId == currentUserId }
    val myEntry = if (myIndex >= 0) entries[myIndex] else null

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F221A),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0C1E17),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            Color(0xFF0E2920),
                        )
                    )
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(26.dp),
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.leaderboard_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Catégorie : ${selectedCategory.labelFr}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }

                if (myEntry != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "Votre position (${selectedCategory.labelFr})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA7F3D0),
                                )
                                Text(
                                    text = "#${myIndex + 1} • ${myEntry.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Text(
                                text = "${myEntry.pointsFor(selectedCategory)} pts",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFDE68A),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumCard(
    topEntries: List<LeaderboardEntry>,
    category: LeaderboardCategory,
    currentUserId: String,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Podium • ${category.labelFr}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val orderedIndices = when (topEntries.size) {
                    2 -> listOf(1, 0)
                    else -> listOf(1, 0, 2)
                }
                orderedIndices.forEach { index ->
                    if (index < topEntries.size) {
                        PodiumSlot(
                            rank = index + 1,
                            entry = topEntries[index],
                            category = category,
                            isCurrentUser = topEntries[index].userId == currentUserId,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    rank: Int,
    entry: LeaderboardEntry,
    category: LeaderboardCategory,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val medalColor = rankMedalColor(rank)
    val podiumHeight = when (rank) {
        1 -> 126.dp
        2 -> 108.dp
        else -> 96.dp
    }

    Surface(
        modifier = modifier.height(podiumHeight),
        shape = RoundedCornerShape(18.dp),
        color = medalColor.copy(alpha = 0.14f),
        border = if (isCurrentUser) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, medalColor.copy(alpha = 0.35f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = medalColor,
            ) {
                Text(
                    text = "#$rank",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${entry.pointsFor(category)} pts",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LeaderboardRowCard(
    rank: Int,
    entry: LeaderboardEntry,
    category: LeaderboardCategory,
    isCurrentUser: Boolean,
) {
    val medalColor = rankMedalColor(rank)
    val isTopThree = rank <= 3
    val points = entry.pointsFor(category)
    val distanceMeters = entry.distanceFor(category)
    val sessions = entry.sessionsFor(category)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrentUser) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrentUser) 4.dp else 2.dp,
        border = if (isCurrentUser) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTopThree) medalColor.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#$rank",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isTopThree) medalColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrentUser) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "Vous",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "%.1f km • %d sortie%s",
                        distanceMeters / 1000.0,
                        sessions,
                        if (sessions > 1) "s" else "",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "$points",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLeaderboardCard(category: LeaderboardCategory) {
    val icon = when (category) {
        LeaderboardCategory.ALL -> Icons.Rounded.EmojiEvents
        LeaderboardCategory.CYCLING -> Icons.AutoMirrored.Rounded.DirectionsBike
        LeaderboardCategory.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "Aucun score en ${category.labelFr}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when (category) {
                    LeaderboardCategory.RUNNING -> "Lancez une course à pied (25 pts / 100 m) pour ouvrir ce classement !"
                    LeaderboardCategory.CYCLING -> "Lancez une balade à vélo (10 pts / km) pour ouvrir ce classement !"
                    LeaderboardCategory.ALL -> "Complétez une première sortie à vélo ou en course à pied pour apparaître ici."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun rankMedalColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFD97706) // Gold
    2 -> Color(0xFF64748B) // Silver
    3 -> Color(0xFFB45309) // Bronze
    else -> Color(0xFF10B981)
}
