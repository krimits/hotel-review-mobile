package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.export.ExcelSheetPreview
import com.example.export.ReportShareHelper
import com.example.model.HotelDepartment
import com.example.model.HotelProfile
import com.example.model.ReportFilterConfig
import com.example.model.ReportPeriod
import com.example.model.SentimentType
import com.example.viewmodel.ReportBundleState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsStudioScreen(
    profile: HotelProfile,
    selectedPropertyId: String,
    config: ReportFilterConfig,
    matchingCount: Int,
    bundleState: ReportBundleState,
    onSelectPeriod: (ReportPeriod, Long?, Long?) -> Unit,
    onToggleSentiment: (SentimentType) -> Unit,
    onToggleDepartment: (HotelDepartment) -> Unit,
    onClearDepartments: () -> Unit,
    onSetOnlyUnresolved: (Boolean) -> Unit,
    onSetIncludeAiSummary: (Boolean) -> Unit,
    onSetIncludePatternsAndActions: (Boolean) -> Unit,
    onGeneratePreview: () -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeProperty = remember(selectedPropertyId) { com.example.model.HotelProperty.byId(selectedPropertyId) }
    val activeScopeLabel = activeProperty?.displayLabel
        ?: "${profile.hotelName} · Όλα τα καταλύματα (${com.example.model.HotelProperty.CATALOG.size})"

    var showDateRangeDialog by remember { mutableStateOf(false) }
    var selectedPreviewTab by remember { mutableIntStateOf(0) } // 0 = PDF, 1 = Excel
    var selectedExcelSheetIdx by remember { mutableIntStateOf(0) }
    var zoomedPdf by remember { mutableStateOf(false) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ReportShareHelper.MIME_PDF)
    ) { targetUri ->
        val ready = bundleState as? ReportBundleState.Ready ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            scope.launch {
                ReportShareHelper.copyFileToSafUri(context, ready.pdfFile, targetUri).fold(
                    onSuccess = { onPostMessage(context.getString(R.string.report_saved_success)) },
                    onFailure = { e -> onPostMessage(e.localizedMessage ?: "Αποτυχία αποθήκευσης PDF.") }
                )
            }
        }
    }

    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ReportShareHelper.MIME_XLSX)
    ) { targetUri ->
        val ready = bundleState as? ReportBundleState.Ready ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            scope.launch {
                ReportShareHelper.copyFileToSafUri(context, ready.excelFile, targetUri).fold(
                    onSuccess = { onPostMessage(context.getString(R.string.report_saved_success)) },
                    onFailure = { e -> onPostMessage(e.localizedMessage ?: "Αποτυχία αποθήκευσης Excel.") }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.reports_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Εύρος εξαγωγής: $activeScopeLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.reports_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Report Configuration & Filtering Card
            item {
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
                            text = "1. Περίοδος & Φίλτρα Αναφοράς",
                            style = MaterialTheme.typography.titleMedium
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = config.period == ReportPeriod.LAST_7_DAYS,
                                onClick = { onSelectPeriod(ReportPeriod.LAST_7_DAYS, null, null) },
                                label = { Text(stringResource(R.string.period_7_days)) }
                            )
                            FilterChip(
                                selected = config.period == ReportPeriod.LAST_14_DAYS,
                                onClick = { onSelectPeriod(ReportPeriod.LAST_14_DAYS, null, null) },
                                label = { Text(stringResource(R.string.period_14_days)) }
                            )
                            FilterChip(
                                selected = config.period == ReportPeriod.LAST_30_DAYS,
                                onClick = { onSelectPeriod(ReportPeriod.LAST_30_DAYS, null, null) },
                                label = { Text(stringResource(R.string.period_30_days)) }
                            )
                            FilterChip(
                                selected = config.period == ReportPeriod.LAST_60_DAYS,
                                onClick = { onSelectPeriod(ReportPeriod.LAST_60_DAYS, null, null) },
                                label = { Text(stringResource(R.string.period_60_days)) }
                            )
                            FilterChip(
                                selected = config.period == ReportPeriod.LAST_90_DAYS,
                                onClick = { onSelectPeriod(ReportPeriod.LAST_90_DAYS, null, null) },
                                label = { Text(stringResource(R.string.period_90_days)) }
                            )
                            FilterChip(
                                selected = config.period == ReportPeriod.ALL_TIME,
                                onClick = { onSelectPeriod(ReportPeriod.ALL_TIME, null, null) },
                                label = { Text(stringResource(R.string.period_all_time)) }
                            )
                            FilterChip(
                                selected = config.period == ReportPeriod.CUSTOM,
                                onClick = { showDateRangeDialog = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(stringResource(R.string.period_custom)) }
                            )
                        }

                        HorizontalDivider()

                        Text(
                            text = "Συναίσθημα Κριτικών",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SentimentType.entries.forEach { sent ->
                                FilterChip(
                                    selected = sent in config.selectedSentiments,
                                    onClick = { onToggleSentiment(sent) },
                                    label = { Text(sent.labelEl) }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Τμήματα Ξενοδοχείου (${if (config.selectedDepartments.isEmpty()) "Όλα" else "${config.selectedDepartments.size} επιλεγμένα"})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (config.selectedDepartments.isNotEmpty()) {
                                TextButton(onClick = onClearDepartments) {
                                    Text("Επαναφορά όλων")
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HotelDepartment.entries.forEach { dept ->
                                FilterChip(
                                    selected = dept in config.selectedDepartments,
                                    onClick = { onToggleDepartment(dept) },
                                    label = { Text(dept.shortLabelEl) }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Μόνο εκκρεμή ζητήματα προς τακτοποίηση",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = config.onlyUnresolved,
                                onCheckedChange = onSetOnlyUnresolved
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.include_patterns_actions_label),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = config.includePatternsAndActions,
                                onCheckedChange = onSetIncludePatternsAndActions,
                                modifier = Modifier.testTag("toggle_patterns_actions_switch")
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.include_ai_summary_label),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = config.includeAiExecutiveSummary,
                                onCheckedChange = onSetIncludeAiSummary,
                                modifier = Modifier.testTag("toggle_ai_summary_switch")
                            )
                        }

                        Button(
                            onClick = onGeneratePreview,
                            enabled = bundleState !is ReportBundleState.Generating && matchingCount > 0,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("generate_report_preview_button")
                        ) {
                            if (bundleState is ReportBundleState.Generating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.generating_reports_status))
                            } else {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${stringResource(R.string.generate_preview_button)} ($matchingCount κριτικές)")
                            }
                        }

                        if (matchingCount == 0) {
                            Text(
                                text = "Δεν βρέθηκαν κριτικές για τα τρέχοντα φίλτρα. Δοκιμάστε «Όλες οι Περίοδοι» ή «Όλα τα καταλύματα».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 2. Error state if report generation failed
            if (bundleState is ReportBundleState.Error) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bundleState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // 3. Ready In-App Preview (PDF Pages + Multi-Sheet Excel) & Share/Save Actions
            if (bundleState is ReportBundleState.Ready) {
                item {
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
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "2. Προεπισκόπηση & Διαμοιρασμός με την Ομάδα",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${bundleState.periodDescription} • ${bundleState.filteredReviewsCount} κριτικές • Μ.Ο. ${bundleState.snapshot.formattedAverageRating}/10",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (bundleState.patternsCount > 0 || bundleState.recommendationsCount > 0) {
                                        Text(
                                            text = "Περιλαμβάνει ${bundleState.patternsCount} μοτίβα & ${bundleState.recommendationsCount} προτάσεις ενεργειών",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (selectedPreviewTab == 0) {
                                    IconButton(onClick = { zoomedPdf = !zoomedPdf }) {
                                        Icon(
                                            imageVector = if (zoomedPdf) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                                            contentDescription = if (zoomedPdf) "Σμίκρυνση" else "Μεγέθυνση"
                                        )
                                    }
                                }
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = selectedPreviewTab == 0,
                                    onClick = { selectedPreviewTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Filled.PictureAsPdf,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("tab_preview_pdf")
                                ) {
                                    Text(stringResource(R.string.preview_tab_pdf))
                                }
                                SegmentedButton(
                                    selected = selectedPreviewTab == 1,
                                    onClick = { selectedPreviewTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Filled.TableChart,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("tab_preview_excel")
                                ) {
                                    Text(stringResource(R.string.preview_tab_excel))
                                }
                            }

                            if (selectedPreviewTab == 0) {
                                PdfPagesPreviewBox(
                                    pages = bundleState.pdfPageBitmaps,
                                    zoomed = zoomedPdf
                                )
                            } else {
                                ExcelSheetsPreviewBox(
                                    sheets = bundleState.excelSheets,
                                    selectedSheetIndex = selectedExcelSheetIdx.coerceIn(
                                        0,
                                        (bundleState.excelSheets.size - 1).coerceAtLeast(0)
                                    ),
                                    onSelectSheet = { selectedExcelSheetIdx = it }
                                )
                            }

                            HorizontalDivider()

                            // Share & Save Buttons (PDF + Excel)
                            val reportTargetName = activeProperty?.name ?: profile.hotelName
                            val subject = "Αναφορά Απόδοσης $reportTargetName (${bundleState.periodDescription})"

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        ReportShareHelper.shareReportFile(
                                            context = context,
                                            file = bundleState.pdfFile,
                                            mimeType = ReportShareHelper.MIME_PDF,
                                            subject = subject
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("share_pdf_button")
                                ) {
                                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.share_pdf_button),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        ReportShareHelper.shareReportFile(
                                            context = context,
                                            file = bundleState.excelFile,
                                            mimeType = ReportShareHelper.MIME_XLSX,
                                            subject = subject
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("share_excel_button")
                                ) {
                                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.share_excel_button),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { savePdfLauncher.launch(bundleState.pdfFile.name) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_pdf_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.save_pdf_device))
                                }

                                FilledTonalButton(
                                    onClick = { saveExcelLauncher.launch(bundleState.excelFile.name) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_excel_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.save_excel_device))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showDateRangeDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = config.customStartMillis,
            initialSelectedEndDateMillis = config.customEndMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelectPeriod(
                            ReportPeriod.CUSTOM,
                            pickerState.selectedStartDateMillis,
                            pickerState.selectedEndDateMillis
                        )
                        showDateRangeDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
            )
        }
    }
}

@Composable
private fun PdfPagesPreviewBox(
    pages: List<android.graphics.Bitmap>,
    zoomed: Boolean
) {
    if (pages.isEmpty()) return
    val imageBitmaps = remember(pages) { pages.map { it.asImageBitmap() } }
    var currentPageIndex by remember(pages) { mutableIntStateOf(0) }
    val safeIndex = currentPageIndex.coerceIn(0, imageBitmaps.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { if (safeIndex > 0) currentPageIndex = safeIndex - 1 },
                enabled = safeIndex > 0,
                modifier = Modifier.testTag("pdf_prev_page_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Προηγούμενη σελίδα"
                )
            }
            Text(
                text = "Σελίδα ${safeIndex + 1} από ${imageBitmaps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = { if (safeIndex < imageBitmaps.lastIndex) currentPageIndex = safeIndex + 1 },
                enabled = safeIndex < imageBitmaps.lastIndex,
                modifier = Modifier.testTag("pdf_next_page_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Επόμενη σελίδα"
                )
            }
        }

        Box(
            modifier = if (zoomed) {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            Image(
                bitmap = imageBitmaps[safeIndex],
                contentDescription = "Προεπισκόπηση σελίδας PDF ${safeIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = (if (zoomed) Modifier.width(680.dp) else Modifier.fillMaxWidth())
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun ExcelSheetsPreviewBox(
    sheets: List<ExcelSheetPreview>,
    selectedSheetIndex: Int,
    onSelectSheet: (Int) -> Unit
) {
    if (sheets.isEmpty()) return
    val currentSheet = sheets[selectedSheetIndex]
    val maxPreviewRows = 50
    val visibleRows = remember(currentSheet) { currentSheet.rows.take(maxPreviewRows) }
    val hiddenRowCount = (currentSheet.rows.size - visibleRows.size).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            sheets.forEachIndexed { idx, sheet ->
                FilterChip(
                    selected = idx == selectedSheetIndex,
                    onClick = { onSelectSheet(idx) },
                    label = { Text("${idx + 1}. ${sheet.sheetName}") }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                visibleRows.forEach { row ->
                    if (row.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Row(
                            modifier = Modifier.border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            row.forEachIndexed { cIdx, cell ->
                                val colWidthDp = ((currentSheet.columnWidthsChars.getOrElse(cIdx) { 18 }) * 6.5f)
                                    .coerceIn(96f, 300f).dp
                                val bgColor = when (cell.styleIndex) {
                                    1 -> Color(0xFF0F4C5C)
                                    2 -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                                val textColor = when (cell.styleIndex) {
                                    1 -> Color.White
                                    2 -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                val isBold = cell.styleIndex in listOf(1, 2, 3, 4)

                                Box(
                                    modifier = Modifier
                                        .width(colWidthDp)
                                        .background(bgColor)
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cell.displayValue,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = textColor,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (hiddenRowCount > 0) {
                    Text(
                        text = "… και $hiddenRowCount επιπλέον γραμμές (περιλαμβάνονται όλες στο αρχείο .xlsx)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }
        }
    }
}
