package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.HotelAiService
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.HotelRepository
import com.example.data.OperationalStateStore
import com.example.firebase.configureAppCheck
import com.example.model.AlertRulesConfig
import com.example.model.CriticalAlertKind
import com.example.model.HotelProperty
import com.example.model.OperationalAlert
import com.example.ui.screens.ActionsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PatternsScreen
import com.example.ui.screens.ReportsStudioScreen
import com.example.ui.screens.ReviewsScreen
import com.example.ui.theme.HotelPulseTheme
import com.example.ui.theme.SentimentNegative
import com.example.ui.theme.SentimentNeutral
import com.example.viewmodel.HotelPulseViewModel
import com.example.viewmodel.ReviewsUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DASHBOARD(
        R.string.nav_dashboard,
        Icons.Filled.Dashboard,
        Icons.Outlined.Dashboard,
        "nav_dashboard"
    ),
    REVIEWS(
        R.string.nav_reviews,
        Icons.Filled.RateReview,
        Icons.Outlined.RateReview,
        "nav_reviews"
    ),
    ACTIONS(
        R.string.nav_actions,
        Icons.Filled.PlaylistAddCheck,
        Icons.Outlined.PlaylistAddCheck,
        "nav_actions"
    ),
    PATTERNS(
        R.string.nav_patterns,
        Icons.Filled.Insights,
        Icons.Outlined.Insights,
        "nav_patterns"
    ),
    REPORTS(
        R.string.nav_reports,
        Icons.Filled.PictureAsPdf,
        Icons.Outlined.PictureAsPdf,
        "nav_reports"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            HotelPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HotelPulseRoot()
                }
            }
        }
    }
}

private data class FirebaseHandles(
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore
)

@Composable
fun HotelPulseRoot() {
    val context = LocalContext.current
    var handles by remember { mutableStateOf<FirebaseHandles?>(null) }
    var isDemoMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Complete the initial surface transaction before touching Firebase SDK singletons
        withFrameNanos { }
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        handles = withContext(Dispatchers.IO) {
            FirebaseHandles(
                auth = Firebase.auth,
                firestore = FirebaseFirestore.getInstance(databaseId)
            )
        }
    }

    if (isDemoMode) {
        key("demo_session") {
            HotelPulseWorkspace(
                currentUserId = "demo",
                firestore = null,
                isDemoMode = true,
                onExitDemoMode = { isDemoMode = false }
            )
        }
        return
    }

    val readyHandles = handles
    if (readyHandles == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val currentUser by readyHandles.auth
        .authStateFlow()
        .collectAsStateWithLifecycle(initialValue = readyHandles.auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = {},
            onEnterDemoMode = { isDemoMode = true }
        )
    } else {
        key(user.uid) {
            HotelPulseWorkspace(
                currentUserId = user.uid,
                firestore = readyHandles.firestore,
                isDemoMode = false,
                onExitDemoMode = {}
            )
        }
    }
}

@Composable
private fun HotelPulseWorkspace(
    currentUserId: String,
    firestore: FirebaseFirestore?,
    isDemoMode: Boolean,
    onExitDemoMode: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val credentialManagerLazy = remember(context) { lazy { CredentialManager.create(context) } }

    val viewModel: HotelPulseViewModel = viewModel(
        key = if (isDemoMode) "demo_vm" else "cloud_$currentUserId",
        factory = viewModelFactory {
            initializer {
                HotelPulseViewModel(
                    repository = if (isDemoMode || firestore == null) null else HotelRepository(firestore),
                    aiService = if (isDemoMode) null else HotelAiService(),
                    operationalStore = OperationalStateStore(
                        context = context.applicationContext,
                        scopeKey = if (isDemoMode) "demo" else currentUserId
                    ),
                    currentUserId = currentUserId,
                    isDemoMode = isDemoMode
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPropertyId by viewModel.selectedPropertyId.collectAsStateWithLifecycle()
    val opsState by viewModel.operationalState.collectAsStateWithLifecycle()
    val propertyReviews by viewModel.propertyScopedReviews.collectAsStateWithLifecycle()
    val propertySnapshot by viewModel.propertySnapshot.collectAsStateWithLifecycle()
    val listFilter by viewModel.listFilter.collectAsStateWithLifecycle()
    val filteredReviews by viewModel.filteredReviewsForList.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendedActions.collectAsStateWithLifecycle()
    val dailyPlan by viewModel.dailyActionPlan.collectAsStateWithLifecycle()
    val assignedTasks by viewModel.scopedAssignedTasks.collectAsStateWithLifecycle()
    val operationalAlerts by viewModel.operationalAlerts.collectAsStateWithLifecycle()
    val topicPatterns by viewModel.topicPatterns.collectAsStateWithLifecycle()
    val patternWindowDays by viewModel.patternWindowDays.collectAsStateWithLifecycle()
    val patternSourceFilter by viewModel.patternSourceFilter.collectAsStateWithLifecycle()
    val reportConfig by viewModel.reportConfig.collectAsStateWithLifecycle()
    val reportMatching by viewModel.reportMatchingReviews.collectAsStateWithLifecycle()
    val reportBundle by viewModel.reportBundleState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusyAction.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainDestination.DASHBOARD) }
    var openAddReviewSheet by remember { mutableStateOf(false) }
    var selectedReviewIdToOpen by remember { mutableStateOf<String?>(null) }
    var showAlertsSheet by remember { mutableStateOf(false) }
    var actionsInitialSegment by remember { mutableStateOf<Int?>(null) }

    if (currentTab != MainDestination.DASHBOARD) {
        BackHandler {
            currentTab = MainDestination.DASHBOARD
        }
    }

    var useNavigationRail by remember { mutableStateOf(false) }

    val allReviews = (uiState as? ReviewsUiState.Success)?.reviews.orEmpty()
    val countsByProperty = remember(allReviews, opsState.reviewPropertyMap) {
        allReviews.groupingBy { opsState.reviewPropertyMap[it.id] ?: "p1" }.eachCount()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val widthDp = with(density) { size.width.toDp() }
                useNavigationRail = widthDp >= 600.dp
            }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        MainDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MainDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Persistent Multi-Property Selector Bar + Alerts Bell at the top
                    PropertySelectorHeader(
                        selectedPropertyId = selectedPropertyId,
                        totalReviewsCount = allReviews.size,
                        countsByProperty = countsByProperty,
                        activeAlertsCount = operationalAlerts.size,
                        isDemoMode = isDemoMode,
                        onSelectProperty = viewModel::selectProperty,
                        onOpenAlerts = { showAlertsSheet = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (val state = uiState) {
                            is ReviewsUiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            is ReviewsUiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            is ReviewsUiState.Success -> {
                                when (currentTab) {
                                    MainDestination.DASHBOARD -> {
                                        DashboardScreen(
                                            profile = state.profile,
                                            selectedPropertyId = selectedPropertyId,
                                            reviews = propertyReviews,
                                            snapshot = propertySnapshot,
                                            dailyPlan = dailyPlan,
                                            topPatterns = topicPatterns,
                                            alerts = operationalAlerts,
                                            reviewPropertyMap = opsState.reviewPropertyMap,
                                            isDemoMode = isDemoMode,
                                            isBusy = isBusy,
                                            onOpenAlerts = { showAlertsSheet = true },
                                            onOpenExportStudio = { currentTab = MainDestination.REPORTS },
                                            onOpenAddReview = {
                                                openAddReviewSheet = true
                                                currentTab = MainDestination.REVIEWS
                                            },
                                            onOpenActionsTab = { currentTab = MainDestination.ACTIONS },
                                            onOpenPatternsTab = { currentTab = MainDestination.PATTERNS },
                                            onSeedDemoReviews = { viewModel.seedDemoReviews() },
                                            onResetDemoData = { viewModel.resetDemoData() },
                                            onSelectDepartment = { dept ->
                                                viewModel.applyDepartmentFromDashboard(dept)
                                                currentTab = MainDestination.REVIEWS
                                            },
                                            onSelectReview = { rev ->
                                                selectedReviewIdToOpen = rev.id
                                                currentTab = MainDestination.REVIEWS
                                            },
                                            onSaveHotelProfile = { name, loc ->
                                                viewModel.saveHotelSettings(name, loc)
                                            },
                                            onSignOut = {
                                                if (isDemoMode) {
                                                    onExitDemoMode()
                                                } else {
                                                    signOutUser(credentialManagerLazy.value, scope)
                                                }
                                            }
                                        )
                                    }

                                    MainDestination.REVIEWS -> {
                                        ReviewsScreen(
                                            reviews = filteredReviews,
                                            allReviews = state.reviews,
                                            filterState = listFilter,
                                            selectedPropertyId = selectedPropertyId,
                                            reviewPropertyMap = opsState.reviewPropertyMap,
                                            checkedReviewIds = opsState.checkedReviewIds,
                                            assignedTasks = opsState.assignedTasks,
                                            recommendations = recommendations,
                                            isBusy = isBusy,
                                            initialSelectedReviewId = selectedReviewIdToOpen,
                                            showAddSheetInitially = openAddReviewSheet,
                                            onConsumeAddSheetTrigger = { openAddReviewSheet = false },
                                            onClearInitialSelectedReview = { selectedReviewIdToOpen = null },
                                            onSearchChange = viewModel::updateSearchQuery,
                                            onSentimentFilterChange = viewModel::updateSentimentFilter,
                                            onDepartmentFilterChange = viewModel::updateDepartmentFilter,
                                            onSourceFilterChange = viewModel::updateSourceFilter,
                                            onTopicFilterChange = viewModel::updateTopicFilter,
                                            onTriageFilterChange = viewModel::updateTriageFilter,
                                            onToggleUnresolved = viewModel::toggleUnresolvedOnly,
                                            onToggleNeedsConfirmation = viewModel::toggleNeedsConfirmationOnly,
                                            onClearAllFilters = viewModel::clearAllReviewFilters,
                                            onConfirmReviewAnalysis = viewModel::confirmReviewAnalysis,
                                            onApproveAndAssignAction = { action ->
                                                viewModel.approveAndAssignAction(action)
                                            },
                                            onStartTask = viewModel::startTask,
                                            onCompleteTask = viewModel::completeTask,
                                            onCreateReviewWithAi = viewModel::analyzeAndCreateReview,
                                            onUpdateReviewReplyAndResolution = { rev, resolved, reply ->
                                                viewModel.toggleResolvedAndSaveReply(rev, resolved, reply)
                                            },
                                            onRegenerateReply = viewModel::regenerateReplyForReview,
                                            onDeleteReview = viewModel::deleteReview,
                                            onReplyCopied = {
                                                viewModel.postMessage(context.getString(R.string.reply_copied_toast))
                                            }
                                        )
                                    }

                                    MainDestination.ACTIONS -> {
                                        ActionsScreen(
                                            recommendations = recommendations,
                                            dailyPlan = dailyPlan,
                                            assignedTasks = assignedTasks,
                                            weights = opsState.weights,
                                            limits = opsState.limits,
                                            alertRules = opsState.alertRules,
                                            initialSegment = actionsInitialSegment,
                                            onConsumeInitialSegment = { actionsInitialSegment = null },
                                            onUpdateWeights = viewModel::updatePriorityWeights,
                                            onUpdateLimits = viewModel::updatePlanLimits,
                                            onConfirmAnalysis = viewModel::confirmReviewAnalysis,
                                            onApproveAndAssign = { action ->
                                                viewModel.approveAndAssignAction(action)
                                            },
                                            onStartTask = viewModel::startTask,
                                            onCompleteTask = viewModel::completeTask,
                                            onAddTaskNote = { taskId, fromDept, toDept, text ->
                                                viewModel.addTaskNote(taskId, fromDept, toDept, text)
                                            },
                                            onOpenReviewById = { reviewId ->
                                                selectedReviewIdToOpen = reviewId
                                                currentTab = MainDestination.REVIEWS
                                            }
                                        )
                                    }

                                    MainDestination.PATTERNS -> {
                                        PatternsScreen(
                                            patterns = topicPatterns,
                                            windowDays = patternWindowDays,
                                            sourceFilter = patternSourceFilter,
                                            onSelectWindowDays = viewModel::updatePatternWindow,
                                            onSelectSourceFilter = viewModel::updatePatternSource,
                                            onOpenReviewById = { reviewId ->
                                                selectedReviewIdToOpen = reviewId
                                                currentTab = MainDestination.REVIEWS
                                            },
                                            onFilterReviewsByTopic = { aspect, source ->
                                                viewModel.applyPatternToReviewsFilter(aspect, source)
                                                currentTab = MainDestination.REVIEWS
                                            },
                                            onOpenReportsStudio = { currentTab = MainDestination.REPORTS }
                                        )
                                    }

                                    MainDestination.REPORTS -> {
                                        ReportsStudioScreen(
                                            profile = state.profile,
                                            selectedPropertyId = selectedPropertyId,
                                            config = reportConfig,
                                            matchingCount = reportMatching.size,
                                            bundleState = reportBundle,
                                            onSelectPeriod = viewModel::updateReportPeriod,
                                            onToggleSentiment = viewModel::toggleReportSentiment,
                                            onToggleDepartment = viewModel::toggleReportDepartment,
                                            onClearDepartments = viewModel::clearReportDepartmentFilter,
                                            onSetOnlyUnresolved = viewModel::setReportOnlyUnresolved,
                                            onSetIncludeAiSummary = viewModel::setReportIncludeAiSummary,
                                            onSetIncludePatternsAndActions = viewModel::setReportIncludePatternsAndActions,
                                            onGeneratePreview = {
                                                viewModel.generateReportPreviewAndFiles(context.applicationContext)
                                            },
                                            onPostMessage = viewModel::postMessage
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAlertsSheet) {
        AlertsCenterSheet(
            alerts = operationalAlerts,
            rules = opsState.alertRules,
            dismissedCount = opsState.dismissedAlertIds.size,
            onDismiss = { showAlertsSheet = false },
            onUpdateRules = viewModel::updateAlertRules,
            onDismissAlert = viewModel::dismissAlert,
            onRestoreDismissed = viewModel::restoreDismissedAlerts,
            onOpenAlertTarget = { alert ->
                showAlertsSheet = false
                if (alert.kind == CriticalAlertKind.OVERDUE_TASK) {
                    actionsInitialSegment = 1
                    currentTab = MainDestination.ACTIONS
                } else if (!alert.reviewId.isNullOrBlank()) {
                    selectedReviewIdToOpen = alert.reviewId
                    currentTab = MainDestination.REVIEWS
                }
            }
        )
    }
}

@Composable
private fun PropertySelectorHeader(
    selectedPropertyId: String,
    totalReviewsCount: Int,
    countsByProperty: Map<String, Int>,
    activeAlertsCount: Int,
    isDemoMode: Boolean,
    onSelectProperty: (String) -> Unit,
    onOpenAlerts: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeProp = remember(selectedPropertyId) { HotelProperty.byId(selectedPropertyId) }
    val activeTitle = activeProp?.displayLabel ?: "Όλα τα καταλύματα (${HotelProperty.CATALOG.size})"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("property_selector_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apartment,
                        contentDescription = "Επιλογή καταλύματος",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = activeTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Όλα τα καταλύματα ($totalReviewsCount κριτ.)",
                                fontWeight = if (selectedPropertyId == HotelProperty.ALL_ID) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            if (selectedPropertyId == HotelProperty.ALL_ID) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            onSelectProperty(HotelProperty.ALL_ID)
                            expanded = false
                        },
                        modifier = Modifier.testTag("property_option_all")
                    )
                    HorizontalDivider()
                    HotelProperty.CATALOG.forEach { prop ->
                        val count = countsByProperty[prop.id] ?: 0
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${prop.displayLabel} ($count)",
                                    fontWeight = if (selectedPropertyId == prop.id) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (selectedPropertyId == prop.id) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                onSelectProperty(prop.id)
                                expanded = false
                            },
                            modifier = Modifier.testTag("property_option_${prop.id}")
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onOpenAlerts,
                    modifier = Modifier.testTag("alerts_bell_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (activeAlertsCount > 0) {
                                Badge(
                                    containerColor = SentimentNegative,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ) {
                                    Text(if (activeAlertsCount > 99) "99+" else "$activeAlertsCount")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (activeAlertsCount > 0) Icons.Filled.NotificationsActive
                            else Icons.Filled.Notifications,
                            contentDescription = "Κέντρο Ειδοποιήσεων ($activeAlertsCount)",
                            tint = if (activeAlertsCount > 0) SentimentNegative
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isDemoMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
private fun AlertsCenterSheet(
    alerts: List<OperationalAlert>,
    rules: AlertRulesConfig,
    dismissedCount: Int,
    onDismiss: () -> Unit,
    onUpdateRules: (Double, Int) -> Unit,
    onDismissAlert: (String) -> Unit,
    onRestoreDismissed: () -> Unit,
    onOpenAlertTarget: (OperationalAlert) -> Unit
) {
    var selectedKind by remember { mutableStateOf<CriticalAlertKind?>(null) }
    val filteredAlerts = remember(alerts, selectedKind) {
        if (selectedKind == null) alerts else alerts.filter { it.kind == selectedKind }
    }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("el-GR")) }

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
            Text(
                text = "Κέντρο Κρίσιμων Ειδοποιήσεων (${alerts.size})",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Ειδοποιήσεις για χαμηλές βαθμολογίες, εκπρόθεσμες εργασίες πέραν του ορίου SLA και επείγοντα περιστατικά χωρίς ανάθεση.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Threshold configuration card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Όριο χαμηλής βαθμολογίας (/10)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3.5, 4.5, 5.5).forEach { threshold ->
                            FilterChip(
                                selected = kotlin.math.abs(rules.lowRatingThreshold - threshold) < 0.05,
                                onClick = { onUpdateRules(threshold, rules.overdueHours) },
                                label = { Text("≤ ${String.format(Locale.US, "%.1f", threshold)}") }
                            )
                        }
                    }

                    Text(
                        text = "Όριο καθυστέρησης ανατεθειμένης εργασίας (SLA)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24, 48, 72).forEach { hrs ->
                            FilterChip(
                                selected = rules.overdueHours == hrs,
                                onClick = { onUpdateRules(rules.lowRatingThreshold, hrs) },
                                label = { Text("${hrs}ω") }
                            )
                        }
                    }
                }
            }

            // Kind filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedKind == null,
                    onClick = { selectedKind = null },
                    label = { Text("Όλες (${alerts.size})") }
                )
                CriticalAlertKind.entries.forEach { kind ->
                    val cnt = alerts.count { it.kind == kind }
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = { selectedKind = if (selectedKind == kind) null else kind },
                        label = { Text("${kind.labelEl} ($cnt)") }
                    )
                }
            }

            if (filteredAlerts.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Δεν υπάρχουν ενεργές ειδοποιήσεις για τα τρέχοντα κριτήρια.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                filteredAlerts.forEach { alert ->
                    val badgeColor = if (alert.severity >= 3) SentimentNegative else SentimentNeutral
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_card_${alert.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = badgeColor.copy(alpha = 0.14f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = alert.kind.labelEl,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = dateFormat.format(Date(alert.timestampMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append(alert.propertyName)
                                    if (!alert.departmentNameEl.isNullOrBlank()) {
                                        append(" • ")
                                        append(alert.departmentNameEl)
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = alert.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { onOpenAlertTarget(alert) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        if (alert.kind == CriticalAlertKind.OVERDUE_TASK) "Άνοιγμα ανάθεσης"
                                        else "Άνοιγμα κριτικής"
                                    )
                                }
                                OutlinedButton(onClick = { onDismissAlert(alert.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Σίγαση")
                                }
                            }
                        }
                    }
                }
            }

            if (dismissedCount > 0) {
                TextButton(
                    onClick = onRestoreDismissed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Επαναφορά σιγασμένων ειδοποιήσεων ($dismissedCount)")
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
