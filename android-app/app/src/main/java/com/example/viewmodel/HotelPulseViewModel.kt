package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HotelAiService
import com.example.data.HotelRepository
import com.example.export.ExcelReportGenerator
import com.example.export.ExcelSheetPreview
import com.example.export.PdfReportGenerator
import com.example.export.ReportMetadata
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelDepartment
import com.example.model.HotelProfile
import com.example.model.HotelReview
import com.example.model.ReportFilterConfig
import com.example.model.ReportPeriod
import com.example.model.ReviewSource
import com.example.model.SentimentType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val onlyUnresolved: Boolean = false
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
        val executiveSummary: String?
    ) : ReportBundleState
    data class Error(val message: String) : ReportBundleState
}

class HotelPulseViewModel(
    private val repository: HotelRepository,
    private val aiService: HotelAiService,
    private val currentUserId: String
) : ViewModel() {

    private val _listFilter = MutableStateFlow(ReviewListFilterState())
    val listFilter: StateFlow<ReviewListFilterState> = _listFilter.asStateFlow()

    private val _reportConfig = MutableStateFlow(ReportFilterConfig())
    val reportConfig: StateFlow<ReportFilterConfig> = _reportConfig.asStateFlow()

    private val _reportBundleState = MutableStateFlow<ReportBundleState>(ReportBundleState.Idle)
    val reportBundleState: StateFlow<ReportBundleState> = _reportBundleState.asStateFlow()

    private val _isBusyAction = MutableStateFlow(false)
    val isBusyAction: StateFlow<Boolean> = _isBusyAction.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Two-tier Kotlin Flow error handling unwrapping FirebaseFirestoreException causes
    val uiState: StateFlow<ReviewsUiState> = combine(
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

    val filteredReviewsForList: StateFlow<List<HotelReview>> = combine(
        uiState,
        _listFilter
    ) { state, filter ->
        val all = (state as? ReviewsUiState.Success)?.reviews ?: return@combine emptyList()
        all.filter { review ->
            val matchesQuery = filter.searchQuery.isBlank() ||
                review.guestName.contains(filter.searchQuery, ignoreCase = true) ||
                review.reviewText.contains(filter.searchQuery, ignoreCase = true) ||
                review.summaryLine.contains(filter.searchQuery, ignoreCase = true) ||
                review.keyHighlights.any { it.contains(filter.searchQuery, ignoreCase = true) }

            val matchesSentiment = filter.sentimentFilter == null ||
                review.sentimentEnum == filter.sentimentFilter

            val matchesDepartment = filter.departmentFilter == null ||
                filter.departmentFilter.name in review.departments

            val matchesUnresolved = !filter.onlyUnresolved || !review.resolved

            matchesQuery && matchesSentiment && matchesDepartment && matchesUnresolved
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val reportMatchingReviews: StateFlow<List<HotelReview>> = combine(
        uiState,
        _reportConfig
    ) { state, config ->
        val all = (state as? ReviewsUiState.Success)?.reviews ?: return@combine emptyList()
        filterReviewsForReport(all, config, System.currentTimeMillis())
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

    fun updateSearchQuery(query: String) {
        _listFilter.update { it.copy(searchQuery = query) }
    }

    fun updateSentimentFilter(sentiment: SentimentType?) {
        _listFilter.update { it.copy(sentimentFilter = sentiment) }
    }

    fun updateDepartmentFilter(department: HotelDepartment?) {
        _listFilter.update { it.copy(departmentFilter = department) }
    }

    fun toggleUnresolvedOnly(onlyUnresolved: Boolean) {
        _listFilter.update { it.copy(onlyUnresolved = onlyUnresolved) }
    }

    // Report config mutations invalidate stale generated bundle so user always previews fresh data
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

    fun setReportOnlyUnresolved(onlyUnresolved: Boolean) {
        _reportConfig.update { it.copy(onlyUnresolved = onlyUnresolved) }
        _reportBundleState.value = ReportBundleState.Idle
    }

    fun saveHotelSettings(hotelName: String, hotelLocation: String) {
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
        viewModelScope.launch {
            _isBusyAction.value = true
            val result = repository.seedSampleReviews(currentUserId)
            _isBusyAction.value = false
            result.fold(
                onSuccess = { count ->
                    _snackbarMessage.value = "Προστέθηκαν $count ρεαλιστικές κριτικές στο Cloud."
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία φόρτωσης δείγματος."
                }
            )
        }
    }

    fun analyzeAndCreateReview(
        guestName: String,
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
        viewModelScope.launch {
            _isBusyAction.value = true
            val hotelName = (uiState.value as? ReviewsUiState.Success)?.profile?.hotelName
                ?: "Hotel Pulse"

            val aiResult = aiService.analyzeReview(
                guestName = guestName.ifBlank { "Επισκέπτης" },
                rating = rating,
                reviewText = cleanText,
                hotelName = hotelName
            )

            aiResult.fold(
                onSuccess = { triage ->
                    val newReview = HotelReview(
                        userId = currentUserId,
                        guestName = guestName.trim().ifBlank { "Επισκέπτης" },
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
                        resolved = false
                    )
                    val saveRes = repository.addReview(currentUserId, newReview)
                    _isBusyAction.value = false
                    saveRes.fold(
                        onSuccess = {
                            _snackbarMessage.value = "Η κριτική αναλύθηκε με AI και αποθηκεύτηκε."
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

    fun toggleResolvedAndSaveReply(review: HotelReview, newResolved: Boolean, updatedReply: String) {
        viewModelScope.launch {
            val result = repository.updateReviewResolutionAndReply(
                userId = currentUserId,
                reviewId = review.id,
                resolved = newResolved,
                suggestedReply = updatedReply
            )
            result.fold(
                onSuccess = {
                    val statusMsg = if (newResolved) "Η κριτική σημειώθηκε ως τακτοποιημένη." else "Η κριτική επανήλθε σε εκκρεμή."
                    _snackbarMessage.value = statusMsg
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία ενημέρωσης κριτικής."
                }
            )
        }
    }

    fun regenerateReplyForReview(review: HotelReview, formalTone: Boolean) {
        viewModelScope.launch {
            _isBusyAction.value = true
            val hotelName = (uiState.value as? ReviewsUiState.Success)?.profile?.hotelName ?: "Hotel Pulse"
            val result = aiService.regenerateGuestReply(review, hotelName, formalTone)
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
        viewModelScope.launch {
            val result = repository.deleteReview(currentUserId, reviewId)
            result.onSuccess {
                _snackbarMessage.value = "Η κριτική διαγράφηκε."
            }.onFailure { e ->
                _snackbarMessage.value = e.localizedMessage ?: "Αποτυχία διαγραφής."
            }
        }
    }

    fun generateReportPreviewAndFiles(appContext: Context) {
        val currentSuccess = uiState.value as? ReviewsUiState.Success ?: return
        val config = _reportConfig.value
        val nowMillis = System.currentTimeMillis()
        val matchingReviews = filterReviewsForReport(currentSuccess.reviews, config, nowMillis)

        if (matchingReviews.isEmpty()) {
            _reportBundleState.value = ReportBundleState.Error(
                "Δεν υπάρχουν κριτικές που να ταιριάζουν στα επιλεγμένα φίλτρα περιόδου/τμημάτων."
            )
            return
        }

        viewModelScope.launch {
            _reportBundleState.value = ReportBundleState.Generating
            try {
                val snapshot = HotelAnalyticsSnapshot.fromReviews(matchingReviews)
                val periodDesc = buildPeriodDescription(config, nowMillis)

                val aiSummary: String? = if (config.includeAiExecutiveSummary) {
                    aiService.generateExecutiveSummaryForReport(
                        hotelName = currentSuccess.profile.hotelName,
                        periodLabel = periodDesc,
                        snapshot = snapshot,
                        reviews = matchingReviews
                    ).getOrElse { err ->
                        // Surface non-fatal warning via snackbar while still generating accurate PDF/Excel stats
                        _snackbarMessage.value = "Σημείωση AI: ${err.localizedMessage ?: "Η σύνοψη παραλείφθηκε"}"
                        null
                    }
                } else {
                    null
                }

                val metadata = ReportMetadata(
                    hotelName = currentSuccess.profile.hotelName,
                    hotelLocation = currentSuccess.profile.hotelLocation,
                    periodLabel = periodDesc,
                    generatedAtMillis = nowMillis,
                    executiveSummary = aiSummary
                )

                val bundle = withContext(Dispatchers.IO) {
                    val reportsDir = File(appContext.cacheDir, "reports").apply { mkdirs() }
                    val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowMillis))
                    val pdfFile = File(reportsDir, "HotelPulse_Report_$dateStamp.pdf")
                    val xlsxFile = File(reportsDir, "HotelPulse_Report_$dateStamp.xlsx")

                    val generatedPdf = PdfReportGenerator.generatePdfAndPreview(
                        context = appContext,
                        outputFile = pdfFile,
                        metadata = metadata,
                        snapshot = snapshot,
                        reviews = matchingReviews,
                        previewScale = 2
                    )

                    val excelPreview = ExcelReportGenerator.generateExcelWorkbook(
                        outputFile = xlsxFile,
                        metadata = metadata,
                        snapshot = snapshot,
                        reviews = matchingReviews
                    )

                    ReportBundleState.Ready(
                        pdfFile = generatedPdf.pdfFile,
                        excelFile = xlsxFile,
                        pdfPageBitmaps = generatedPdf.previewBitmaps,
                        excelSheets = excelPreview.sheets,
                        snapshot = snapshot,
                        filteredReviewsCount = matchingReviews.size,
                        periodDescription = periodDesc,
                        executiveSummary = aiSummary
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

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        fun filterReviewsForReport(
            reviews: List<HotelReview>,
            config: ReportFilterConfig,
            nowMillis: Long
        ): List<HotelReview> {
            val startBound: Long? = when (config.period) {
                ReportPeriod.LAST_7_DAYS -> nowMillis - 7L * DAY_MS
                ReportPeriod.LAST_30_DAYS -> nowMillis - 30L * DAY_MS
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
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("el", "GR"))
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
