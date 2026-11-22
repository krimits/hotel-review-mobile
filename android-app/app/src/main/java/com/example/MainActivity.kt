package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.HotelAiService
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.HotelRepository
import com.example.firebase.configureAppCheck
import com.example.model.HotelReview
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportsStudioScreen
import com.example.ui.screens.ReviewsScreen
import com.example.ui.theme.HotelPulseTheme
import com.example.viewmodel.HotelPulseViewModel
import com.example.viewmodel.ReviewsUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

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

@Composable
fun HotelPulseRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedHotelPulseApp(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedHotelPulseApp(currentUserId: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val credentialManagerLazy = remember(context) { lazy { CredentialManager.create(context) } }

    val viewModel: HotelPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HotelPulseViewModel(
                    repository = HotelRepository(db),
                    aiService = HotelAiService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listFilter by viewModel.listFilter.collectAsStateWithLifecycle()
    val filteredReviews by viewModel.filteredReviewsForList.collectAsStateWithLifecycle()
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
    var selectedReviewFromDashboard by remember { mutableStateOf<HotelReview?>(null) }

    if (currentTab != MainDestination.DASHBOARD) {
        BackHandler {
            currentTab = MainDestination.DASHBOARD
        }
    }

    var useNavigationRail by remember { mutableStateOf(false) }

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
                                label = { Text(stringResource(dest.labelRes)) },
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

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                                        reviews = state.reviews,
                                        snapshot = state.overallSnapshot,
                                        isBusy = isBusy,
                                        onOpenExportStudio = { currentTab = MainDestination.REPORTS },
                                        onOpenAddReview = {
                                            openAddReviewSheet = true
                                            currentTab = MainDestination.REVIEWS
                                        },
                                        onSeedDemoReviews = { viewModel.seedDemoReviews() },
                                        onSelectDepartment = { dept ->
                                            viewModel.updateDepartmentFilter(dept)
                                            currentTab = MainDestination.REVIEWS
                                        },
                                        onSelectReview = { rev ->
                                            selectedReviewFromDashboard = rev
                                            currentTab = MainDestination.REVIEWS
                                        },
                                        onSaveHotelProfile = { name, loc ->
                                            viewModel.saveHotelSettings(name, loc)
                                        },
                                        onSignOut = {
                                            signOutUser(credentialManagerLazy.value, scope)
                                        }
                                    )
                                }

                                MainDestination.REVIEWS -> {
                                    ReviewsScreen(
                                        reviews = filteredReviews,
                                        filterState = listFilter,
                                        isBusy = isBusy,
                                        initialSelectedReview = selectedReviewFromDashboard,
                                        showAddSheetInitially = openAddReviewSheet,
                                        onConsumeAddSheetTrigger = { openAddReviewSheet = false },
                                        onClearInitialSelectedReview = { selectedReviewFromDashboard = null },
                                        onSearchChange = viewModel::updateSearchQuery,
                                        onSentimentFilterChange = viewModel::updateSentimentFilter,
                                        onDepartmentFilterChange = viewModel::updateDepartmentFilter,
                                        onToggleUnresolved = viewModel::toggleUnresolvedOnly,
                                        onCreateReviewWithAi = viewModel::analyzeAndCreateReview,
                                        onUpdateReviewReplyAndResolution = viewModel::toggleResolvedAndSaveReply,
                                        onRegenerateReply = viewModel::regenerateReplyForReview,
                                        onDeleteReview = viewModel::deleteReview,
                                        onReplyCopied = {
                                            viewModel.postMessage(context.getString(R.string.reply_copied_toast))
                                        }
                                    )
                                }

                                MainDestination.REPORTS -> {
                                    ReportsStudioScreen(
                                        profile = state.profile,
                                        config = reportConfig,
                                        matchingCount = reportMatching.size,
                                        bundleState = reportBundle,
                                        onSelectPeriod = viewModel::updateReportPeriod,
                                        onToggleSentiment = viewModel::toggleReportSentiment,
                                        onToggleDepartment = viewModel::toggleReportDepartment,
                                        onClearDepartments = viewModel::clearReportDepartmentFilter,
                                        onSetOnlyUnresolved = viewModel::setReportOnlyUnresolved,
                                        onSetIncludeAiSummary = viewModel::setReportIncludeAiSummary,
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
