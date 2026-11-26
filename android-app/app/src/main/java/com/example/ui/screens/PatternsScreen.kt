package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ReviewSource
import com.example.model.TopicAspect
import com.example.model.TopicPatternSignal
import com.example.ui.theme.SentimentNegative
import com.example.ui.theme.SentimentNeutral
import com.example.ui.theme.SentimentPositive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatternsScreen(
    patterns: List<TopicPatternSignal>,
    windowDays: Int,
    sourceFilter: ReviewSource?,
    onSelectWindowDays: (Int) -> Unit,
    onSelectSourceFilter: (ReviewSource?) -> Unit,
    onOpenReviewById: (String) -> Unit,
    onFilterReviewsByTopic: (TopicAspect, ReviewSource?) -> Unit,
    onOpenReportsStudio: () -> Unit
) {
    var selectedEvidenceSignal by remember { mutableStateOf<TopicPatternSignal?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("el-GR")) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.patterns_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.patterns_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onOpenReportsStudio,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("patterns_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF / Excel")
                    }
                }
            }

            // Filter Card (7/14/30/60 days + Source)
            item {
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
                        Text(
                            text = "Χρονικό Παράθυρο Σύγκρισης",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(7, 14, 30, 60).forEach { days ->
                                FilterChip(
                                    selected = windowDays == days,
                                    onClick = { onSelectWindowDays(days) },
                                    label = { Text("$days ημέρες") },
                                    modifier = Modifier.testTag("pattern_window_$days")
                                )
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Φίλτρο Πηγής Κριτικών",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = sourceFilter == null,
                                onClick = { onSelectSourceFilter(null) },
                                label = { Text("Όλες οι πηγές") },
                                modifier = Modifier.testTag("pattern_source_all")
                            )
                            ReviewSource.entries.forEach { src ->
                                FilterChip(
                                    selected = sourceFilter == src,
                                    onClick = {
                                        onSelectSourceFilter(if (sourceFilter == src) null else src)
                                    },
                                    label = { Text(src.labelEl) },
                                    modifier = Modifier.testTag("pattern_source_${src.name}")
                                )
                            }
                        }
                    }
                }
            }

            if (patterns.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Δεν εντοπίστηκαν αρνητικά μοτίβα",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Δοκιμάστε μεγαλύτερο χρονικό παράθυρο (30 ή 60 ημέρες) ή επιλέξτε «Όλες οι πηγές».",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(patterns, key = { it.aspect.name }) { signal ->
                    PatternSignalCard(
                        signal = signal,
                        windowDays = windowDays,
                        onOpenEvidence = { selectedEvidenceSignal = signal }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    val activeSignal = selectedEvidenceSignal
    if (activeSignal != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedEvidenceSignal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Αποσπάσματα: ${activeSignal.aspect.labelEl}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${activeSignal.negativeCount} από ${activeSignal.totalMentions} αναφορές αρνητικές (${activeSignal.negativeRatePercent}%) σε ${activeSignal.propertyCount} καταλύματα (${windowDays} ημ.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                activeSignal.evidence.forEachIndexed { idx, item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.propertyName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${item.guestName} • ${item.source.labelEl} • ${dateFormat.format(Date(item.stayDateMillis))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.resolvedIncident) {
                                    Surface(
                                        color = SentimentPositive.copy(alpha = 0.14f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "Ήδη λυμένο",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SentimentPositive,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "«${item.quote}»",
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = {
                                    selectedEvidenceSignal = null
                                    onOpenReviewById(item.reviewId)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("pattern_open_review_${item.reviewId}_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Άνοιγμα κριτικής")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val targetAspect = activeSignal.aspect
                        selectedEvidenceSignal = null
                        onFilterReviewsByTopic(targetAspect, sourceFilter)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pattern_filter_all_reviews")
                ) {
                    Text("Δες όλες τις κριτικές αυτού του θέματος")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PatternSignalCard(
    signal: TopicPatternSignal,
    windowDays: Int,
    onOpenEvidence: () -> Unit
) {
    val rateFraction = (signal.negativeRatePercent / 100f).coerceIn(0f, 1f)
    val severityColor = when {
        signal.negativeRatePercent >= 65 -> SentimentNegative
        signal.negativeRatePercent >= 35 -> SentimentNeutral
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pattern_card_${signal.aspect.name}")
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
                Text(
                    text = signal.aspect.labelEl,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Apartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (signal.propertyCount == 1) "1 κατάλυμα" else "${signal.propertyCount} καταλύματα",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${signal.negativeCount} / ${signal.totalMentions} αρνητικές (${signal.negativeRatePercent}%)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = severityColor
                )

                val delta = signal.rateChangePoints
                val deltaText = when {
                    delta == null -> "Νέο σήμα ($windowDays ημ.)"
                    delta > 0 -> "+$delta μ.π."
                    else -> "$delta μ.π."
                }
                val deltaColor = when {
                    delta == null -> MaterialTheme.colorScheme.primary
                    delta > 0 -> SentimentNegative
                    else -> SentimentPositive
                }
                Surface(
                    color = deltaColor.copy(alpha = 0.14f),
                    shape = CircleShape
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        if (delta != null) {
                            Icon(
                                imageVector = if (delta > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                contentDescription = null,
                                tint = deltaColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelSmall,
                            color = deltaColor
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { rateFraction },
                color = severityColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            if (signal.totalMentions < 5) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SentimentNeutral.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = SentimentNeutral,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Μικρό δείγμα (<5 αναφορές) — ενδεικτική τάση",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            signal.evidence.firstOrNull()?.let { top ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "«${top.quote}»",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${top.propertyName} • ${top.guestName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onOpenEvidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pattern_evidence_button_${signal.aspect.name}")
            ) {
                Text("Δες αποσπάσματα & καταλύματα (${signal.evidence.size})")
            }
        }
    }
}
