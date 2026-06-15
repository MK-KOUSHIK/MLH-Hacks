package com.example.ui

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.api.ScanResponse
import com.example.data.AppDatabase
import com.example.data.ThreatEntity
import com.example.receiver.SmsReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Scanning : ScanUiState
    data class Success(val result: ScanResponse) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val threatDao = db.threatDao()

    val scanHistory: StateFlow<List<ThreatEntity>> = threatDao.getAllThreatsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val scanCount: StateFlow<Int> = threatDao.getScanCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val threatCount: StateFlow<Int> = threatDao.getThreatCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Manual input fields & active states
    private val _manualInput = MutableStateFlow("")
    val manualInput = _manualInput.asStateFlow()

    private val _selectedType = MutableStateFlow("SMS") // "SMS", "EMAIL", "URL"
    val selectedType = _selectedType.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState = _scanState.asStateFlow()

    // Clipboard monitoring
    private val _clipboardText = MutableStateFlow("")
    val clipboardText = _clipboardText.asStateFlow()



    fun updateManualInput(text: String) {
        _manualInput.value = text
        if (_scanState.value !is ScanUiState.Idle) {
            _scanState.value = ScanUiState.Idle
        }
    }

    fun changeSelectedType(type: String) {
        _selectedType.value = type
        _scanState.value = ScanUiState.Idle
    }

    fun clearFeedback() {
        _scanState.value = ScanUiState.Idle
    }

    /**
     * Executes manual active scan on current input fields
     */
    fun runManualScan() {
        val input = _manualInput.value.trim()
        if (input.isEmpty()) {
            _scanState.value = ScanUiState.Error("Please enter some text, email content, or link to scan.")
            return
        }

        _scanState.value = ScanUiState.Scanning

        viewModelScope.launch {
            try {
                val response = GeminiClient.scanContent(_selectedType.value, input)
                
                // Write manual scan to SQLite history
                val entity = ThreatEntity(
                    type = _selectedType.value,
                    content = input,
                    sender = "Manual Scan",
                    riskLevel = response.riskLevel,
                    riskScore = response.riskScore,
                    analysisVerdict = response.verdict,
                    recommendedAction = response.recommendedAction,
                    timestamp = System.currentTimeMillis()
                )
                threatDao.insertThreat(entity)

                _scanState.value = ScanUiState.Success(response)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.localizedMessage ?: "Unknown diagnostic error occurred.")
            }
        }
    }

    /**
     * Checks if current Clipboard clipboard clip contains scan-ready text
     */
    fun checkClipboardContent() {
        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipDescription = clipboard.primaryClipDescription
                if (clipDescription != null && (
                    clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                    clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                )) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text?.toString() ?: ""
                    if (text.isNotBlank() && text != _clipboardText.value) {
                        _clipboardText.value = text
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ViewModel", "Clipboard detection security error: ${e.message}")
        }
    }

    /**
     * Direct scanning of clipboard text
     */
    fun scanClipboardText() {
        val text = _clipboardText.value
        if (text.isBlank()) return

        // Auto determine text type heuristically
        val hasUrl = text.contains("http://") || text.contains("https://") || text.contains("www.")
        val type = if (hasUrl && text.length < 200) "URL" else "SMS"

        _manualInput.value = text
        _selectedType.value = type
        runManualScan()
    }

    /**
     * Deletes log records
     */
    fun clearHistories() {
        viewModelScope.launch {
            threatDao.clearAllThreats()
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            threatDao.deleteThreatById(id)
        }
    }

    /**
     * Background Scan Simulation Engine - Inject simulated, real-time threat incoming SMS triggers
     */
    fun triggerSimulatedThreat(scenarioType: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val (sender, smsBody) = when (scenarioType) {
                "wellsfargo" -> Pair(
                    "+1 (888) 492-9102",
                    "Wells Fargo Critical Notification: Your account has been suspended due to an unrecognized login attempt from IP 184.22.9.11. Update your banking profile now at http://secure-wellsfargo-verify.web.app to restore instant online banking access immediately."
                )
                "usps" -> Pair(
                    "USPS-DeliveryHold",
                    "USPS Alert: Your package could not be delivered on 06/14 due to incorrect street number information. Action Required: Please update your residential addresses to schedule redelivery and unlock postal transit immediately at: https://usps-tracking-package-hold.click/verify"
                )
                "refund" -> Pair(
                    "IRS-GovRef",
                    "CONGRATULATIONS! Your federal tax refund of $1,420.50 is ready for instant direct deposit authorization. Click here secured portal verification: http://gov-irs-refund-claims.xyz/claim before 24 Hours to claim your funds!"
                )
                else -> Pair(
                    "Friend",
                    "Hey, are we still meeting for coffee at noon? Let me know, thanks!"
                )
            }

            // Real background threat analysis simulates what SmsReceiver does!
            val response = GeminiClient.scanContent("SMS", smsBody)

            val threatEntity = ThreatEntity(
                type = "SMS",
                content = smsBody,
                sender = sender,
                riskLevel = response.riskLevel,
                riskScore = response.riskScore,
                analysisVerdict = response.verdict,
                recommendedAction = response.recommendedAction,
                timestamp = System.currentTimeMillis()
            )
            // Insert to SQLite so user can view in Dashboard thread history
            threatDao.insertThreat(threatEntity)

            // Post notification using receiver static helper (satisfies real-time background notification mandate!)
            SmsReceiver.showInstantNotification(app, sender, smsBody, response)
        }
    }
}
