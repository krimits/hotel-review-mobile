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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.domain.HostPulseEngine
import com.example.model.ActionTaskStatus
import com.example.model.AlertRulesConfig
import com.example.model.AssignedActionTask
import com.example.model.DailyActionPlan
import com.example.model.PlanLimits
import com.example.model.PriorityWeights
import com.example.model.RecommendedAction
import com.example.model.TaskDepartmentNote
import com.example.ui.theme.SentimentNegative
import com.example.ui.theme.SentimentNeutral
import com.example.ui.theme.SentimentPositive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionsScreen(
    recommendations: List<RecommendedAction>,
    dailyPlan: DailyActionPlan,
    assignedTasks: List<AssignedActionTask>,
    weights: PriorityWeights,
    limits: PlanLimits,
    alertRules: AlertRulesConfig = AlertRulesConfig(),
    initialSegment: Int? = null,
    onConsumeInitialSegment: () -> Unit = {},
    onUpdateWeights: (Int, Int) -> Unit,
    onUpdateLimits: (Int, Int) -> Unit,
    onConfirmAnalysis: (String) -> Unit,
    onApproveAndAssign: (RecommendedAction) -> Unit,
    onStartTask: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onAddTaskNote: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOpenReviewById: (String) -> Unit
) {
    var activeSegment by rememberSaveable { mutableIntStateOf(0) } // 0 = Προτάσεις, 1 = Αναθέσεις
    var taskStatusFilter by rememberSaveable { mutableIntStateOf(0) } // 0 = Ανοικτές, 1 = Ολοκληρωμένες, 2 = Όλες

    LaunchedEffect(initialSegment) {
        if (initialSegment != null) {
            activeSegment = initialSegment
            onConsumeInitialSegment()
        }
    }

    val planKeys = remember(dailyPlan) { dailyPlan.selected.map { it.key }.toSet() }
    val outsidePlan = remember(recommendations, planKeys) {
        recommendations.filterNot { it.key in planKeys }
    }
    val openTasksCount = remember(assignedTasks) {
        assignedTasks.count { it.status != ActionTaskStatus.DONE }
    }
    val filteredTasks = remember(assignedTasks, taskStatusFilter) {
        when (taskStatusFilter) {
            0 -> assignedTasks.filter { it.status != ActionTaskStatus.DONE }
            1 -> assignedTasks.filter { it.status == ActionTaskStatus.DONE }
            else -> assignedTasks
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.actions_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.actions_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = activeSegment == 0,
                        onClick = { activeSegment = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("actions_tab_recommendations")
                    ) {
                        Text("Προτάσεις (${recommendations.size})")
                    }
                    SegmentedButton(
                        selected = activeSegment == 1,
                        onClick = { activeSegment = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("actions_tab_assigned")
                    ) {
                        Text("Αναθέσεις ($openTasksCount)")
                    }
                }
            }

            if (activeSegment == 0) {
                item {
                    CapacityAndWeightsCard(
                        weights = weights,
                        limits = limits,
                        dailyPlan = dailyPlan,
                        onUpdateWeights = onUpdateWeights,
                        onUpdateLimits = onUpdateLimits
                    )
                }

                // Daily Plan (max 3 actions)
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ημερήσιο Πλάνο (έως 3 παρεμβάσεις)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${dailyPlan.selected.size}/3 • ${dailyPlan.totalMinutes} λ. • ${dailyPlan.totalBudgetEuros}€",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (dailyPlan.selected.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (recommendations.isEmpty()) {
                                    "Δεν υπάρχουν εκκρεμή προβλήματα προς ανάθεση. Μόνο πραγματικά εκκρεμή προβλήματα (όχι ήδη λυμένα ή επιθυμίες) παράγουν ενέργειες."
                                } else {
                                    "Καμία πρόταση δεν χωράει στον τρέχοντα διαθέσιμο χρόνο (${limits.maxMinutes} λ.) και προϋπολογισμό (${limits.maxBudgetEuros}€). Αυξήστε τα όρια παραπάνω."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(dailyPlan.selected, key = { "plan_${it.key}" }) { rec ->
                        RecommendationActionCard(
                            action = rec,
                            inDailyPlan = true,
                            onConfirmAnalysis = { onConfirmAnalysis(rec.reviewId) },
                            onApproveAndAssign = { onApproveAndAssign(rec) },
                            onOpenReview = { onOpenReviewById(rec.reviewId) }
                        )
                    }
                }

                if (outsidePlan.isNotEmpty()) {
                    item {
                        Text(
                            text = "Υπόλοιπες Προτάσεις Εκτός Ημερήσιου Πλάνου (${outsidePlan.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(outsidePlan, key = { "rest_${it.key}" }) { rec ->
                        RecommendationActionCard(
                            action = rec,
                            inDailyPlan = false,
                            onConfirmAnalysis = { onConfirmAnalysis(rec.reviewId) },
                            onApproveAndAssign = { onApproveAndAssign(rec) },
                            onOpenReview = { onOpenReviewById(rec.reviewId) }
                        )
                    }
                }
            } else {
                // Assigned Tasks Workflow Tab
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = taskStatusFilter == 0,
                            onClick = { taskStatusFilter = 0 },
                            label = { Text("Ανοικτές ($openTasksCount)") }
                        )
                        FilterChip(
                            selected = taskStatusFilter == 1,
                            onClick = { taskStatusFilter = 1 },
                            label = {
                                val doneCnt = assignedTasks.count { it.status == ActionTaskStatus.DONE }
                                Text("Ολοκληρωμένες ($doneCnt)")
                            }
                        )
                        FilterChip(
                            selected = taskStatusFilter == 2,
                            onClick = { taskStatusFilter = 2 },
                            label = { Text("Όλες (${assignedTasks.size})") }
                        )
                    }
                }

                if (filteredTasks.isEmpty()) {
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
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AssignmentTurnedIn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Δεν υπάρχουν εργασίες σε αυτή την προβολή",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Επιβεβαιώστε μια ανάλυση και πατήστε «Έγκριση & Ανάθεση» από τις Προτάσεις.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTasks, key = { it.id }) { task ->
                        AssignedTaskCard(
                            task = task,
                            alertRules = alertRules,
                            onStart = { onStartTask(task.id) },
                            onComplete = { onCompleteTask(task.id) },
                            onAddNote = { fromDept, toDept, text ->
                                onAddTaskNote(task.id, fromDept, toDept, text)
                            },
                            onOpenReview = { onOpenReviewById(task.reviewId) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapacityAndWeightsCard(
    weights: PriorityWeights,
    limits: PlanLimits,
    dailyPlan: DailyActionPlan,
    onUpdateWeights: (Int, Int) -> Unit,
    onUpdateLimits: (Int, Int) -> Unit
) {
    val timeOptions = listOf(15, 30, 60, 120, 240)
    val budgetOptions = listOf(0, 25, 50, 100, 150, 300)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Διαθέσιμος Χρόνος & Προϋπολογισμός Ομάδας",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Διαθέσιμος χρόνος σήμερα: ${limits.maxMinutes} λεπτά (χρήση: ${dailyPlan.totalMinutes} λ.)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    timeOptions.forEach { mins ->
                        FilterChip(
                            selected = limits.maxMinutes == mins,
                            onClick = { onUpdateLimits(mins, limits.maxBudgetEuros) },
                            label = { Text("$mins λ.") },
                            modifier = Modifier.testTag("limit_time_$mins")
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = {
                        if (limits.maxMinutes <= 0) 0f
                        else (dailyPlan.totalMinutes.toFloat() / limits.maxMinutes.toFloat()).coerceIn(0f, 1f)
                    },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Διαθέσιμος προϋπολογισμός: ${limits.maxBudgetEuros}€ (χρήση: ${dailyPlan.totalBudgetEuros}€)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    budgetOptions.forEach { eur ->
                        FilterChip(
                            selected = limits.maxBudgetEuros == eur,
                            onClick = { onUpdateLimits(limits.maxMinutes, eur) },
                            label = { Text("${eur}€") },
                            modifier = Modifier.testTag("limit_budget_$eur")
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = {
                        if (limits.maxBudgetEuros <= 0) {
                            if (dailyPlan.totalBudgetEuros == 0) 0f else 1f
                        } else {
                            (dailyPlan.totalBudgetEuros.toFloat() / limits.maxBudgetEuros.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }

            HorizontalDivider()

            Text(
                text = "Βάρη Υπολογισμού Προτεραιότητας (Σοβαρότητα × Συχνότητα 30μ)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Βαρύτητα προβλήματος",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${weights.severityWeight} / 5",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = weights.severityWeight.toFloat(),
                    onValueChange = { onUpdateWeights(it.roundToInt(), weights.recurrenceWeight) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.testTag("slider_severity_weight")
                )
            }

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Συχνότητα επανάληψης (30 ημερών)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${weights.recurrenceWeight} / 5",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Slider(
                    value = weights.recurrenceWeight.toFloat(),
                    onValueChange = { onUpdateWeights(weights.severityWeight, it.roundToInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.testTag("slider_recurrence_weight")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationActionCard(
    action: RecommendedAction,
    inDailyPlan: Boolean,
    onConfirmAnalysis: () -> Unit,
    onApproveAndAssign: () -> Unit,
    onOpenReview: () -> Unit
) {
    val urgencyColor = if (action.severity == 3) SentimentNegative else SentimentNeutral

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (inDailyPlan) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("action_card_${action.key}")
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = urgencyColor.copy(alpha = 0.14f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (action.severity == 3) "Επείγον" else "Κανονικό",
                            style = MaterialTheme.typography.labelSmall,
                            color = urgencyColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = action.departmentNameEl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    if (!inDailyPlan && !action.fitsSingleLimits) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Εκτός ορίου χρόνου/€",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Σκορ ${String.format(Locale.US, "%.1f", action.priorityScore)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = action.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${action.propertyName} • ${action.guestName} • Επανάληψη 30μ: ×${action.recurrence30d}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Exact Evidence Quote Block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
                Text(
                    text = "«${action.quote}»",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // Effort, Cost, Deadline Strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${action.effortMinutes} λεπτά",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Euro,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${action.costEuros}€",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "• Προθεσμία: ${action.deadlineLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = urgencyColor
                )
            }

            // Mandatory Human-in-the-Loop Confirmation Banner
            if (!action.isAnalysisConfirmed) {
                Surface(
                    color = SentimentNeutral.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = SentimentNeutral,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Απαιτείται «Επιβεβαίωση ανάλυσης» πριν την έγκριση & ανάθεση στην ομάδα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = SentimentPositive,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Η ανάλυση επιβεβαιώθηκε από τον υπεύθυνο",
                        style = MaterialTheme.typography.labelMedium,
                        color = SentimentPositive
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!action.isAnalysisConfirmed) {
                    Button(
                        onClick = onConfirmAnalysis,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_analysis_${action.reviewId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.confirm_analysis_button),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = onApproveAndAssign,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("approve_action_${action.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.approve_assign_button),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenReview,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_review_from_action_${action.reviewId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Κριτική")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignedTaskCard(
    task: AssignedActionTask,
    alertRules: AlertRulesConfig,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onAddNote: (String, String, String) -> Unit,
    onOpenReview: () -> Unit
) {
    val nowMillis = remember(task.id, task.status) { System.currentTimeMillis() }
    val isOverdue = remember(task.id, task.status, task.createdAtMillis, alertRules) {
        HostPulseEngine.isTaskOverdue(task, alertRules, nowMillis)
    }
    val pendingHours = remember(task.id, task.createdAtMillis) {
        HostPulseEngine.taskPendingHours(task, nowMillis)
    }
    val statusColor = when (task.status) {
        ActionTaskStatus.OPEN -> SentimentNeutral
        ActionTaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        ActionTaskStatus.DONE -> SentimentPositive
    }

    val noteDateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("el-GR")) }
    var showNoteComposer by remember(task.id) { mutableStateOf(false) }
    var fromDepartment by remember(task.id) { mutableStateOf(task.departmentNameEl) }
    var toDepartment by remember(task.id) {
        mutableStateOf(if (task.departmentNameEl == "Υποδοχή") "Συντήρηση" else "Υποδοχή")
    }
    var noteInput by remember(task.id) { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = task.status.labelEl,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (isOverdue) {
                        Surface(
                            color = SentimentNegative.copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = SentimentNegative,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Εκπρόθεσμη · ${pendingHours}ω",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SentimentNegative
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "${task.departmentNameEl} • ${task.effortMinutes} λ. • ${task.costEuros}€",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${task.propertyName} • Προθεσμία: ${task.deadlineLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (task.quote.isNotBlank()) {
                Text(
                    text = "«${task.quote}»",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Inter-department Internal Notes Section
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Σημειώσεις τμημάτων (${task.notes.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (task.status != ActionTaskStatus.DONE) {
                            TextButton(
                                onClick = { showNoteComposer = !showNoteComposer },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("toggle_note_composer_${task.id}")
                            ) {
                                Text(if (showNoteComposer) "Κλείσιμο" else "+ Σημείωση")
                            }
                        }
                    }

                    if (task.notes.isEmpty()) {
                        Text(
                            text = "Δεν υπάρχουν ακόμα εσωτερικές σημειώσεις μεταξύ τμημάτων.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        task.notes.forEach { note ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${note.fromDepartment} → ${note.toDepartment}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = noteDateFormat.format(Date(note.createdAtMillis)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = note.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (showNoteComposer && task.status != ActionTaskStatus.DONE) {
                        HorizontalDivider()
                        Text(
                            text = "Από τμήμα",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            TaskDepartmentNote.DEPARTMENT_OPTIONS.forEach { dept ->
                                FilterChip(
                                    selected = fromDepartment == dept,
                                    onClick = { fromDepartment = dept },
                                    label = { Text(dept) }
                                )
                            }
                        }

                        Text(
                            text = "Προς τμήμα",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            (TaskDepartmentNote.DEPARTMENT_OPTIONS + TaskDepartmentNote.ALL_DEPARTMENTS).forEach { dept ->
                                FilterChip(
                                    selected = toDepartment == dept,
                                    onClick = { toDepartment = dept },
                                    label = { Text(dept) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it.take(500) },
                            placeholder = { Text("π.χ. Έγινε έλεγχος, αναμένεται ανταλλακτικό…") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_input_${task.id}")
                        )
                        Button(
                            onClick = {
                                if (noteInput.isNotBlank()) {
                                    onAddNote(fromDepartment, toDepartment, noteInput)
                                    noteInput = ""
                                    showNoteComposer = false
                                }
                            },
                            enabled = noteInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("submit_note_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Αποστολή σημείωσης ($fromDepartment → $toDepartment)")
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (task.status) {
                    ActionTaskStatus.OPEN -> {
                        FilledTonalButton(
                            onClick = onStart,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_task_${task.id}")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Σε εξέλιξη",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onComplete,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("complete_task_${task.id}")
                        ) {
                            Icon(Icons.Filled.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ολοκλήρωση",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    ActionTaskStatus.IN_PROGRESS -> {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("complete_task_${task.id}")
                        ) {
                            Icon(Icons.Filled.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ολοκλήρωση Εργασίας")
                        }
                    }

                    ActionTaskStatus.DONE -> {
                        Surface(
                            color = SentimentPositive.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SentimentPositive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ολοκληρώθηκε",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SentimentPositive
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenReview,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Κριτική")
                }
            }
        }
    }
}
