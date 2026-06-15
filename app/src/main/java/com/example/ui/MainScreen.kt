package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.ScanResponse
import com.example.data.ThreatEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val scanHistory by viewModel.scanHistory.collectAsStateWithLifecycle()
    val manualInput by viewModel.manualInput.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val clipboardText by viewModel.clipboardText.collectAsState()
    val totalScanned by viewModel.scanCount.collectAsStateWithLifecycle()
    val threatsNeutralized by viewModel.threatCount.collectAsStateWithLifecycle()

    var showClearHistoryDialog by remember { mutableStateOf(false) }



    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Guardian AI",
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "REAL-TIME PROTECTION",
                            fontWeight = FontWeight.Bold,
                            color = CyberPrimary,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(34.dp)
                            .background(CyberPrimary.copy(alpha = 0.12f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Shield Logo",
                            tint = CyberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    if (scanHistory.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearHistoryDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All Logs",
                                tint = TextSecondary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp, start = 8.dp)
                            .size(34.dp)
                            .background(Color(0xFFE2E8F0), shape = CircleShape)
                            .border(1.dp, Color(0xFFCBD5E1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            )
        },
        containerColor = DarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Radar Telemetry Status Card
            item {
                TelemetryBoard(
                    totalScanned = totalScanned,
                    threatsNeutralized = threatsNeutralized
                )
            }

            // 2. Clipboard Quick Scan Card
            if (clipboardText.isNotBlank()) {
                item {
                    ClipboardQuickScanCard(
                        clipboardText = clipboardText,
                        onScanClick = { viewModel.scanClipboardText() }
                    )
                }
            }

            // 3. Active Real-time Manual Scanner
            item {
                ManualScannerPanel(
                    selectedType = selectedType,
                    manualInput = manualInput,
                    scanState = scanState,
                    onTypeChange = { viewModel.changeSelectedType(it) },
                    onInputChange = { viewModel.updateManualInput(it) },
                    onScanTrigger = { viewModel.runManualScan() },
                    onClearFeedback = { viewModel.clearFeedback() }
                )
            }

            // 4. Background Scenario Simulator Console
            item {
                ScamSimulatorConsole(
                    ontrigger = { viewModel.triggerSimulatedThreat(it) }
                )
            }

            // 5. Section Header for threats history List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAFETY INSPECTION LOGS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${scanHistory.size} Scan(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Empty state helper vs Scan History List
            if (scanHistory.isEmpty()) {
                item {
                    EmptyHistoryPlaceholder()
                }
            } else {
                items(scanHistory, key = { it.id }) { item ->
                    HistoryLogItem(
                        item = item,
                        onDeleteClick = { viewModel.deleteItem(item.id) }
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Purge Security Logs", color = Color.White) },
            text = { Text("Are you sure you want to permanently delete all localized phishing and threat scanning histories?", color = TextSecondary) },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistories()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RiskDanger)
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            containerColor = DarkSurface
        )
    }
}

/**
 * Animated Shield Radar Dashboard and Quick Analytics Counters
 */
@Composable
fun TelemetryBoard(
    totalScanned: Int,
    threatsNeutralized: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_board"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant Centered Pulse Shield Container
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFFF0FDFA), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Secondary pulse ring
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFCCFBF1).copy(alpha = 0.6f), shape = CircleShape)
                )
                // Solid main shield badge layout
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CyberPrimary, CyberSecondary)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield Guard Active",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (threatsNeutralized > 0) "System Guarded" else "System Secured",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = "Monitoring 3 active channels (SMS, Email, URL)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Lower Active Status Badge & Counters Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Flashing live indicator green dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Total Scans: $totalScanned  •  Blocked: $threatsNeutralized",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(Color.White, shape = RoundedCornerShape(99.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberPrimary
                    )
                }
            }
        }
    }
}

/**
 * Clipboard auto-detection dynamic action card
 */
@Composable
fun ClipboardQuickScanCard(
    clipboardText: String,
    onScanClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clipboard_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Clipboard Detected",
                        tint = CyberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "COPIED MESSAGE DETECTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"$clipboardText\"",
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = FontFamily.SansSerif,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                fontStyle = FontStyle.Normal
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onScanClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberPrimary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .height(36.dp)
                    .testTag("scan_clipboard_button")
            ) {
                Text(
                    text = "Scan Copied Text",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Real-time active Phishing analysis sandbox area
 */
@Composable
fun ManualScannerPanel(
    selectedType: String,
    manualInput: String,
    scanState: ScanUiState,
    onTypeChange: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onScanTrigger: () -> Unit,
    onClearFeedback: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_scanner_panel"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "SANDBOX DEEP ANALYZER",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextSecondary,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Selector Tabs Row (Protection Modules Inspired layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SMS", "EMAIL", "URL").forEach { type ->
                    val isSelected = selectedType == type
                    val borderAccent = if (isSelected) CyberPrimary else Color(0xFFE2E8F0)
                    val bgAccent = if (isSelected) Color(0xFFF0FDFA) else Color.White
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(bgAccent, shape = RoundedCornerShape(20.dp))
                            .border(if (isSelected) 2.dp else 1.dp, borderAccent, RoundedCornerShape(20.dp))
                            .clickable { onTypeChange(type) }
                            .padding(vertical = 12.dp)
                            .testTag("tab_$type"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = type,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) CyberPrimary else TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(if (isSelected) Color(0xFF10B981) else Color(0xFF94A3B8), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSelected) "ACTIVE" else "READY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fill-out Text field
            OutlinedTextField(
                value = manualInput,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = when (selectedType) {
                            "SMS" -> "Paste suspicious SMS content, urgent alerts, prize pins..."
                            "EMAIL" -> "Paste suspicious email greeting, urgent support messages, fake invoices..."
                            else -> "Paste or type suspicious web url links to inspect..."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .testTag("manual_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPrimary,
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 5,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (manualInput.isNotBlank()) {
                    TextButton(
                        onClick = { onInputChange("") },
                        modifier = Modifier.testTag("clear_input_button")
                    ) {
                        Text(
                            text = "Reset Text", 
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = onScanTrigger,
                    enabled = scanState !is ScanUiState.Scanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("scan_button")
                ) {
                    if (scanState is ScanUiState.Scanning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Scan",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Inspection Scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Stateful Live Diagnostics Feedback results
            AnimatedVisibility(
                visible = scanState !is ScanUiState.Idle,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (scanState) {
                        is ScanUiState.Scanning -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    color = CyberPrimary,
                                    trackColor = Color(0xFFE2E8F0),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Evaluating signals via Gemini AI Phishing engine...",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is ScanUiState.Success -> {
                            ScanFeedbackWidget(
                                result = scanState.result,
                                onDismiss = onClearFeedback
                            )
                        }
                        is ScanUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(RiskDanger.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp))
                                    .border(1.dp, RiskDanger.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Diagnostic Failure",
                                        tint = RiskDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Scan Failed",
                                        fontWeight = FontWeight.Bold,
                                        color = RiskDanger,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = scanState.message,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                TextButton(onClick = onClearFeedback, modifier = Modifier.align(Alignment.End)) {
                                    Text("Dismiss", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

/**
 * Custom visual output block displaying threats rating and explanations
 */
@Composable
fun ScanFeedbackWidget(
    result: ScanResponse,
    onDismiss: () -> Unit
) {
    val levelColor = when (result.riskLevel) {
        "DANGEROUS" -> RiskDanger
        "SUSPICIOUS" -> RiskWarning
        else -> RiskSafe
    }

    val boxBg = when (result.riskLevel) {
        "DANGEROUS" -> Color(0xFFFEE2E2) // Soft warm red bg
        "SUSPICIOUS" -> Color(0xFFFEF3C7) // Soft amber alert bg
        else -> Color(0xFFF0FDFA) // Soft teal/mint bg
    }

    val textOverBgColor = when (result.riskLevel) {
        "DANGEROUS" -> Color(0xFF991B1B) // Dark crimson for red text
        "SUSPICIOUS" -> Color(0xFF92400E) // Dark brown-amber for yellow text
        else -> Color(0xFF115E59) // Dark teal for green text
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(boxBg, shape = RoundedCornerShape(20.dp))
            .border(1.dp, levelColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(18.dp)
            .testTag("scan_feedback_widget")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(levelColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${result.riskLevel} DETECTED",
                    fontWeight = FontWeight.ExtraBold,
                    color = textOverBgColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = "Score: ${result.riskScore}%",
                fontWeight = FontWeight.Bold,
                color = textOverBgColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Verdict Analysis:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = result.verdict,
            fontSize = 13.sp,
            color = TextPrimary.copy(alpha = 0.85f),
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Automated Protective Action Plan:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = result.recommendedAction,
            fontSize = 13.sp,
            color = textOverBgColor,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.End)
                .testTag("close_feedback_button")
        ) {
            Text("Clear Results", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Sandboxing console allowing the simulation of background threat detections
 */
@Composable
fun ScamSimulatorConsole(
    ontrigger: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("simulator_console"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Simulations Mode",
                    tint = CyberPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BACKGROUND INTEGRATION SIMULATOR",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Verify real-time system interception. Tap a mock threat configuration to inject a virtual SMS payload. PhishGuard will evaluate it in the background and trigger dynamic push alarms.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable simulated targets inside a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { ontrigger("wellsfargo") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inject_wells_fargo_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bank Spoof", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("WellsFargo", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }

                Button(
                    onClick = { ontrigger("usps") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inject_usps_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Package Hold", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("USPS Link", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }

                Button(
                    onClick = { ontrigger("refund") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inject_irs_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("IRS Jackpot", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Tax Scraping", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Render standard empty state
 */
@Composable
fun EmptyHistoryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, shape = RoundedCornerShape(32.dp))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(32.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "All Clear",
            tint = CyberPrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Vault is Secure",
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "No threats intercepted or logged. Inbound notifications and clipboard caches are secure.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

/**
 * Individual Interception History Item with detailed dropdown info
 */
@Composable
fun HistoryLogItem(
    item: ThreatEntity,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (item.riskLevel) {
        "DANGEROUS" -> RiskDanger
        "SUSPICIOUS" -> RiskWarning
        else -> RiskSafe
    }

    val itemBg = when (item.riskLevel) {
        "DANGEROUS" -> Color(0xFFFEF2F2) // Soft warm red alert card
        "SUSPICIOUS" -> Color(0xFFFFFBEB) // Soft warm yellow alert card
        else -> Color(0xFFF0FDFA) // Soft teal/mint clean card
    }

    val iconBg = when (item.riskLevel) {
        "DANGEROUS" -> Color(0xFFFEE2E2)
        "SUSPICIOUS" -> Color(0xFFFEF3C7)
        else -> Color(0xFFCCFBF1)
    }

    val typeIcon = when (item.type) {
        "SMS" -> Icons.Default.Email
        "EMAIL" -> Icons.Default.Email
        else -> Icons.Default.Search
    }

    val formatTime = remember(item.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss - MM/dd", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("history_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = itemBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, levelColor.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Icon with dynamic hazard colors
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBg, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = "Scan Category",
                        tint = levelColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Mid Text info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.sender,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Scanned $formatTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Right Threat Level Badge Group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(levelColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${item.riskLevel} (${item.riskScore}%)",
                            fontSize = 11.sp,
                            color = levelColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Quick compressed preview
            if (!expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Expanded audit information
            if (expanded) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = levelColor.copy(alpha = 0.15f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "RAW SCANNED PAYLOAD:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ANALYSIS VERDICT:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.analysisVerdict,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SAFETY INSTRUCTION:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.recommendedAction,
                    style = MaterialTheme.typography.bodySmall,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Delete individual log button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("delete_item_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Discard Record",
                        tint = RiskDanger.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicThemeBorderColor(): Color {
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { phase = (phase + 0.05f) % 1f }
        }
    }
    return CyberPrimary.copy(alpha = 0.12f)
}
