package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CriticalAlertKind
import com.example.model.DailyActionPlan
import com.example.model.DepartmentStat
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelDepartment
import com.example.model.HotelProfile
import com.example.model.HotelProperty
import com.example.model.HotelReview
import com.example.model.OperationalAlert
import com.example.model.SentimentType
import com.example.model.TopicPatternSignal
import com.example.model.TriagePriority
import com.example.ui.theme.SentimentNegative
import com.example.ui.theme.SentimentNeutral
import com.example.ui.theme.SentimentPositive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DashboardScreen(
    profile: HotelProfile,
    selectedPropertyId: String,
    reviews: List<HotelReview>,
    snapshot: HotelAnalyticsSnapshot,
    dailyPlan: DailyActionPlan,
    topPatterns: List<TopicPatternSignal>,
    alerts: List<OperationalAlert> = emptyList(),
    reviewPropertyMap: Map<String, String>,
    isDemoMode: Boolean,
    isBusy: Boolean,
    onOpenAlerts: () -> Unit = {},
    onOpenExportStudio: () -> Unit,
    onOpenAddReview: () -> Unit,
    onOpenActionsTab: () -> Unit,
    onOpenPatternsTab: () -> Unit,
    onSeedDemoReviews: () -> Unit,
    onResetDemoData: () -> Unit,
    onSelectDepartment: (HotelDepartment) -> Unit,
    onSelectReview: (HotelReview) -> Unit,
    onSaveHotelProfile: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var showEditHotelDialog by remember { mutableStateOf(false) }
    val activeProperty = remember(selectedPropertyId) { HotelProperty.byId(selectedPropertyId) }
    val heroTitle = activeProperty?.name ?: profile.hotelName
    val heroLocation = activeProperty?.location ?: profile.hotelLocation

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HotelHeroCard(
                    title = heroTitle,
                    location = heroLocation,
                    isDemoMode = isDemoMode,
                    onEditHotel = { showEditHotelDialog = true },
                    onResetDemo = onResetDemoData,
                    onOpenExportStudio = onOpenExportStudio,
                    onOpenAddReview = onOpenAddReview,
                    onSignOut = onSignOut
                )
            }

            if (alerts.isNotEmpty()) {
                item {
                    ActiveAlertsBannerCard(
                        alerts = alerts,
                        onOpenAlerts = onOpenAlerts
                    )
                }
            }

            if (reviews.isEmpty()) {
                item {
                    EmptyDashboardCard(
                        isBusy = isBusy,
                        onAddReview = onOpenAddReview,
                        onSeedDemo = onSeedDemoReviews
                    )
                }
            } else {
                item {
                    KpiSummaryGrid(snapshot = snapshot)
                }

                // Daily Action Plan Highlight Card (V2)
                item {
                    DailyPlanPreviewCard(
                        dailyPlan = dailyPlan,
                        onOpenActionsTab = onOpenActionsTab
                    )
                }

                // Top Repeating Patterns Highlight Card (V2)
                if (topPatterns.isNotEmpty()) {
                    item {
                        TopPatternsPreviewCard(
                            patterns = topPatterns.take(3),
                            onOpenPatternsTab = onOpenPatternsTab
                        )
                    }
                }

                item {
                    SentimentDistributionCard(snapshot = snapshot)
                }

                item {
                    DepartmentTriageCard(
                        stats = snapshot.departmentStats,
                        onSelectDepartment = onSelectDepartment
                    )
                }

                val urgentReviews = reviews.filter {
                    !it.resolved && (it.priorityEnum == TriagePriority.HIGH || it.sentimentEnum == SentimentType.NEGATIVE)
                }.take(4)

                if (urgentReviews.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.section_recent_urgent),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(urgentReviews, key = { it.id }) { urgent ->
                        UrgentReviewCard(
                            review = urgent,
                            propertyName = HotelProperty.displayName(reviewPropertyMap[urgent.id]),
                            onClick = { onSelectReview(urgent) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showEditHotelDialog) {
        EditHotelProfileDialog(
            initialName = profile.hotelName,
            initialLocation = profile.hotelLocation,
            onDismiss = { showEditHotelDialog = false },
            onSave = { name, location ->
                onSaveHotelProfile(name, location)
                showEditHotelDialog = false
            }
        )
    }
}

@Composable
private fun ActiveAlertsBannerCard(
    alerts: List<OperationalAlert>,
    onOpenAlerts: () -> Unit
) {
    val overdueCount = alerts.count { it.kind == CriticalAlertKind.OVERDUE_TASK }
    val lowRatingCount = alerts.count { it.kind == CriticalAlertKind.LOW_RATING }
    val summaryLine = buildList {
        if (overdueCount > 0) add("$overdueCount εκπρόθεσμες εργασίες")
        if (lowRatingCount > 0) add("$lowRatingCount χαμηλές βαθμολογίες")
        val urgentOnly = alerts.size - overdueCount - lowRatingCount
        if (urgentOnly > 0) add("$urgentOnly επείγοντα χωρίς ανάθεση")
    }.joinToString(" • ")

    Card(
        onClick = onOpenAlerts,
        colors = CardDefaults.cardColors(
            containerColor = SentimentNegative.copy(alpha = 0.11f)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_alerts_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = SentimentNegative.copy(alpha = 0.18f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = SentimentNegative,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${alerts.size} κρίσιμες ειδοποιήσεις",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = SentimentNegative
                )
                if (summaryLine.isNotBlank()) {
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            FilledTonalButton(
                onClick = onOpenAlerts,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Προβολή")
            }
        }
    }
}

@Composable
private fun DailyPlanPreviewCard(
    dailyPlan: DailyActionPlan,
    onOpenActionsTab: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAddCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ημερήσιο Πλάνο Ενεργειών (${dailyPlan.selected.size}/3)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onOpenActionsTab,
                    modifier = Modifier.testTag("dashboard_open_actions_button")
                ) {
                    Text("Όλες")
                }
            }

            if (dailyPlan.selected.isEmpty()) {
                Text(
                    text = "Δεν υπάρχουν εκκρεμείς προτεινόμενες παρεμβάσεις εντός ορίων χρόνου/προϋπολογισμού.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Σύνολο: ${dailyPlan.totalMinutes} λεπτά • ${dailyPlan.totalBudgetEuros}€",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                dailyPlan.selected.forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onOpenActionsTab)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${rec.propertyName} • ${rec.departmentNameEl} • ${rec.effortMinutes} λ. • ${rec.costEuros}€",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", rec.priorityScore),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPatternsPreviewCard(
    patterns: List<TopicPatternSignal>,
    onOpenPatternsTab: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Insights,
                        contentDescription = null,
                        tint = SentimentNegative
                    )
                    Text(
                        text = "Επαναλαμβανόμενα Μοτίβα",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onOpenPatternsTab,
                    modifier = Modifier.testTag("dashboard_open_patterns_button")
                ) {
                    Text("Ανάλυση")
                }
            }

            patterns.forEach { p ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenPatternsTab)
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.aspect.labelEl,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "${p.propertyCount} καταλύματα • ${p.negativeCount}/${p.totalMentions} αρνητικές",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = SentimentNegative.copy(alpha = 0.14f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${p.negativeRatePercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = SentimentNegative,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelHeroCard(
    title: String,
    location: String,
    isDemoMode: Boolean,
    onEditHotel: () -> Unit,
    onResetDemo: () -> Unit,
    onOpenExportStudio: () -> Unit,
    onOpenAddReview: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val heroBitmap by produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.img_hotel_hero_1795276513603,
                opts
            )?.asImageBitmap()
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(206.dp)
        ) {
            heroBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A2E38).copy(alpha = 0.50f),
                                Color(0xFF0A2E38).copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isDemoMode) {
                    IconButton(
                        onClick = onResetDemo,
                        modifier = Modifier.testTag("reset_demo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Επαναφορά Demo",
                            tint = Color.White
                        )
                    }
                }
                IconButton(
                    onClick = onEditHotel,
                    modifier = Modifier.testTag("edit_hotel_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_edit_hotel),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = stringResource(R.string.sign_out),
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (location.isNotBlank()) {
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onOpenExportStudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_export_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_export_cta),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenAddReview,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("dashboard_add_review_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.dashboard_add_review))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboardCard(
    isBusy: Boolean,
    onAddReview: () -> Unit,
    onSeedDemo: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.RateReview,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.dashboard_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSeedDemo,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("seed_demo_reviews_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_seed_demo),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = onAddReview,
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_add_review_button")
                ) {
                    Text(text = stringResource(R.string.dashboard_add_review))
                }
            }
        }
    }
}

@Composable
private fun KpiSummaryGrid(snapshot: HotelAnalyticsSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KpiStatCard(
                label = stringResource(R.string.kpi_avg_rating),
                value = "${snapshot.formattedAverageRating}/10",
                subtitle = "Μέσος όρος περιόδου",
                icon = Icons.Filled.Star,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                label = stringResource(R.string.kpi_total_reviews),
                value = "${snapshot.totalReviews}",
                subtitle = "${snapshot.resolutionRatePercent}% τακτοποιήθηκαν",
                icon = Icons.Outlined.RateReview,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KpiStatCard(
                label = stringResource(R.string.kpi_positive_rate),
                value = "${snapshot.positivePercent}%",
                subtitle = "${snapshot.positiveCount} θετικές κριτικές",
                icon = Icons.Filled.ThumbUp,
                accentColor = SentimentPositive,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                label = stringResource(R.string.kpi_urgent_open),
                value = "${snapshot.urgentUnresolvedCount}",
                subtitle = if (snapshot.urgentUnresolvedCount > 0) "Απαιτείται ενέργεια" else "Όλα υπό έλεγχο",
                icon = Icons.Filled.PriorityHigh,
                accentColor = if (snapshot.urgentUnresolvedCount > 0) SentimentNegative else SentimentPositive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SentimentDistributionCard(snapshot: HotelAnalyticsSnapshot) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.section_sentiment_split),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(116.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 20.dp.toPx()
                        val diameter = size.minDimension - stroke
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)

                        if (snapshot.totalReviews == 0) {
                            drawArc(
                                color = Color.LightGray,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke)
                            )
                        } else {
                            val total = snapshot.totalReviews.toFloat()
                            var start = -90f
                            val posSweep = (snapshot.positiveCount / total) * 360f
                            val neuSweep = (snapshot.neutralCount / total) * 360f
                            val negSweep = 360f - posSweep - neuSweep

                            if (posSweep > 0f) {
                                drawArc(
                                    color = SentimentPositive,
                                    startAngle = start,
                                    sweepAngle = posSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                                start += posSweep
                            }
                            if (neuSweep > 0f) {
                                drawArc(
                                    color = SentimentNeutral,
                                    startAngle = start,
                                    sweepAngle = neuSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                                start += neuSweep
                            }
                            if (negSweep > 0f) {
                                drawArc(
                                    color = SentimentNegative,
                                    startAngle = start,
                                    sweepAngle = negSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${snapshot.positivePercent}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Θετικό",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SentimentLegendItem("Θετικές", snapshot.positiveCount, snapshot.positivePercent, SentimentPositive)
                    SentimentLegendItem("Ουδέτερες", snapshot.neutralCount, snapshot.neutralPercent, SentimentNeutral)
                    SentimentLegendItem("Αρνητικές", snapshot.negativeCount, snapshot.negativePercent, SentimentNegative)
                }
            }
        }
    }
}

@Composable
private fun SentimentLegendItem(
    label: String,
    count: Int,
    percent: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "$count ($percent%)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DepartmentTriageCard(
    stats: List<DepartmentStat>,
    onSelectDepartment: (HotelDepartment) -> Unit
) {
    val maxMentions = maxOf(1, stats.maxOfOrNull { it.totalMentions } ?: 1)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.section_department_triage),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            stats.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectDepartment(item.department) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("dept_row_${item.department.name}"),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.department.labelEl,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (item.complaintCount > 0) {
                                "${item.complaintCount} παράπονα • ${item.totalMentions} αναφ."
                            } else {
                                "${item.totalMentions} αναφορές"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.complaintCount > 0) SentimentNegative else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { item.totalMentions.toFloat() / maxMentions.toFloat() },
                        color = if (item.complaintCount > 0) SentimentNegative else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UrgentReviewCard(
    review: HotelReview,
    propertyName: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("urgent_review_${review.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${review.guestName} • ${review.sourceEnum.labelEl}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = propertyName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = SentimentNegative.copy(alpha = 0.14f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "${review.rating}/10 • ${review.priorityEnum.labelEl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SentimentNegative,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = review.summaryLine.ifBlank { review.reviewText },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditHotelProfileDialog(
    initialName: String,
    initialLocation: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var location by remember { mutableStateOf(initialLocation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hotel_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hotel_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.hotel_location_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, location) }) {
                Text(stringResource(R.string.save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}
