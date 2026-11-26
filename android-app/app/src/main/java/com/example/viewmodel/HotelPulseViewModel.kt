package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HotelAiService
import com.example.data.HotelRepository
import com.example.data.OperationalSnapshot
import com.example.data.OperationalStateStore
import com.example.domain.HostPulseEngine
import com.example.export.ExcelReportGenerator
import com.example.export.ExcelSheetPreview
import com.example.export.PdfReportGenerator
import com.example.export.ReportMetadata
import com.example.model.ActionCategory
import com.example.model.ActionTaskStatus
import com.example.model.AlertRulesConfig
import com.example.model.AssignedActionTask
import com.example.model.DailyActionPlan
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelDepartment
import com.example.model.HotelProfile
import com.example.model.HotelProperty
import com.example.model.HotelReview
import com.example.model.OperationalAlert
import com.example.model.PlanLimits
import com.example.model.PriorityWeights
import com.example.model.RecommendedAction
import com.example.model.ReportFilterConfig
import com.example.model.ReportPeriod
import com.example.model.ReviewLanguage
import com.example.model.ReviewSource
import com.example.model.ReviewTriageCategory
import com.example.model.SeedReviewsProvider
import com.example.model.SentimentType
import com.example.model.TaskDepartmentNote
import com.example.model.TopicAspect
import com.example.model.TopicPatternSignal
import com.example.model.TriagePriority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface ReviewsUiState {
    data object Loading : ReviewsUiState
    data class Success(
        val profile: HotelProfile,
        val reviews: List<HotelReview>,
        val overallSnapshot: HotelAnalyticsSnapshot
    ) : ReviewsUiState
    data class Error(val message: String) : ReviewsUiState
}

data class ReviewListFilterState(
    val searchQuery: String = "",
    val sentimentFilter: SentimentType? = null,
    val departmentFilter: HotelDepartment? = null,
    val sourceFilter: ReviewSource? = null,
    val topicFilter: TopicAspect? = null,
    val triageFilter: ReviewTriageCategory? = null,
    val onlyUnresolved: Boolean = false,
    val needsConfirmationOnly: Boolean = false
)

sealed interface ReportBundleState {
    data object Idle : ReportBundleState
    data object Generating : ReportBundleState
    data class Ready(
        val pdfFile: File,
        val excelFile: File,
        val pdfPageBitmaps: List<Bitmap>,
        val excelSheets: List<ExcelSheetPreview>,
        val snapshot: HotelAnalyticsSnapshot,
        val filteredReviewsCount: Int,
        val periodDescription: String,
        val executiveSummary: String?,
        val patternsCount: Int = 0,
        val recommendationsCount: Int = 0
    ) : ReportBundleState
    data class Error(val message: String) : ReportBundleState
}

class HotelPulseViewModel(
    private val repository: HotelRepository?,
    private val aiService: HotelAiService?,
    private val operationalStore: OperationalStateStore,
    private val currentUserId: String,
    val isDemoMode: Boolean = false
) : ViewModel() {

    private val _selectedPropertyId = MutableStateFlow(HotelProperty.ALL_ID)
    val selectedPropertyId: StateFlow<String> = _selectedPropertyId.asStateFlow()

    private val _ops = MutableStateFlow(loadOrBootstrapOperationalState())
    val operationalState: StateFlow<OperationalSnapshot> = _ops.asStateFlow()

    private val _demoProfile = MutableStateFlow(
        HotelProfile(
            id = "demo",
            userId = "demo",
            hotelName = "HostPulse Portfolio (Demo)",
            hotelLocation = "Ελλάδα · 10 Καταλύματα"
        )
    )
    private val _demoReviews = MutableStateFlow(_ops.value.demoReviews.orEmpty())

    private val _listFilter = MutableStateFlow(ReviewListFilterState())
    val listFilter: StateFlow<ReviewListFilterState> = _listFilter.asStateFlow()

    private val _patternWindowDays = MutableStateFlow(30)
    val patternWindowDays: StateFlow<Int> = _patternWindowDays.asStateFlow()

    private val _patternSourceFilter = MutableStateFlow<ReviewSource?>(null)
    val patternSourceFilter: StateFlow<ReviewSource?> = _patternSourceFilter.asStateFlow()

    private val _reportConfig = MutableStateFlow(ReportFilterConfig())
    val reportConfig: StateFlow<ReportFilterConfig> = _reportConfig.asStateFlow()

    private val _reportBundleState = MutableStateFlow<ReportBundleState>(ReportBundleState.Idle)
    val reportBundleState: StateFlow<ReportBundleState> = _reportBundleState.asStateFlow()

    private val _isBusyAction = MutableStateFlow(false)
    val isBusyAction: StateFlow<Boolean> = _isBusyAction.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private fun loadOrBootstrapOperationalState(): OperationalSnapshot {
        val loaded = operationalStore.load()
        if (!isDemoMode) return loaded

        if (loaded.demoReviews != null && loaded.demoReviews.isNotEmpty()) {
            return loaded
        }

        // First-time Demo Mode bootstrap: 24 multi-property reviews across 10 Greek properties
        // + 2 initial tasks matching Mock1 (demo_3 maintenance IN_PROGRESS, demo_6 clean OPEN)
        val now = System.currentTimeMillis()
        val bundles = SeedReviewsProvider.buildMultiPropertySeedBundles("demo", now)
        val reviews = bundles.map { it.review }
        val propMap = bundles.associate { it.review.id to it.propertyId }
        val initialChecked = setOf("demo_3", "demo_6")

        val demo3 = reviews.firstOrNull { it.id == "demo_3" }
        val demo6 = reviews.firstOrNull { it.id == "demo_6" }
        val initialTasks = buildList {
            if (demo3 != null) {
                val demo3PropId = propMap[demo3.id] ?: "p1"
                val demo3Issue = HostPulseEngine.analyzeReviewText(demo3.reviewText)
                    .pendingIssues
                    .firstOrNull { it.category == ActionCategory.MAINTENANCE }
                val demo3Quote = demo3Issue?.quote ?: "Ο κλιματισμός ήταν χαλασμένος."
                val demo3Severity = demo3Issue?.severity ?: 3
                add(
                    AssignedActionTask(
                        id = "t_seed_1",
                        key = HostPulseEngine.actionKey(demo3.id, ActionCategory.MAINTENANCE),
                        reviewId = demo3.id,
                        propertyId = demo3PropId,
                        propertyName = HotelProperty.displayName(demo3PropId),
                        category = ActionCategory.MAINTENANCE,
                        departmentNameEl = ActionCategory.MAINTENANCE.departmentNameEl,
                        title = HostPulseEngine.smartActionTitle(ActionCategory.MAINTENANCE, demo3Quote),
                        quote = demo3Quote,
                        severity = demo3Severity,
                        effortMinutes = ActionCategory.MAINTENANCE.effortMinutes,
                        costEuros = ActionCategory.MAINTENANCE.costEuros,
                        deadlineLabel = if (demo3Severity == 3) "Σήμερα" else "Εντός 48 ωρών",
                        status = ActionTaskStatus.IN_PROGRESS,
                        createdAtMillis = now - 2L * DAY_MS,
                        notes = listOf(
                            TaskDepartmentNote(
                                id = "note_seed_1",
                                fromDepartment = "Συντήρηση",
                                toDepartment = "Υποδοχή",
                                text = "Έγινε έλεγχος της μονάδας A/C και παραγγέλθηκε ανταλλακτικό. Προσωρινά τοποθετήθηκε φορητό κλιματιστικό — παρακαλούμε ενημερώστε τον επισκέπτη.",
                                createdAtMillis = now - 1L * DAY_MS
                            )
                        )
                    )
                )
            }
            if (demo6 != null) {
                val demo6PropId = propMap[demo6.id] ?: "p2"
                val demo6Issue = HostPulseEngine.analyzeReviewText(demo6.reviewText)
                    .pendingIssues
                    .firstOrNull { it.category == ActionCategory.CLEAN }
                val demo6Quote = demo6Issue?.quote ?: "Βρώμικες πετσέτες και μούχλα στο μπάνιο."
                val demo6Severity = demo6Issue?.severity ?: 3
                add(
                    AssignedActionTask(
                        id = "t_seed_2",
                        key = HostPulseEngine.actionKey(demo6.id, ActionCategory.CLEAN),
                        reviewId = demo6.id,
                        propertyId = demo6PropId,
                        propertyName = HotelProperty.displayName(demo6PropId),
                        category = ActionCategory.CLEAN,
                        departmentNameEl = ActionCategory.CLEAN.departmentNameEl,
                        title = ActionCategory.CLEAN.defaultActionTitle,
                        quote = demo6Quote,
                        severity = demo6Severity,
                        effortMinutes = ActionCategory.CLEAN.effortMinutes,
                        costEuros = ActionCategory.CLEAN.costEuros,
                        deadlineLabel = if (demo6Severity == 3) "Σήμερα" else "Εντός 48 ωρών",
                        status = ActionTaskStatus.OPEN,
                        createdAtMillis = now - 3L * DAY_MS,
                        notes = listOf(
                            TaskDepartmentNote(
                                id = "note_seed_2",
                                fromDepartment = "Υποδοχή",
                                toDepartment = "Καθαριότητα",
                                text = "Προτεραιότητα πριν το επόμενο check-in στις 15:00 — απαιτείται βαθύς καθαρισμός μπάνιου και αλλαγή λευκών ειδών.",
                                createdAtMillis = now - 2L * DAY_MS
                            )
                        )
                    )
                )
            }
        }

        val fresh = OperationalSnapshot(
            reviewPropertyMap = propMap,
            checkedReviewIds = initialChecked,
            assignedTasks = initialTasks,
            weights = PriorityWeights(severityWeight = 4, recurrenceWeight = 3),
            limits = PlanLimits(maxMinutes = 120, maxBudgetEuros = 150),
            demoReviews = reviews
        )
        operationalStore.save(fresh)
        return fresh
    }

    private fun updateOperationalState(transform: (OperationalSnapshot) -> OperationalSnapshot) {
        _ops.update { prev ->
            val next = transform(prev)
            operationalStore.save(next)
            next
        }
    }

    // Core UI State (Cloud Mode via Firestore Flow OR Demo Mode via local MutableStateFlow)
    val uiState: StateFlow<ReviewsUiState> = if (isDemoMode || repository == null) {
        combine(_demoProfile, _demoReviews) { profile, reviews ->
            val sorted = reviews.sortedByDescending { it.stayDateMillis }
            ReviewsUiState.Success(
                profile = profile,
                reviews = sorted,
                overallSnapshot = HotelAnalyticsSnapshot.fromReviews(sorted)
            ) as ReviewsUiState
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ReviewsUiState.Success(
                profile = _demoProfile.value,
                reviews = _demoReviews.value.sortedByDescending { it.stayDateMillis },
                overallSnapshot = HotelAnalyticsSnapshot.fromReviews(_demoReviews.value)
            )
        )
    } else {
        combine(
            repository.observeHotelProfile(currentUserId),
            repository.observeReviews(currentUserId)
        ) { profile, reviews ->
            val resolvedProfile = profile ?: HotelProfile(userId = currentUserId)
            val snapshot = HotelAnalyticsSnapshot.fromReviews(reviews)
            ReviewsUiState.Success(
                profile = resolvedProfile,
                reviews = reviews,
                overallSnapshot = snapshot
            ) as ReviewsUiState
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(
                ReviewsUiState.Error(
                    rootError.localizedMessage ?: "Αποτυχία φόρτωσης κριτικών από το Cloud."
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ReviewsUiState.Loading
        )
    }

    // Reviews filtered by the persistent top Property Selector
    val propertyScopedReviews: StateFlow<List<HotelReview>> = combine(
        uiState,
        _selectedPropertyId,
        _ops
    ) { state, propId, ops ->
        val all = (state as? ReviewsUiState.Success)?.reviews ?: return@combine emptyList()
        if (propId == HotelProperty.ALL_ID) {
            all
        } else {
            all.filter { review -> (ops.reviewPropertyMap[review.id] ?: "p1") == propId }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val propertySnapshot: StateFlow<HotelAnalyticsSnapshot> = combine(
        propertyScopedReviews,
        _selectedPropertyId
    ) { scoped, _ ->
        HotelAnalyticsSnapshot.fromReviews(scoped)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HotelAnalyticsSnapshot.fromReviews(emptyList())
    )

    val filteredReviewsForList: StateFlow<List<HotelReview>> = combine(
        propertyScopedReviews,
        _listFilter,
        _ops
    ) { scoped, filter, ops ->
        scoped.filter { review ->
            val analysis = HostPulseEngine.analyzeReviewText(review.reviewText)
            val propName = HotelProperty.displayName(ops.reviewPropertyMap[review.id])

            val matchesQuery = filter.searchQuery.isBlank() ||
                HostPulseEngine.accentInsensitiveContains(review.guestName, filter.searchQuery) ||
                HostPulseEngine.accentInsensitiveContains(review.reviewText, filter.searchQuery) ||
                HostPulseEngine.accentInsensitiveContains(review.summaryLine, filter.searchQuery) ||
                HostPulseEngine.accentInsensitiveContains(propName, filter.searchQuery) ||
                review.keyHighlights.any { HostPulseEngine.accentInsensitiveContains(it, filter.searchQuery) }

            val matchesSentiment = filter.sentimentFilter == null ||
                review.sentimentEnum == filter.sentimentFilter

            val matchesDepartment = filter.departmentFilter == null ||
                filter.departmentFilter.name in review.departments

            val matchesSource = filter.sourceFilter == null ||
                review.sourceEnum == filter.sourceFilter

            val matchesTopic = filter.topicFilter == null ||
                analysis.aspects.any { it.category.topicAspect == filter.topicFilter } ||
                analysis.issues.any { it.category.topicAspect == filter.topicFilter }

            val matchesTriage = filter.triageFilter == null ||
                analysis.triageCategory == filter.triageFilter

            val matchesUnresolved = !filter.onlyUnresolved ||
                (!review.resolved && analysis.pendingIssues.isNotEmpty())

            val matchesNeedsConfirm = !filter.needsConfirmationOnly ||
                (analysis.pendingIssues.isNotEmpty() && review.id !in ops.checkedReviewIds)

            matchesQuery && matchesSentiment && matchesDepartment && matchesSource &&
                matchesTopic && matchesTriage && matchesUnresolved && matchesNeedsConfirm
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val recommendedActions: StateFlow<List<RecommendedAction>> = combine(
        uiState,
        _selectedPropertyId,
        _ops
    ) { state, propId, ops ->
        val all = (state as? ReviewsUiState.Success)?.reviews ?: return@combine emptyList()
        HostPulseEngine.buildRecommendations(
            reviews = all,
            reviewPropertyMap = ops.reviewPropertyMap,
            selectedPropertyId = propId,
            assignedTasks = ops.assignedTasks,
            checkedReviewIds = ops.checkedReviewIds,
            weights = ops.weights,
            limits = ops.limits,
            nowMillis = System.currentTimeMillis()
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val dailyActionPlan: StateFlow<DailyActionPlan> = combine(
        recommendedActions,
        _ops
    ) { recs, ops ->
        HostPulseEngine.buildDailyPlan(
            candidates = recs,
            limits = ops.limits,
            maxItems = 3
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DailyActionPlan(emptyList(), 0, 0)
    )

    val scopedAssignedTasks: StateFlow<List<AssignedActionTask>> = combine(
        _ops,
        _selectedPropertyId
    ) { ops, propId ->
        ops.assignedTasks
            .filter { propId == HotelProperty.ALL_ID || it.propertyId == propId }
            .sortedByDescending { it.createdAtMillis }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val operationalAlerts: StateFlow<List<OperationalAlert>> = combine(
        uiState,
        _selectedPropertyId,
        _ops
    ) { state, propId, ops ->
        val all = (state as? ReviewsUiState.Success)?.reviews ?: return@combine emptyList()
        HostPulseEngine.buildOperationalAlerts(
            reviews = all,
            assignedTasks = ops.assignedTasks,
            reviewPropertyMap = ops.reviewPropertyMap,
            selectedPropertyId = propId,
            rules = ops.alertRules,
            dismissedAlertIds = ops.dismissedAlertIds,
            nowMillis = System.currentTimeMillis()
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val topicPatterns: StateFlow<List<TopicPatternSignal>> = combine(
        uiState,
        _selectedPropertyId,
        _ops,
        _patternWindowDays,
        _patternSourceFilter
    ) { state, propId, ops, windowDays, sourceFilter ->
        val all = (state as? ReviewsUiState.Success)?.reviews ?: return@combine emptyList()
        HostPulseEngine.buildTopicPatterns(
            reviews = all,
            reviewPropertyMap = ops.reviewPropertyMap,
            selectedPropertyId = propId,
            windowDays = windowDays,
            sourceFilter = sourceFilter,
            nowMillis = System.currentTimeMillis()
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val reportMatchingReviews: StateFlow<List<HotelReview>> = combine(
        propertyScopedReviews,
        _reportConfig
    ) { scoped, config ->
        filterReviewsForReport(scoped, config, System.currentTimeMillis())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun postMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun selectProperty(propertyId: String) {
        _selectedPropertyId.value = propertyId
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun updateSearchQuery(query: String) {
        _listFilter.update { it.copy(searchQuery = query) }
    }

    fun updateSentimentFilter(sentiment: SentimentType?) {
        _listFilter.update { it.copy(sentimentFilter = sentiment) }
    }

    fun updateDepartmentFilter(department: HotelDepartment?) {
        _listFilter.update { it.copy(departmentFilter = department) }
    }

    fun updateSourceFilter(source: ReviewSource?) {
        _listFilter.update { it.copy(sourceFilter = source) }
    }

    fun updateTopicFilter(topic: TopicAspect?) {
        _listFilter.update { it.copy(topicFilter = topic) }
    }

    fun updateTriageFilter(triage: ReviewTriageCategory?) {
        _listFilter.update { it.copy(triageFilter = triage) }
    }

    fun toggleUnresolvedOnly(onlyUnresolved: Boolean) {
        _listFilter.update { it.copy(onlyUnresolved = onlyUnresolved) }
    }

    fun toggleNeedsConfirmationOnly(needsOnly: Boolean) {
        _listFilter.update { it.copy(needsConfirmationOnly = needsOnly) }
    }

    fun clearAllReviewFilters() {
        _listFilter.value = ReviewListFilterState()
    }

    fun applyDepartmentFromDashboard(department: HotelDepartment) {
        _listFilter.value = ReviewListFilterState(departmentFilter = department)
    }

    fun applyPatternToReviewsFilter(aspect: TopicAspect, source: ReviewSource?) {
        _listFilter.value = ReviewListFilterState(
            topicFilter = aspect,
            sourceFilter = source
        )
    }

    fun updatePatternWindow(days: Int) {
        _patternWindowDays.value = days
    }

    fun updatePatternSource(source: ReviewSource?) {
        _patternSourceFilter.value = source
    }

    // -----------------------------------------------------------------------
    // Human-in-the-loop Confirmation & Action Recommender Workflow
    // -----------------------------------------------------------------------

    fun confirmReviewAnalysis(reviewId: String) {
        if (reviewId.isBlank()) return
        updateOperationalState { current ->
            current.copy(checkedReviewIds = current.checkedReviewIds + reviewId)
        }
        _snackbarMessage.value = "Η ανάλυση επιβεβαιώθηκε. Μπορείτε πλέον να εγκρίνετε & να αναθέσετε ενέργειες."
    }

    fun approveAndAssignAction(action: RecommendedAction): Boolean {
        val currentOps = _ops.value
        if (action.reviewId !in currentOps.checkedReviewIds) {
            _snackbarMessage.value = "Απαιτείται πρώτα «Επιβεβαίωση ανάλυσης» από τον υπεύθυνο πριν την ανάθεση."
            return false
        }
        if (currentOps.assignedTasks.any { it.key == action.key }) {
            _snackbarMessage.value = "Η ενέργεια έχει ήδη ανατεθεί."
            return false
        }
        val newTask = AssignedActionTask(
            id = "task_${UUID.randomUUID().toString().take(8)}",
            key = action.key,
            reviewId = action.reviewId,
            propertyId = action.propertyId,
            propertyName = action.propertyName,
            category = action.category,
            departmentNameEl = action.departmentNameEl,
            title = action.title,
            quote = action.quote,
            severity = action.severity,
            effortMinutes = action.effortMinutes,
            costEuros = action.costEuros,
            deadlineLabel = action.deadlineLabel,
            status = ActionTaskStatus.OPEN,
            createdAtMillis = System.currentTimeMillis()
        )
        updateOperationalState { current ->
            current.copy(assignedTasks = listOf(newTask) + current.assignedTasks)
        }
        _reportBundleState.value = ReportBundleState.Idle
        _snackbarMessage.value = "Η ενέργεια ανατέθηκε στο τμήμα «${action.departmentNameEl}»."
        return true
    }

    fun startTask(taskId: String) {
        val task = _ops.value.assignedTasks.firstOrNull { it.id == taskId } ?: return
        if (!task.status.canTransitionTo(ActionTaskStatus.IN_PROGRESS)) return
        updateOperationalState { current ->
            current.copy(
                assignedTasks = current.assignedTasks.map {
                    if (it.id == taskId) it.copy(status = ActionTaskStatus.IN_PROGRESS) else it
                }
            )
        }
        _reportBundleState.value = ReportBundleState.Idle
        _snackbarMessage.value = "Η εργασία μπήκε σε κατάσταση «Σε εξέλιξη»."
    }

    fun completeTask(taskId: String) {
        val task = _ops.value.assignedTasks.firstOrNull { it.id == taskId } ?: return
        if (!task.status.canTransitionTo(ActionTaskStatus.DONE)) return
        updateOperationalState { current ->
            current.copy(
                assignedTasks = current.assignedTasks.map {
                    if (it.id == taskId) it.copy(status = ActionTaskStatus.DONE) else it
                }
            )
        }
        // Check if all pending issues of that review are now DONE; if so, mark review resolved
        val allReviews = (uiState.value as? ReviewsUiState.Success)?.reviews.orEmpty()
        val matchingReview = allReviews.firstOrNull { it.id == task.reviewId }
        if (matchingReview != null && !matchingReview.resolved) {
            val analysis = HostPulseEngine.analyzeReviewText(matchingReview.reviewText)
            val allDone = analysis.pendingIssues.isNotEmpty() && analysis.pendingIssues.all { issue ->
                val k = HostPulseEngine.actionKey(matchingReview.id, issue.category)
                _ops.value.assignedTasks.any { it.key == k && it.status == ActionTaskStatus.DONE }
            }
            if (allDone) {
                toggleResolvedAndSaveReply(
                    review = matchingReview,
                    newResolved = true,
                    updatedReply = matchingReview.suggestedReply,
                    silent = true
                )
            }
        }
        _reportBundleState.value = ReportBundleState.Idle
        _snackbarMessage.value = "Η εργασία ολοκληρώθηκε."
    }

    fun updatePriorityWeights(severityWeight: Int, recurrenceWeight: Int) {
        updateOperationalState { current ->
            current.copy(
                weights = PriorityWeights(
                    severityWeight = severityWeight.coerceIn(1, 5),
                    recurrenceWeight = recurrenceWeight.coerceIn(1, 5)
                )
            )
        }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun updatePlanLimits(maxMinutes: Int, maxBudgetEuros: Int) {
        updateOperationalState { current ->
            current.copy(
                limits = PlanLimits(
                    maxMinutes = maxMinutes.coerceIn(15, 480),
                    maxBudgetEuros = maxBudgetEuros.coerceIn(0, 1000)
                )
            )
        }
    }

    fun addTaskNote(
        taskId: String,
        fromDepartment: String,
        toDepartment: String,
        text: String
    ): Boolean {
        val cleanText = text.trim().take(500)
        if (cleanText.isEmpty()) {
            _snackbarMessage.value = "Γράψτε μια σύντομη εσωτερική σημείωση."
            return false
        }
        val target = _ops.value.assignedTasks.firstOrNull { it.id == taskId } ?: return false
        if (target.notes.size >= 30) {
            _snackbarMessage.value = "Συμπληρώθηκε το μέγιστο όριο σημειώσεων για αυτή την εργασία."
            return false
        }
        val note = TaskDepartmentNote(
            id = "note_${UUID.randomUUID().toString().take(8)}",
            fromDepartment = fromDepartment.trim().ifBlank { target.departmentNameEl },
            toDepartment = toDepartment.trim().ifBlank { "Υποδοχή" },
            text = cleanText,
            createdAtMillis = System.currentTimeMillis()
        )
        updateOperationalState { current ->
            current.copy(
                assignedTasks = current.assignedTasks.map { t ->
                    if (t.id == taskId) t.copy(notes = t.notes + note) else t
                }
            )
        }
        _snackbarMessage.value = "Προστέθηκε σημείωση (${note.fromDepartment} → ${note.toDepartment})."
        return true
    }

    fun updateAlertRules(lowRatingThreshold: Double, overdueHours: Int) {
        updateOperationalState { current ->
            current.copy(
                alertRules = AlertRulesConfig(
                    lowRatingThreshold = lowRatingThreshold.coerceIn(1.0, 9.0),
                    overdueHours = overdueHours.coerceIn(12, 168)
                )
            )
        }
    }

    fun dismissAlert(alertId: String) {
        if (alertId.isBlank()) return
        updateOperationalState { current ->
            current.copy(dismissedAlertIds = current.dismissedAlertIds + alertId)
        }
        _snackbarMessage.value = "Η ειδοποίηση σημειώθηκε ως αναγνωσμένη."
    }

    fun restoreDismissedAlerts() {
        updateOperationalState { current ->
            current.copy(dismissedAlertIds = emptySet())
        }
        _snackbarMessage.value = "Επαναφέρθηκαν οι σιγασμένες ειδοποιήσεις."
    }

    // -----------------------------------------------------------------------
    // Report Studio Config Mutations
    // -----------------------------------------------------------------------

    fun updateReportPeriod(period: ReportPeriod, customStart: Long? = null, customEnd: Long? = null) {
        _reportConfig.update {
            it.copy(
                period = period,
                customStartMillis = customStart ?: it.customStartMillis,
                customEndMillis = customEnd ?: it.customEndMillis
            )
        }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun toggleReportSentiment(sentiment: SentimentType) {
        _reportConfig.update { current ->
            val updated = if (sentiment in current.selectedSentiments && current.selectedSentiments.size > 1) {
                current.selectedSentiments - sentiment
            } else {
                current.selectedSentiments + sentiment
            }
            current.copy(selectedSentiments = updated)
        }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun toggleReportDepartment(department: HotelDepartment) {
        _reportConfig.update { current ->
            val updated = if (department in current.selectedDepartments) {
                current.selectedDepartments - department
            } else {
                current.selectedDepartments + department
            }
            current.copy(selectedDepartments = updated)
        }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun clearReportDepartmentFilter() {
        _reportConfig.update { it.copy(selectedDepartments = emptySet()) }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun setReportIncludeAiSummary(include: Boolean) {
        _reportConfig.update { it.copy(includeAiExecutiveSummary = include) }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun setReportIncludePatternsAndActions(include: Boolean) {
        _reportConfig.update { it.copy(includePatternsAndActions = include) }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun setReportOnlyUnresolved(onlyUnresolved: Boolean) {
        _reportConfig.update { it.copy(onlyUnresolved = onlyUnresolved) }
        _reportBundleState.value = ReportBundleState.Idle
    }

    // -----------------------------------------------------------------------
    // Hotel Profile & Reviews CRUD (Cloud Mode + Demo Mode)
    // -----------------------------------------------------------------------

    fun saveHotelSettings(hotelName: String, hotelLocation: String) {
        if (isDemoMode || repository == null) {
            _demoProfile.update {
                it.copy(
                    hotelName = hotelName.trim().ifBlank { it.hotelName },
                    hotelLocation = hotelLocation.trim()
                )
            }
            _snackbarMessage.value = "Τα στοιχεία ενημερώθηκαν (Demo Mode)."
            return
        }
        viewModelScope.launch {
            _isBusyAction.value = true
            val result = repository.saveHotelProfile(currentUserId, hotelName, hotelLocation)
            _isBusyAction.value = false
            result.fold(
                onSuccess = { _snackbarMessage.value = "Τα στοιχεία του ξενοδοχείου ενημερώθηκαν." },
                onFailure = { e -> _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία αποθήκευσης." }
            )
        }
    }

    fun seedDemoReviews() {
        if (isDemoMode || repository == null) {
            resetDemoData()
            return
        }
        viewModelScope.launch {
            _isBusyAction.value = true
            val result = repository.seedMultiPropertySampleReviews(currentUserId)
            _isBusyAction.value = false
            result.fold(
                onSuccess = { docPropMap ->
                    updateOperationalState { current ->
                        current.copy(reviewPropertyMap = current.reviewPropertyMap + docPropMap)
                    }
                    _snackbarMessage.value = "Προστέθηκαν ${docPropMap.size} κριτικές σε 10 ελληνικά καταλύματα στο Cloud."
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία φόρτωσης δείγματος."
                }
            )
        }
    }

    fun resetDemoData() {
        if (!isDemoMode) return
        operationalStore.clear()
        val fresh = loadOrBootstrapOperationalState()
        _ops.value = fresh
        _demoReviews.value = fresh.demoReviews.orEmpty()
        _selectedPropertyId.value = HotelProperty.ALL_ID
        _reportBundleState.value = ReportBundleState.Idle
        _snackbarMessage.value = "Τα δεδομένα Demo (10 καταλύματα, 24 κριτικές) επαναφέρθηκαν."
    }

    fun analyzeAndCreateReview(
        guestName: String,
        propertyId: String,
        source: ReviewSource,
        rating: Double,
        stayDateMillis: Long,
        reviewText: String,
        onComplete: () -> Unit
    ) {
        val cleanText = reviewText.trim()
        if (cleanText.length < 4) {
            _snackbarMessage.value = "Παρακαλώ εισάγετε το πλήρες κείμενο της κριτικής."
            return
        }
        val targetPropertyId = if (HotelProperty.byId(propertyId) != null) propertyId else "p1"
        val cleanGuest = guestName.trim().ifBlank { "Επισκέπτης" }
        val localAnalysis = HostPulseEngine.analyzeReviewText(cleanText)
        val detectedLang = HostPulseEngine.detectLanguage(cleanText)

        viewModelScope.launch {
            _isBusyAction.value = true

            // In Demo Mode (no Cloud Auth), run instant deterministic 4-category triage without hitting Firebase
            if (isDemoMode || repository == null || aiService == null) {
                val newId = "demo_${UUID.randomUUID().toString().take(8)}"
                val depts = localAnalysis.aspects
                    .map { it.category.hotelDepartment.name }
                    .distinct()
                    .ifEmpty { listOf(HotelDepartment.STAFF_SERVICE.name) }
                val score = when (localAnalysis.overallSentiment) {
                    SentimentType.POSITIVE -> 86L
                    SentimentType.NEUTRAL -> 55L
                    SentimentType.NEGATIVE -> 24L
                }
                val prio = when {
                    localAnalysis.pendingIssues.any { it.severity == 3 } -> TriagePriority.HIGH
                    localAnalysis.pendingIssues.isNotEmpty() -> TriagePriority.MEDIUM
                    else -> TriagePriority.LOW
                }
                val summary = when (localAnalysis.triageCategory) {
                    ReviewTriageCategory.REAL_PENDING ->
                        "Εκκρεμές πρόβλημα: ${localAnalysis.pendingIssues.firstOrNull()?.category?.labelEl ?: "Γενικό"}"
                    ReviewTriageCategory.REAL_RESOLVED -> "Ήδη λυμένο περιστατικό — χωρίς εκκρεμή ενέργεια"
                    ReviewTriageCategory.HYPOTHETICAL -> "Επιθυμία / υπόθεση — χωρίς εκκρεμή ενέργεια"
                    ReviewTriageCategory.POSITIVE_COMMENT -> "Θετικό σχόλιο επισκέπτη"
                    ReviewTriageCategory.INSUFFICIENT -> "Απαιτείται ανθρώπινος έλεγχος"
                }
                val draftReview = HotelReview(
                    id = newId,
                    userId = "demo",
                    guestName = cleanGuest,
                    source = source.name,
                    language = detectedLang.name,
                    rating = rating,
                    stayDateMillis = stayDateMillis,
                    reviewText = cleanText,
                    sentiment = localAnalysis.overallSentiment.name,
                    sentimentScore = score,
                    priority = prio.name,
                    departments = depts,
                    keyHighlights = localAnalysis.aspects.map { "${it.category.labelEl}: ${it.sentiment.labelEl}" }.take(4),
                    summaryLine = summary,
                    suggestedReply = "",
                    resolved = localAnalysis.pendingIssues.isEmpty() && rating > _ops.value.alertRules.lowRatingThreshold
                )
                val finalReview = draftReview.copy(
                    suggestedReply = HostPulseEngine.draftTemplateReply(draftReview, localAnalysis)
                )
                val updatedList = listOf(finalReview) + _demoReviews.value
                _demoReviews.value = updatedList
                updateOperationalState { current ->
                    current.copy(
                        reviewPropertyMap = current.reviewPropertyMap + (newId to targetPropertyId),
                        demoReviews = updatedList
                    )
                }
                _isBusyAction.value = false
                _reportBundleState.value = ReportBundleState.Idle
                val isCriticalAlert = rating <= _ops.value.alertRules.lowRatingThreshold ||
                    localAnalysis.pendingIssues.any { it.severity == 3 }
                _snackbarMessage.value = if (isCriticalAlert) {
                    "Κρίσιμη ειδοποίηση (${String.format(Locale.US, "%.1f", rating)}/10) — προστέθηκε στο Κέντρο Ειδοποιήσεων."
                } else {
                    "Η κριτική αναλύθηκε (${localAnalysis.triageCategory.labelEl}) και προστέθηκε."
                }
                onComplete()
                return@launch
            }

            // Cloud Mode: analyze via Firebase AI Logic (Gemini) + save to Firestore
            val hotelName = HotelProperty.displayName(targetPropertyId)
            val aiResult = aiService.analyzeReview(
                guestName = cleanGuest,
                rating = rating,
                reviewText = cleanText,
                hotelName = hotelName
            )

            aiResult.fold(
                onSuccess = { triage ->
                    val newReview = HotelReview(
                        userId = currentUserId,
                        guestName = cleanGuest,
                        source = source.name,
                        language = triage.language.name,
                        rating = rating,
                        stayDateMillis = stayDateMillis,
                        reviewText = cleanText,
                        sentiment = triage.sentiment.name,
                        sentimentScore = triage.sentimentScore,
                        priority = triage.priority.name,
                        departments = triage.departments.map { it.name },
                        keyHighlights = triage.keyHighlights,
                        summaryLine = triage.summaryLine,
                        suggestedReply = triage.suggestedReply,
                        resolved = localAnalysis.pendingIssues.isEmpty() &&
                            triage.sentiment == SentimentType.POSITIVE &&
                            rating > _ops.value.alertRules.lowRatingThreshold
                    )
                    val saveRes = repository.addReview(currentUserId, newReview)
                    _isBusyAction.value = false
                    saveRes.fold(
                        onSuccess = { docId ->
                            updateOperationalState { current ->
                                current.copy(reviewPropertyMap = current.reviewPropertyMap + (docId to targetPropertyId))
                            }
                            _reportBundleState.value = ReportBundleState.Idle
                            val isCriticalAlert = rating <= _ops.value.alertRules.lowRatingThreshold ||
                                localAnalysis.pendingIssues.any { it.severity == 3 }
                            _snackbarMessage.value = if (isCriticalAlert) {
                                "Κρίσιμη ειδοποίηση (${String.format(Locale.US, "%.1f", rating)}/10) — αποθηκεύτηκε & ενεργοποιήθηκε alert."
                            } else {
                                "Η κριτική αναλύθηκε με AI (${localAnalysis.triageCategory.labelEl}) και αποθηκεύτηκε."
                            }
                            onComplete()
                        },
                        onFailure = { err ->
                            _snackbarMessage.value = err.localizedMessage ?: "Σφάλμα αποθήκευσης κριτικής."
                        }
                    )
                },
                onFailure = { err ->
                    _isBusyAction.value = false
                    _snackbarMessage.value = err.localizedMessage ?: "Η ανάλυση AI απέτυχε."
                }
            )
        }
    }

    fun toggleResolvedAndSaveReply(
        review: HotelReview,
        newResolved: Boolean,
        updatedReply: String,
        silent: Boolean = false
    ) {
        val statusChanged = newResolved != review.resolved
        val successMsg = when {
            !statusChanged -> "Η απάντηση αποθηκεύτηκε."
            newResolved -> "Η κριτική σημειώθηκε ως τακτοποιημένη."
            else -> "Η κριτική επανήλθε σε εκκρεμή."
        }
        if (isDemoMode || repository == null) {
            val updatedList = _demoReviews.value.map {
                if (it.id == review.id) it.copy(resolved = newResolved, suggestedReply = updatedReply.trim()) else it
            }
            _demoReviews.value = updatedList
            updateOperationalState { it.copy(demoReviews = updatedList) }
            if (!silent) _snackbarMessage.value = successMsg
            return
        }
        viewModelScope.launch {
            val result = repository.updateReviewResolutionAndReply(
                userId = currentUserId,
                reviewId = review.id,
                resolved = newResolved,
                suggestedReply = updatedReply
            )
            result.fold(
                onSuccess = {
                    if (!silent) _snackbarMessage.value = successMsg
                },
                onFailure = { e ->
                    if (!silent) _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία ενημέρωσης κριτικής."
                }
            )
        }
    }

    fun regenerateReplyForReview(review: HotelReview, formalTone: Boolean) {
        if (isDemoMode || repository == null || aiService == null) {
            val analysis = HostPulseEngine.analyzeReviewText(review.reviewText)
            val baseReply = HostPulseEngine.draftTemplateReply(review, analysis)
            val styled = if (formalTone) {
                if (review.languageEnum == ReviewLanguage.EN) {
                    "Dear ${review.guestName}, on behalf of our management team, we sincerely appreciate your detailed review. $baseReply"
                } else {
                    "Αξιότιμε/η ${review.guestName}, εκ μέρους της διεύθυνσης σας ευχαριστούμε για τα σχόλιά σας. $baseReply"
                }
            } else {
                baseReply
            }
            toggleResolvedAndSaveReply(review, review.resolved, styled)
            _snackbarMessage.value = "Δημιουργήθηκε νέα προτεινόμενη απάντηση."
            return
        }
        viewModelScope.launch {
            _isBusyAction.value = true
            val propName = HotelProperty.displayName(_ops.value.reviewPropertyMap[review.id])
            val result = aiService.regenerateGuestReply(review, propName, formalTone)
            result.fold(
                onSuccess = { freshReply ->
                    repository.updateReviewResolutionAndReply(
                        userId = currentUserId,
                        reviewId = review.id,
                        resolved = review.resolved,
                        suggestedReply = freshReply
                    )
                    _isBusyAction.value = false
                    _snackbarMessage.value = "Δημιουργήθηκε νέα προτεινόμενη απάντηση AI."
                },
                onFailure = { e ->
                    _isBusyAction.value = false
                    _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία αναγέννησης απάντησης."
                }
            )
        }
    }

    fun deleteReview(reviewId: String) {
        if (isDemoMode || repository == null) {
            val updatedList = _demoReviews.value.filterNot { it.id == reviewId }
            _demoReviews.value = updatedList
            updateOperationalState { current ->
                current.copy(
                    demoReviews = updatedList,
                    checkedReviewIds = current.checkedReviewIds - reviewId,
                    assignedTasks = current.assignedTasks.filterNot { it.reviewId == reviewId }
                )
            }
            _reportBundleState.value = ReportBundleState.Idle
            _snackbarMessage.value = "Η κριτική διαγράφηκε."
            return
        }
        viewModelScope.launch {
            val result = repository.deleteReview(currentUserId, reviewId)
            result.onSuccess {
                updateOperationalState { current ->
                    current.copy(
                        checkedReviewIds = current.checkedReviewIds - reviewId,
                        assignedTasks = current.assignedTasks.filterNot { it.reviewId == reviewId }
                    )
                }
                _snackbarMessage.value = "Η κριτική διαγράφηκε."
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία διαγραφής."
            }
        }
    }

    // -----------------------------------------------------------------------
    // Enriched PDF + Multi-Sheet Excel (.xlsx) Export
    // -----------------------------------------------------------------------

    fun generateReportPreviewAndFiles(appContext: Context) {
        val currentSuccess = uiState.value as? ReviewsUiState.Success ?: return
        val config = _reportConfig.value
        val ops = _ops.value
        val selectedPropId = _selectedPropertyId.value
        val nowMillis = System.currentTimeMillis()

        val propScoped = if (selectedPropId == HotelProperty.ALL_ID) {
            currentSuccess.reviews
        } else {
            currentSuccess.reviews.filter { (ops.reviewPropertyMap[it.id] ?: "p1") == selectedPropId }
        }
        val matchingReviews = filterReviewsForReport(propScoped, config, nowMillis)

        if (matchingReviews.isEmpty()) {
            _reportBundleState.value = ReportBundleState.Error(
                "Δεν υπάρχουν κριτικές που να ταιριάζουν στα επιλεγμένα φίλτρα καταλύματος/περιόδου/τμημάτων."
            )
            return
        }

        viewModelScope.launch {
            _reportBundleState.value = ReportBundleState.Generating
            try {
                val snapshot = HotelAnalyticsSnapshot.fromReviews(matchingReviews)
                val periodDesc = buildPeriodDescription(config, nowMillis)
                val selectedProp = HotelProperty.byId(selectedPropId)
                val reportHotelName = selectedProp?.name ?: currentSuccess.profile.hotelName
                val reportLocation = selectedProp?.location ?: currentSuccess.profile.hotelLocation

                val matchingIds = matchingReviews.map { it.id }.toSet()

                // Pass full property-scoped reviews so previous-window rateChangePoints & 30d recurrence remain accurate
                val reportPatterns = if (config.includePatternsAndActions) {
                    val patWindow = when (config.period) {
                        ReportPeriod.LAST_7_DAYS -> 7
                        ReportPeriod.LAST_14_DAYS -> 14
                        ReportPeriod.LAST_30_DAYS -> 30
                        ReportPeriod.LAST_60_DAYS -> 60
                        else -> _patternWindowDays.value
                    }
                    HostPulseEngine.buildTopicPatterns(
                        reviews = propScoped,
                        reviewPropertyMap = ops.reviewPropertyMap,
                        selectedPropertyId = selectedPropId,
                        windowDays = patWindow,
                        sourceFilter = null,
                        nowMillis = nowMillis
                    ).filter { signal -> signal.reviewIds.any { it in matchingIds } }
                } else {
                    emptyList()
                }

                val reportRecommendations = if (config.includePatternsAndActions) {
                    HostPulseEngine.buildRecommendations(
                        reviews = propScoped,
                        reviewPropertyMap = ops.reviewPropertyMap,
                        selectedPropertyId = selectedPropId,
                        assignedTasks = ops.assignedTasks,
                        checkedReviewIds = ops.checkedReviewIds,
                        weights = ops.weights,
                        limits = ops.limits,
                        nowMillis = nowMillis
                    ).filter { it.reviewId in matchingIds }
                } else {
                    emptyList()
                }

                val scopedTasks = ops.assignedTasks.filter {
                    selectedPropId == HotelProperty.ALL_ID || it.propertyId == selectedPropId
                }

                val executiveSummary: String? = if (config.includeAiExecutiveSummary) {
                    if (!isDemoMode && aiService != null) {
                        aiService.generateExecutiveSummaryForReport(
                            hotelName = reportHotelName,
                            periodLabel = periodDesc,
                            snapshot = snapshot,
                            reviews = matchingReviews
                        ).getOrElse {
                            buildLocalExecutiveSummary(reportHotelName, snapshot, reportPatterns, reportRecommendations)
                        }
                    } else {
                        buildLocalExecutiveSummary(reportHotelName, snapshot, reportPatterns, reportRecommendations)
                    }
                } else {
                    null
                }

                val metadata = ReportMetadata(
                    hotelName = reportHotelName,
                    hotelLocation = reportLocation,
                    periodLabel = periodDesc,
                    generatedAtMillis = nowMillis,
                    executiveSummary = executiveSummary
                )

                val bundle = withContext(Dispatchers.IO) {
                    val reportsDir = File(appContext.cacheDir, "reports").apply { mkdirs() }
                    // Prune old cached reports, keeping only the most recent 6 files
                    reportsDir.listFiles()
                        ?.sortedByDescending { it.lastModified() }
                        ?.drop(6)
                        ?.forEach { it.delete() }
                    val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowMillis))
                    val pdfFile = File(reportsDir, "HotelPulse_Report_$dateStamp.pdf")
                    val xlsxFile = File(reportsDir, "HotelPulse_Report_$dateStamp.xlsx")

                    val generatedPdf = PdfReportGenerator.generatePdfAndPreview(
                        context = appContext,
                        outputFile = pdfFile,
                        metadata = metadata,
                        snapshot = snapshot,
                        reviews = matchingReviews,
                        patterns = reportPatterns,
                        recommendations = reportRecommendations,
                        reviewPropertyMap = ops.reviewPropertyMap,
                        assignedTasks = scopedTasks,
                        previewScale = 1.5f
                    )

                    val excelPreview = ExcelReportGenerator.generateExcelWorkbook(
                        outputFile = xlsxFile,
                        metadata = metadata,
                        snapshot = snapshot,
                        reviews = matchingReviews,
                        patterns = reportPatterns,
                        recommendations = reportRecommendations,
                        reviewPropertyMap = ops.reviewPropertyMap,
                        checkedReviewIds = ops.checkedReviewIds,
                        assignedTasks = scopedTasks
                    )

                    ReportBundleState.Ready(
                        pdfFile = generatedPdf.pdfFile,
                        excelFile = xlsxFile,
                        pdfPageBitmaps = generatedPdf.previewBitmaps,
                        excelSheets = excelPreview.sheets,
                        snapshot = snapshot,
                        filteredReviewsCount = matchingReviews.size,
                        periodDescription = periodDesc,
                        executiveSummary = executiveSummary,
                        patternsCount = reportPatterns.size,
                        recommendationsCount = reportRecommendations.size
                    )
                }
                _reportBundleState.value = bundle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _reportBundleState.value = ReportBundleState.Error(
                    e.localizedMessage ?: "Αποτυχία δημιουργίας αρχείων αναφοράς."
                )
            }
        }
    }

    private fun buildLocalExecutiveSummary(
        hotelName: String,
        snapshot: HotelAnalyticsSnapshot,
        patterns: List<TopicPatternSignal>,
        recommendations: List<RecommendedAction>
    ): String {
        val topPattern = patterns.firstOrNull()
        val topAction = recommendations.firstOrNull()
        return buildString {
            append("Για το $hotelName καταγράφηκαν ${snapshot.totalReviews} κριτικές με μέση βαθμολογία ${snapshot.formattedAverageRating}/10 (${snapshot.positivePercent}% θετικές). ")
            if (topPattern != null) {
                append("Κύριο επαναλαμβανόμενο μοτίβο: ${topPattern.aspect.labelEl} (${topPattern.negativeCount}/${topPattern.totalMentions} αρνητικές σε ${topPattern.propertyCount} καταλύματα). ")
            }
            if (topAction != null) {
                append("Προτεραιότητα #1 (σκορ ${String.format(Locale.US, "%.1f", topAction.priorityScore)}): ${topAction.title} (${topAction.departmentNameEl}, ${topAction.effortMinutes} λ., ${topAction.costEuros}€).")
            }
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        fun filterReviewsForReport(
            reviews: List<HotelReview>,
            config: ReportFilterConfig,
            nowMillis: Long
        ): List<HotelReview> {
            val startBound: Long? = when (config.period) {
                ReportPeriod.LAST_7_DAYS -> nowMillis - 7L * DAY_MS
                ReportPeriod.LAST_14_DAYS -> nowMillis - 14L * DAY_MS
                ReportPeriod.LAST_30_DAYS -> nowMillis - 30L * DAY_MS
                ReportPeriod.LAST_60_DAYS -> nowMillis - 60L * DAY_MS
                ReportPeriod.LAST_90_DAYS -> nowMillis - 90L * DAY_MS
                ReportPeriod.ALL_TIME -> null
                ReportPeriod.CUSTOM -> config.customStartMillis
            }
            val endBound: Long? = if (config.period == ReportPeriod.CUSTOM) {
                config.customEndMillis?.let { it + DAY_MS - 1L }
            } else {
                null
            }

            return reviews.filter { r ->
                val inDateRange = (startBound == null || r.stayDateMillis >= startBound) &&
                    (endBound == null || r.stayDateMillis <= endBound)
                val inSentiment = r.sentimentEnum in config.selectedSentiments
                val inDept = config.selectedDepartments.isEmpty() ||
                    r.departmentEnums.any { it in config.selectedDepartments }
                val inResolution = !config.onlyUnresolved || !r.resolved
                inDateRange && inSentiment && inDept && inResolution
            }
        }

        fun buildPeriodDescription(config: ReportFilterConfig, nowMillis: Long): String {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("el-GR"))
            return when (config.period) {
                ReportPeriod.ALL_TIME -> "Όλες οι περίοδοι"
                ReportPeriod.CUSTOM -> {
                    val s = config.customStartMillis?.let { fmt.format(Date(it)) } ?: "Αρχή"
                    val e = config.customEndMillis?.let { fmt.format(Date(it)) } ?: fmt.format(Date(nowMillis))
                    "$s – $e"
                }
                else -> {
                    val days = config.period.daysBack ?: 30
                    val startStr = fmt.format(Date(nowMillis - days * DAY_MS))
                    val endStr = fmt.format(Date(nowMillis))
                    "${config.period.labelEl} ($startStr – $endStr)"
                }
            }
        }
    }
}
