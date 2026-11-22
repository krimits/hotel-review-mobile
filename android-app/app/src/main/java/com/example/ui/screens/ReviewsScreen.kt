package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HotelDepartment
import com.example.model.HotelReview
import com.example.model.ReviewSource
import com.example.model.SentimentType
import com.example.ui.theme.SentimentNegative
import com.example.ui.theme.SentimentNeutral
import com.example.ui.theme.SentimentPositive
import com.example.viewmodel.ReviewListFilterState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewsScreen(
    reviews: List<HotelReview>,
    filterState: ReviewListFilterState,
    isBusy: Boolean,
    initialSelectedReview: HotelReview?,
    showAddSheetInitially: Boolean,
    onConsumeAddSheetTrigger: () -> Unit,
    onClearInitialSelectedReview: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSentimentFilterChange: (SentimentType?) -> Unit,
    onDepartmentFilterChange: (HotelDepartment?) -> Unit,
    onToggleUnresolved: (Boolean) -> Unit,
    onCreateReviewWithAi: (String, ReviewSource, Double, Long, String, () -> Unit) -> Unit,
    onUpdateReviewReplyAndResolution: (HotelReview, Boolean, String) -> Unit,
    onRegenerateReply: (HotelReview, Boolean) -> Unit,
    onDeleteReview: (String) -> Unit,
    onReplyCopied: () -> Unit
) {
    var showAddSheet by remember(showAddSheetInitially) { mutableStateOf(showAddSheetInitially) }
    var activeDetailReviewId by remember(initialSelectedReview) {
        mutableStateOf(initialSelectedReview?.id)
    }
    val activeReview = reviews.firstOrNull { it.id == activeDetailReviewId } ?: initialSelectedReview
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("el", "GR")) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(stringResource(R.string.reviews_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reviews_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = filterState.sentimentFilter == null && !filterState.onlyUnresolved,
                    onClick = {
                        onSentimentFilterChange(null)
                        onToggleUnresolved(false)
                    },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = filterState.onlyUnresolved,
                    onClick = { onToggleUnresolved(!filterState.onlyUnresolved) },
                    label = { Text(stringResource(R.string.filter_unresolved)) }
                )
                FilterChip(
                    selected = filterState.sentimentFilter == SentimentType.NEGATIVE,
                    onClick = {
                        onSentimentFilterChange(
                            if (filterState.sentimentFilter == SentimentType.NEGATIVE) null else SentimentType.NEGATIVE
                        )
                    },
                    label = { Text(stringResource(R.string.filter_negative)) }
                )
                FilterChip(
                    selected = filterState.sentimentFilter == SentimentType.NEUTRAL,
                    onClick = {
                        onSentimentFilterChange(
                            if (filterState.sentimentFilter == SentimentType.NEUTRAL) null else SentimentType.NEUTRAL
                        )
                    },
                    label = { Text(stringResource(R.string.filter_neutral)) }
                )
                FilterChip(
                    selected = filterState.sentimentFilter == SentimentType.POSITIVE,
                    onClick = {
                        onSentimentFilterChange(
                            if (filterState.sentimentFilter == SentimentType.POSITIVE) null else SentimentType.POSITIVE
                        )
                    },
                    label = { Text(stringResource(R.string.filter_positive)) }
                )
            }

            if (filterState.departmentFilter != null) {
                Spacer(modifier = Modifier.height(6.dp))
                AssistChip(
                    onClick = { onDepartmentFilterChange(null) },
                    label = { Text("Τμήμα: ${filterState.departmentFilter.labelEl} ✕") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (reviews.isEmpty()) {
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
                                    text = "Δεν βρέθηκαν κριτικές",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Δοκιμάστε να καθαρίσετε τα φίλτρα ή πατήστε «Νέα Κριτική» για ανάλυση με AI.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(reviews, key = { it.id }) { review ->
                        ReviewItemCard(
                            review = review,
                            dateText = dateFormat.format(Date(review.stayDateMillis)),
                            onClick = { activeDetailReviewId = review.id }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.dashboard_add_review)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_review_fab")
        )
    }

    if (showAddSheet) {
        AddReviewAiSheet(
            isBusy = isBusy,
            onDismiss = {
                showAddSheet = false
                onConsumeAddSheetTrigger()
            },
            onSubmit = { guest, source, rating, dateMs, text ->
                onCreateReviewWithAi(guest, source, rating, dateMs, text) {
                    showAddSheet = false
                    onConsumeAddSheetTrigger()
                }
            }
        )
    }

    if (activeReview != null && activeDetailReviewId != null) {
        ReviewDetailSheet(
            review = activeReview,
            isBusy = isBusy,
            onDismiss = {
                activeDetailReviewId = null
                onClearInitialSelectedReview()
            },
            onSaveReplyAndResolution = { resolved, reply ->
                onUpdateReviewReplyAndResolution(activeReview, resolved, reply)
            },
            onRegenerateReply = { formal ->
                onRegenerateReply(activeReview, formal)
            },
            onDelete = {
                onDeleteReview(activeReview.id)
                activeDetailReviewId = null
                onClearInitialSelectedReview()
            },
            onCopied = onReplyCopied
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItemCard(
    review: HotelReview,
    dateText: String,
    onClick: () -> Unit
) {
    val sentimentColor = when (review.sentimentEnum) {
        SentimentType.POSITIVE -> SentimentPositive
        SentimentType.NEUTRAL -> SentimentNeutral
        SentimentType.NEGATIVE -> SentimentNegative
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review_card_${review.id}")
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
                        text = review.guestName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${review.sourceEnum.labelEl} • $dateText • ${review.languageEnum.labelEl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = sentimentColor.copy(alpha = 0.14f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", review.rating)} • ${review.sentimentEnum.labelEl}",
                        style = MaterialTheme.typography.labelMedium,
                        color = sentimentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = review.reviewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                review.departmentEnums.forEach { dept ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = dept.shortLabelEl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(
                    color = if (review.resolved) SentimentPositive.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ) {
                    Text(
                        text = if (review.resolved) "✓ Τακτοποιήθηκε" else "Εκκρεμεί απάντηση",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (review.resolved) SentimentPositive else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReviewAiSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, ReviewSource, Double, Long, String) -> Unit
) {
    var guestName by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(ReviewSource.BOOKING) }
    var ratingSlider by remember { mutableFloatStateOf(7.5f) }
    var reviewText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.new_review_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                AssistChip(
                    onClick = {
                        guestName = "Νίκος Αργυρίου"
                        ratingSlider = 5.4f
                        selectedSource = ReviewSource.BOOKING
                        reviewText = "Το δωμάτιο είχε ωραία θέα αλλά το κλιματιστικό έκανε θόρυβο όλο το βράδυ και στο πρωινό υπήρχε μεγάλη αναμονή για τραπέζι."
                    },
                    label = { Text("Δείγμα Παραπόνου (EL)") }
                )
                AssistChip(
                    onClick = {
                        guestName = "Emily Carter"
                        ratingSlider = 9.4f
                        selectedSource = ReviewSource.TRIPADVISOR
                        reviewText = "Spotless suite, breathtaking sunset terrace and incredibly warm front desk staff who arranged our ferry transfer effortlessly!"
                    },
                    label = { Text("Sample Praise (EN)") }
                )
            }

            OutlinedTextField(
                value = guestName,
                onValueChange = { guestName = it },
                label = { Text(stringResource(R.string.guest_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_guest_name")
            )

            Text(
                text = "${stringResource(R.string.rating_label)}: ${String.format(Locale.US, "%.1f", ratingSlider)} / 10",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = ratingSlider,
                onValueChange = { ratingSlider = (it * 10f).roundToInt() / 10f },
                valueRange = 1f..10f
            )

            Text(
                text = stringResource(R.string.source_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewSource.entries.forEach { src ->
                    FilterChip(
                        selected = selectedSource == src,
                        onClick = { selectedSource = src },
                        label = { Text(src.labelEl) }
                    )
                }
            }

            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text(stringResource(R.string.review_text_label)) },
                minLines = 4,
                maxLines = 7,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_review_text")
            )

            Button(
                onClick = {
                    onSubmit(
                        guestName,
                        selectedSource,
                        ratingSlider.toDouble(),
                        System.currentTimeMillis(),
                        reviewText
                    )
                },
                enabled = !isBusy && reviewText.trim().length >= 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_review_ai_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.analyzing_with_ai))
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.analyze_and_save_button))
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReviewDetailSheet(
    review: HotelReview,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSaveReplyAndResolution: (Boolean, String) -> Unit,
    onRegenerateReply: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onCopied: () -> Unit
) {
    val context = LocalContext.current
    var editableReply by remember(review.id, review.suggestedReply) {
        mutableStateOf(review.suggestedReply)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = review.guestName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${review.sourceEnum.labelEl} • Βαθμολογία ${review.rating}/10 • Προτεραιότητα: ${review.priorityEnum.labelEl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_review),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (review.summaryLine.isNotBlank()) {
                        Text(
                            text = "Σύνοψη AI: ${review.summaryLine}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = review.reviewText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (review.keyHighlights.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            review.keyHighlights.forEach { point ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "• $point",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.suggested_reply_title),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = editableReply,
                onValueChange = { editableReply = it },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onRegenerateReply(false) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.regenerate_reply_warm))
                }
                OutlinedButton(
                    onClick = { onRegenerateReply(true) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.regenerate_reply_formal))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Hotel Reply", editableReply))
                        onCopied()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_reply_button))
                }

                Button(
                    onClick = {
                        onSaveReplyAndResolution(!review.resolved, editableReply)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (review.resolved) stringResource(R.string.mark_unresolved)
                        else stringResource(R.string.mark_resolved),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
