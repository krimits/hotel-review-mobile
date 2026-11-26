package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.HostPulseEngine
import com.example.model.ActionTaskStatus
import com.example.model.AspectSentimentKind
import com.example.model.AssignedActionTask
import com.example.model.HotelDepartment
import com.example.model.HotelProperty
import com.example.model.HotelReview
import com.example.model.RecommendedAction
import com.example.model.ReviewSource
import com.example.model.ReviewTriageCategory
import com.example.model.SentimentType
import com.example.model.TopicAspect
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
    allReviews: List<HotelReview>,
    filterState: ReviewListFilterState,
    selectedPropertyId: String,
    reviewPropertyMap: Map<String, String>,
    checkedReviewIds: Set<String>,
    assignedTasks: List<AssignedActionTask>,
    recommendations: List<RecommendedAction>,
    isBusy: Boolean,
    initialSelectedReviewId: String?,
    showAddSheetInitially: Boolean,
    onConsumeAddSheetTrigger: () -> Unit,
    onClearInitialSelectedReview: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSentimentFilterChange: (SentimentType?) -> Unit,
    onDepartmentFilterChange: (HotelDepartment?) -> Unit,
    onSourceFilterChange: (ReviewSource?) -> Unit,
    onTopicFilterChange: (TopicAspect?) -> Unit,
    onTriageFilterChange: (ReviewTriageCategory?) -> Unit,
    onToggleUnresolved: (Boolean) -> Unit,
    onToggleNeedsConfirmation: (Boolean) -> Unit,
    onClearAllFilters: () -> Unit,
    onConfirmReviewAnalysis: (String) -> Unit,
    onApproveAndAssignAction: (RecommendedAction) -> Unit,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onCreateReviewWithAi: (String, String, ReviewSource, Double, Long, String, () -> Unit) -> Unit,
    onUpdateReviewReplyAndResolution: (HotelReview, Boolean, String) -> Unit,
    onRegenerateReply: (HotelReview, Boolean) -> Unit,
    onDeleteReview: (String) -> Unit,
    onReplyCopied: () -> Unit
) {
    var showAddSheet by remember(showAddSheetInitially) { mutableStateOf(showAddSheetInitially) }
    var activeDetailReviewId by remember(initialSelectedReviewId) {
        mutableStateOf(initialSelectedReviewId)
    }
    val activeReview = allReviews.firstOrNull { it.id == activeDetailReviewId }
        ?: reviews.firstOrNull { it.id == activeDetailReviewId }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("el-GR")) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
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

            Spacer(modifier = Modifier.height(8.dp))

            // 4-Category Triage & Operational Filter Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = filterState.triageFilter == null &&
                        filterState.sentimentFilter == null &&
                        !filterState.onlyUnresolved &&
                        !filterState.needsConfirmationOnly,
                    onClick = onClearAllFilters,
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = filterState.triageFilter == ReviewTriageCategory.REAL_PENDING,
                    onClick = {
                        onTriageFilterChange(
                            if (filterState.triageFilter == ReviewTriageCategory.REAL_PENDING) null
                            else ReviewTriageCategory.REAL_PENDING
                        )
                    },
                    label = { Text("Εκκρεμή") },
                    modifier = Modifier.testTag("filter_triage_pending")
                )
                FilterChip(
                    selected = filterState.triageFilter == ReviewTriageCategory.REAL_RESOLVED,
                    onClick = {
                        onTriageFilterChange(
                            if (filterState.triageFilter == ReviewTriageCategory.REAL_RESOLVED) null
                            else ReviewTriageCategory.REAL_RESOLVED
                        )
                    },
                    label = { Text("Ήδη λυμένα") },
                    modifier = Modifier.testTag("filter_triage_resolved")
                )
                FilterChip(
                    selected = filterState.triageFilter == ReviewTriageCategory.HYPOTHETICAL,
                    onClick = {
                        onTriageFilterChange(
                            if (filterState.triageFilter == ReviewTriageCategory.HYPOTHETICAL) null
                            else ReviewTriageCategory.HYPOTHETICAL
                        )
                    },
                    label = { Text("Επιθυμίες") },
                    modifier = Modifier.testTag("filter_triage_hypothetical")
                )
                FilterChip(
                    selected = filterState.triageFilter == ReviewTriageCategory.POSITIVE_COMMENT,
                    onClick = {
                        onTriageFilterChange(
                            if (filterState.triageFilter == ReviewTriageCategory.POSITIVE_COMMENT) null
                            else ReviewTriageCategory.POSITIVE_COMMENT
                        )
                    },
                    label = { Text("Θετικά") },
                    modifier = Modifier.testTag("filter_triage_positive")
                )
                FilterChip(
                    selected = filterState.needsConfirmationOnly,
                    onClick = { onToggleNeedsConfirmation(!filterState.needsConfirmationOnly) },
                    label = { Text("Προς επιβεβαίωση") }
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
                    selected = filterState.onlyUnresolved,
                    onClick = { onToggleUnresolved(!filterState.onlyUnresolved) },
                    label = { Text(stringResource(R.string.filter_unresolved)) }
                )
            }

            // Active Contextual Filters (Department / Topic / Source)
            val hasContextFilter = filterState.departmentFilter != null ||
                filterState.topicFilter != null ||
                filterState.sourceFilter != null
            if (hasContextFilter) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    filterState.departmentFilter?.let { dept ->
                        AssistChip(
                            onClick = { onDepartmentFilterChange(null) },
                            label = { Text("Τμήμα: ${dept.shortLabelEl} ✕") }
                        )
                    }
                    filterState.topicFilter?.let { topic ->
                        AssistChip(
                            onClick = { onTopicFilterChange(null) },
                            label = { Text("Θέμα: ${topic.labelEl} ✕") }
                        )
                    }
                    filterState.sourceFilter?.let { src ->
                        AssistChip(
                            onClick = { onSourceFilterChange(null) },
                            label = { Text("Πηγή: ${src.labelEl} ✕") }
                        )
                    }
                }
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
                                    text = "Δοκιμάστε να καθαρίσετε τα φίλτρα ή πατήστε «Νέα Κριτική» για ανάλυση 4 κατηγοριών.",
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
                            propertyName = HotelProperty.displayName(reviewPropertyMap[review.id]),
                            isConfirmed = review.id in checkedReviewIds,
                            assignedTasks = assignedTasks,
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
            defaultPropertyId = if (selectedPropertyId == HotelProperty.ALL_ID) "p1" else selectedPropertyId,
            isBusy = isBusy,
            onDismiss = {
                showAddSheet = false
                onConsumeAddSheetTrigger()
            },
            onSubmit = { guest, propId, source, rating, dateMs, text ->
                onCreateReviewWithAi(guest, propId, source, rating, dateMs, text) {
                    showAddSheet = false
                    onConsumeAddSheetTrigger()
                }
            }
        )
    }

    if (activeReview != null && activeDetailReviewId != null) {
        val reviewRecs = recommendations.filter { it.reviewId == activeReview.id }
        val reviewTasks = assignedTasks.filter { it.reviewId == activeReview.id }
        ReviewDetailSheet(
            review = activeReview,
            propertyName = HotelProperty.displayName(reviewPropertyMap[activeReview.id]),
            isConfirmed = activeReview.id in checkedReviewIds,
            reviewRecommendations = reviewRecs,
            reviewTasks = reviewTasks,
            isBusy = isBusy,
            onDismiss = {
                activeDetailReviewId = null
                onClearInitialSelectedReview()
            },
            onConfirmAnalysis = { onConfirmReviewAnalysis(activeReview.id) },
            onApproveAction = onApproveAndAssignAction,
            onStartTask = onStartTask,
            onCompleteTask = onCompleteTask,
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
    propertyName: String,
    isConfirmed: Boolean,
    assignedTasks: List<AssignedActionTask>,
    dateText: String,
    onClick: () -> Unit
) {
    val analysis = remember(review.id, review.reviewText) {
        HostPulseEngine.analyzeReviewText(review.reviewText)
    }
    val workflow = remember(review.id, assignedTasks, analysis) {
        HostPulseEngine.reviewWorkflowStatus(review, assignedTasks, analysis)
    }
    val sentimentColor = when (review.sentimentEnum) {
        SentimentType.POSITIVE -> SentimentPositive
        SentimentType.NEUTRAL -> SentimentNeutral
        SentimentType.NEGATIVE -> SentimentNegative
    }
    val triageColor = when (analysis.triageCategory) {
        ReviewTriageCategory.REAL_PENDING -> SentimentNegative
        ReviewTriageCategory.REAL_RESOLVED -> SentimentPositive
        ReviewTriageCategory.HYPOTHETICAL -> SentimentNeutral
        ReviewTriageCategory.POSITIVE_COMMENT -> SentimentPositive
        ReviewTriageCategory.INSUFFICIENT -> MaterialTheme.colorScheme.onSurfaceVariant
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
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.guestName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$propertyName • ${review.sourceEnum.labelEl} • $dateText",
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                // 4-Category Triage Badge
                Surface(
                    color = triageColor.copy(alpha = 0.14f),
                    shape = CircleShape
                ) {
                    Text(
                        text = analysis.triageCategory.labelEl,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = triageColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Human Confirmation / Workflow Status Badge
                if (analysis.pendingIssues.isNotEmpty()) {
                    Surface(
                        color = if (isConfirmed) SentimentPositive.copy(alpha = 0.12f)
                        else SentimentNeutral.copy(alpha = 0.14f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (isConfirmed) "✓ Ελέγχθηκε • ${workflow.labelEl}" else "Προς έλεγχο",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConfirmed) SentimentPositive else SentimentNeutral,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "0 εκκρεμείς ενέργειες",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                review.departmentEnums.take(2).forEach { dept ->
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReviewAiSheet(
    defaultPropertyId: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, ReviewSource, Double, Long, String) -> Unit
) {
    var guestName by remember { mutableStateOf("") }
    var selectedPropertyId by remember { mutableStateOf(defaultPropertyId) }
    var selectedSource by remember { mutableStateOf(ReviewSource.BOOKING) }
    var ratingSlider by remember { mutableFloatStateOf(7.5f) }
    var reviewText by remember { mutableStateOf("") }

    val liveAnalysis = remember(reviewText) {
        if (reviewText.trim().length >= 4) HostPulseEngine.analyzeReviewText(reviewText) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.new_review_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Mock1 4 Triage Test Samples
            Text(
                text = "Γρήγορα δείγματα δοκιμής (4 κατηγορίες διαλογής):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                AssistChip(
                    onClick = {
                        guestName = "Νίκος Αργυρίου"
                        ratingSlider = 4.5f
                        selectedSource = ReviewSource.BOOKING
                        reviewText = "Το κλιματιστικό δεν λειτουργεί όλο το βράδυ και υπήρχε μούχλα στο μπάνιο."
                    },
                    label = { Text("Εκκρεμές (EL)") }
                )
                AssistChip(
                    onClick = {
                        guestName = "Άννα Μ."
                        ratingSlider = 8.4f
                        selectedSource = ReviewSource.BOOKING
                        reviewText = "Ο κλιματισμός ήταν χαλασμένος, αλλά διορθώθηκε την ίδια μέρα από το προσωπικό."
                    },
                    label = { Text("Ήδη λυμένο") }
                )
                AssistChip(
                    onClick = {
                        guestName = "Πέτρος Λ."
                        ratingSlider = 8.8f
                        selectedSource = ReviewSource.GOOGLE
                        reviewText = "Πεντακάθαρο δωμάτιο και ευγενικό προσωπικό. Θα προτιμούσα μεγαλύτερο ψυγείο στο δωμάτιο."
                    },
                    label = { Text("Επιθυμία / υπόθεση") }
                )
                AssistChip(
                    onClick = {
                        guestName = "Emily Carter"
                        ratingSlider = 8.6f
                        selectedSource = ReviewSource.TRIPADVISOR
                        reviewText = "Amazing sunset views and super friendly staff, however the Wi-Fi was not working in our room."
                    },
                    label = { Text("Positive + Pending Wi-Fi (EN)") }
                )
            }

            // Property Selector
            Text(
                text = "Κατάλυμα",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                HotelProperty.CATALOG.forEach { prop ->
                    FilterChip(
                        selected = selectedPropertyId == prop.id,
                        onClick = { selectedPropertyId = prop.id },
                        label = { Text(prop.name) }
                    )
                }
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
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_review_text")
            )

            // Instant 4-Category Triage Preview
            if (liveAnalysis != null) {
                val previewColor = when (liveAnalysis.triageCategory) {
                    ReviewTriageCategory.REAL_PENDING -> SentimentNegative
                    ReviewTriageCategory.REAL_RESOLVED -> SentimentPositive
                    ReviewTriageCategory.HYPOTHETICAL -> SentimentNeutral
                    ReviewTriageCategory.POSITIVE_COMMENT -> SentimentPositive
                    ReviewTriageCategory.INSUFFICIENT -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    color = previewColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Προεπισκόπηση Διαλογής: ${liveAnalysis.triageCategory.labelEl}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = previewColor
                        )
                        Text(
                            text = if (liveAnalysis.pendingIssues.isNotEmpty()) {
                                "Θα παραχθούν ${liveAnalysis.pendingIssues.size} προτεινόμενες ενέργειες (${liveAnalysis.pendingIssues.joinToString { it.category.departmentNameEl }})."
                            } else {
                                "Δεν θα παραχθεί νέα εργασία (μόνο πραγματικά εκκρεμή προβλήματα παράγουν ενέργειες)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSubmit(
                        guestName,
                        selectedPropertyId,
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
    propertyName: String,
    isConfirmed: Boolean,
    reviewRecommendations: List<RecommendedAction>,
    reviewTasks: List<AssignedActionTask>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmAnalysis: () -> Unit,
    onApproveAction: (RecommendedAction) -> Unit,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onSaveReplyAndResolution: (Boolean, String) -> Unit,
    onRegenerateReply: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onCopied: () -> Unit
) {
    val context = LocalContext.current
    val analysis = remember(review.id, review.reviewText) {
        HostPulseEngine.analyzeReviewText(review.reviewText)
    }
    var editableReply by remember(review.id, review.suggestedReply) {
        mutableStateOf(review.suggestedReply)
    }
    var showDeleteConfirmDialog by remember(review.id) { mutableStateOf(false) }

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
                        text = "$propertyName • ${review.sourceEnum.labelEl} • ${review.rating}/10",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_review),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 4-Category Triage & Full Review Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val triageColor = when (analysis.triageCategory) {
                        ReviewTriageCategory.REAL_PENDING -> SentimentNegative
                        ReviewTriageCategory.REAL_RESOLVED -> SentimentPositive
                        ReviewTriageCategory.HYPOTHETICAL -> SentimentNeutral
                        ReviewTriageCategory.POSITIVE_COMMENT -> SentimentPositive
                        ReviewTriageCategory.INSUFFICIENT -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = triageColor.copy(alpha = 0.14f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Κατηγορία Διαλογής: ${analysis.triageCategory.labelEl}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = triageColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = review.reviewText,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (analysis.aspects.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            analysis.aspects.forEach { asp ->
                                val aspColor = when (asp.sentiment) {
                                    AspectSentimentKind.POSITIVE -> SentimentPositive
                                    AspectSentimentKind.NEGATIVE -> SentimentNegative
                                    AspectSentimentKind.MIXED -> SentimentNeutral
                                    AspectSentimentKind.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Surface(
                                    color = aspColor.copy(alpha = 0.12f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${asp.category.labelEl}: ${asp.sentiment.labelEl}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = aspColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Show non-actionable incidents clearly (resolved / hypothetical)
                    val nonPending = analysis.issues.filter { !it.isPending }
                    nonPending.forEach { issue ->
                        val note = if (issue.resolved) "Ήδη λυμένο περιστατικό (0 ενέργειες)"
                        else "Επιθυμία / υπόθεση (0 ενέργειες)"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$note: «${issue.quote}»",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Human-in-the-loop Confirmation Gate + Inline Pending Actions
            if (analysis.pendingIssues.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConfirmed) SentimentPositive.copy(alpha = 0.08f)
                        else SentimentNeutral.copy(alpha = 0.10f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isConfirmed) Icons.Filled.VerifiedUser else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (isConfirmed) SentimentPositive else SentimentNeutral,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isConfirmed) "Η ανάλυση επιβεβαιώθηκε από τον υπεύθυνο"
                                else "Έλεγχος από τον υπεύθυνο (Υποχρεωτικό βήμα)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!isConfirmed) {
                            Text(
                                text = "Επιβεβαιώστε ότι τα εκκρεμή προβλήματα και τα αποσπάσματα είναι ακριβή για να ενεργοποιηθεί η ανάθεση εργασιών.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onConfirmAnalysis,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("detail_confirm_analysis_button")
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.confirm_analysis_button))
                            }
                        }

                        // Pending Recommendations for this review
                        reviewRecommendations.forEach { rec ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${rec.departmentNameEl} • ${rec.effortMinutes} λ. • ${rec.costEuros}€",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = rec.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "«${rec.quote}»",
                                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { onApproveAction(rec) },
                                        enabled = isConfirmed,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Text(stringResource(R.string.approve_assign_button))
                                    }
                                }
                            }
                        }

                        // Already Assigned Tasks for this review
                        reviewTasks.forEach { task ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Ανάθεση (${task.departmentNameEl}) — ${task.status.labelEl}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    task.notes.lastOrNull()?.let { latest ->
                                        Text(
                                            text = "Σημείωση (${latest.fromDepartment} → ${latest.toDepartment}): ${latest.text}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (task.status == ActionTaskStatus.OPEN) {
                                            FilledTonalButton(
                                                onClick = { onStartTask(task.id) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Σε εξέλιξη")
                                            }
                                        }
                                        if (task.status != ActionTaskStatus.DONE) {
                                            Button(
                                                onClick = { onCompleteTask(task.id) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Filled.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Ολοκλήρωση")
                                            }
                                        }
                                    }
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
                minLines = 3,
                maxLines = 7,
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
                    Text(
                        text = stringResource(R.string.copy_reply_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = {
                        onSaveReplyAndResolution(review.resolved, editableReply)
                    },
                    enabled = editableReply.trim() != review.suggestedReply.trim(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_reply_only_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Αποθήκευση",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = {
                    onSaveReplyAndResolution(!review.resolved, editableReply)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
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
            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Διαγραφή κριτικής;") },
            text = {
                Text(
                    "Θα διαγραφεί οριστικά η κριτική του/της ${review.guestName} μαζί με τυχόν συνδεδεμένες αναθέσεις."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.delete_review))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}
