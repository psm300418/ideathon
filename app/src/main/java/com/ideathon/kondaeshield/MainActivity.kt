package com.ideathon.kondaeshield

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ideathon.kondaeshield.accessibility.VolumeKeyAccessibilityService
import com.ideathon.kondaeshield.analysis.NaggingAnalysis
import com.ideathon.kondaeshield.analysis.NaggingAnalyzer
import com.ideathon.kondaeshield.data.ProcessingState
import com.ideathon.kondaeshield.data.RecordingRepository
import com.ideathon.kondaeshield.data.RecordingSession
import com.ideathon.kondaeshield.recording.RecordingService
import com.ideathon.kondaeshield.settings.ApiKeyStore
import com.ideathon.kondaeshield.settings.ApiProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var repository: RecordingRepository
    private lateinit var apiKeyStore: ApiKeyStore

    private var sessions by mutableStateOf<List<RecordingSession>>(emptyList())
    private var hasAudioPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(true)
    private var accessibilityEnabled by mutableStateOf(false)
    private var storedProviders by mutableStateOf<Set<ApiProvider>>(emptySet())
    private var transcriptionProvider by mutableStateOf(ApiProvider.GROQ)
    private var selectedTab by mutableStateOf(RECORD_SCRIPT_TAB)
    private var showApiKeyDialog by mutableStateOf(false)
    private var apiKeyProvider by mutableStateOf(ApiProvider.GROQ)
    private var apiKeyDraft by mutableStateOf("")
    private var transcriptDialogSession by mutableStateOf<RecordingSession?>(null)
    private var deleteDialogSession by mutableStateOf<RecordingSession?>(null)
    private var receiverRegistered = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshSettingsState()
    }

    private val sessionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshSessions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RecordingRepository(this)
        apiKeyStore = ApiKeyStore(this)
        refreshSessions()
        refreshSettingsState()

        setContent {
            NagBlockerTheme {
                NagBlockerScreen(
                    sessions = sessions,
                    hasAudioPermission = hasAudioPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    accessibilityEnabled = accessibilityEnabled,
                    storedProviders = storedProviders,
                    transcriptionProvider = transcriptionProvider,
                    selectedTab = selectedTab,
                    showApiKeyDialog = showApiKeyDialog,
                    apiKeyProvider = apiKeyProvider,
                    apiKeyDraft = apiKeyDraft,
                    transcriptDialogSession = transcriptDialogSession,
                    deleteDialogSession = deleteDialogSession,
                    onSelectTab = { selectedTab = it },
                    onRefresh = {
                        refreshSessions()
                        refreshSettingsState()
                    },
                    onRequestPermissions = ::requestRequiredPermissions,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onToggleRecording = { RecordingService.toggle(this) },
                    onOpenTranscript = { session ->
                        transcriptDialogSession = session
                    },
                    onDismissTranscript = {
                        transcriptDialogSession = null
                    },
                    onRequestDelete = { session ->
                        deleteDialogSession = session
                    },
                    onConfirmDelete = {
                        deleteDialogSession?.let { deletingSession ->
                            repository.deleteSession(deletingSession.id)
                            if (transcriptDialogSession?.id == deletingSession.id) {
                                transcriptDialogSession = null
                            }
                            deleteDialogSession = null
                            refreshSessions()
                        }
                    },
                    onDismissDelete = {
                        deleteDialogSession = null
                    },
                    onAnalyzeNagging = { session ->
                        val analysis = NaggingAnalyzer.analyze(session.transcript)
                        repository.updateSession(session.id) {
                            it.copy(naggingAnalysis = analysis.toJsonString())
                        }
                        refreshSessions()
                    },
                    onSelectTranscriptionProvider = { provider ->
                        apiKeyStore.setTranscriptionProvider(provider)
                        refreshSettingsState()
                    },
                    onOpenApiKeyDialog = { provider ->
                        apiKeyProvider = provider
                        apiKeyDraft = ""
                        showApiKeyDialog = true
                    },
                    onApiKeyDraftChange = { apiKeyDraft = it },
                    onSaveApiKey = {
                        apiKeyStore.setApiKey(apiKeyProvider, apiKeyDraft)
                        apiKeyDraft = ""
                        showApiKeyDialog = false
                        refreshSettingsState()
                    },
                    onDismissApiKeyDialog = {
                        apiKeyDraft = ""
                        showApiKeyDialog = false
                    },
                    onClearApiKey = { provider ->
                        apiKeyStore.clearApiKey(provider)
                        refreshSettingsState()
                    },
                )
            }
        }

        requestMissingPermissionsOnLaunch()
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter(RecordingService.ACTION_SESSIONS_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(sessionsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(sessionsReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSessions()
        refreshSettingsState()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(sessionsReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun refreshSessions() {
        sessions = repository.getSessions()
    }

    private fun refreshSettingsState() {
        updatePermissionState()
        updateAccessibilityState()
        storedProviders = apiKeyStore.getStoredProviders()
        transcriptionProvider = apiKeyStore.getTranscriptionProvider()
    }

    private fun requestMissingPermissionsOnLaunch() {
        val missingPermissions = missingRuntimePermissions()
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun requestRequiredPermissions() {
        val missingPermissions = missingRuntimePermissions()
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun missingRuntimePermissions(): List<String> =
        requiredPermissions().filterNot(::isPermissionGranted)

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun updatePermissionState() {
        hasAudioPermission = isPermissionGranted(Manifest.permission.RECORD_AUDIO)

        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun updateAccessibilityState() {
        val expectedService = ComponentName(
            this,
            VolumeKeyAccessibilityService::class.java,
        ).flattenToString()

        accessibilityEnabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
            ?.split(":")
            ?.any { it.equals(expectedService, ignoreCase = true) }
            ?: false
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NagBlockerScreen(
    sessions: List<RecordingSession>,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    accessibilityEnabled: Boolean,
    storedProviders: Set<ApiProvider>,
    transcriptionProvider: ApiProvider,
    selectedTab: Int,
    showApiKeyDialog: Boolean,
    apiKeyProvider: ApiProvider,
    apiKeyDraft: String,
    transcriptDialogSession: RecordingSession?,
    deleteDialogSession: RecordingSession?,
    onSelectTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenTranscript: (RecordingSession) -> Unit,
    onDismissTranscript: () -> Unit,
    onRequestDelete: (RecordingSession) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onAnalyzeNagging: (RecordingSession) -> Unit,
    onSelectTranscriptionProvider: (ApiProvider) -> Unit,
    onOpenApiKeyDialog: (ApiProvider) -> Unit,
    onApiKeyDraftChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onDismissApiKeyDialog: () -> Unit,
    onClearApiKey: (ApiProvider) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "꼰BTI",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "새로고침",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                AppTab(
                    selected = selectedTab == RECORD_SCRIPT_TAB,
                    label = "녹음",
                    icon = Icons.Default.Mic,
                    onClick = { onSelectTab(RECORD_SCRIPT_TAB) },
                )
                AppTab(
                    selected = selectedTab == SUMMARY_TAB,
                    label = "요약",
                    icon = Icons.Default.Description,
                    onClick = { onSelectTab(SUMMARY_TAB) },
                )
                AppTab(
                    selected = selectedTab == NAGGING_TAB,
                    label = "꼰대력",
                    icon = Icons.Default.Security,
                    onClick = { onSelectTab(NAGGING_TAB) },
                )
                AppTab(
                    selected = selectedTab == SETTINGS_TAB,
                    label = "설정",
                    icon = Icons.Default.Settings,
                    onClick = { onSelectTab(SETTINGS_TAB) },
                )
            }

            when (selectedTab) {
                RECORD_SCRIPT_TAB -> RecordScriptTab(
                    sessions = sessions,
                    hasAudioPermission = hasAudioPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    hasTranscriptionApiKey = transcriptionProvider in storedProviders,
                    onToggleRecording = onToggleRecording,
                    onOpenTranscript = onOpenTranscript,
                    onRequestDelete = onRequestDelete,
                )

                SUMMARY_TAB -> SummaryTab(sessions = sessions)

                NAGGING_TAB -> NaggingTab(
                    sessions = sessions,
                    onAnalyzeNagging = onAnalyzeNagging,
                )

                SETTINGS_TAB -> SettingsTab(
                    hasAudioPermission = hasAudioPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    accessibilityEnabled = accessibilityEnabled,
                    storedProviders = storedProviders,
                    transcriptionProvider = transcriptionProvider,
                    onRequestPermissions = onRequestPermissions,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onSelectTranscriptionProvider = onSelectTranscriptionProvider,
                    onOpenApiKeyDialog = onOpenApiKeyDialog,
                    onClearApiKey = onClearApiKey,
                )
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            provider = apiKeyProvider,
            apiKeyDraft = apiKeyDraft,
            onApiKeyDraftChange = onApiKeyDraftChange,
            onSaveApiKey = onSaveApiKey,
            onDismiss = onDismissApiKeyDialog,
        )
    }

    transcriptDialogSession?.let { session ->
        TranscriptDialog(
            session = session,
            onDismiss = onDismissTranscript,
        )
    }

    deleteDialogSession?.let { session ->
        DeleteSessionDialog(
            session = session,
            onConfirmDelete = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}

@Composable
private fun AppTab(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
    )
}

@Composable
private fun RecordScriptTab(
    sessions: List<RecordingSession>,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasTranscriptionApiKey: Boolean,
    onToggleRecording: () -> Unit,
    onOpenTranscript: (RecordingSession) -> Unit,
    onRequestDelete: (RecordingSession) -> Unit,
) {
    val activeSession = sessions.firstOrNull {
        it.state == ProcessingState.RECORDING ||
            it.state == ProcessingState.TRANSCRIBING ||
            it.state == ProcessingState.SUMMARIZING
    }
    val isRecording = activeSession?.state == ProcessingState.RECORDING
    val isProcessing = activeSession?.state == ProcessingState.TRANSCRIBING ||
        activeSession?.state == ProcessingState.SUMMARIZING
    val permissionsReady = hasAudioPermission && hasNotificationPermission

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            RecordingHeroPanel(
                isRecording = isRecording,
                isProcessing = isProcessing,
                permissionsReady = permissionsReady,
                hasTranscriptionApiKey = hasTranscriptionApiKey,
                onToggleRecording = onToggleRecording,
            )
        }

        item {
            Text(
                text = "텍스트화된 기록",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (sessions.isEmpty()) {
            item {
                EmptyState(
                    title = "아직 저장된 스크립트가 없습니다.",
                    description = "녹음을 마치면 전체 스크립트가 이곳에 쌓입니다.",
                )
            }
        } else {
            items(
                items = sessions,
                key = { it.id },
            ) { session ->
                ScriptSessionCard(
                    session = session,
                    onOpenTranscript = onOpenTranscript,
                    onRequestDelete = onRequestDelete,
                )
            }
        }
    }
}

@Composable
private fun SummaryTab(sessions: List<RecordingSession>) {
    val scriptSessions = sessions.filter { it.transcript.isNotBlank() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FeatureHeaderPanel(
                title = "요약",
                subtitle = "전체 스크립트를 바탕으로 업무용 요약과 마음 보호용 해석을 생성하는 영역입니다.",
                icon = Icons.Default.Description,
            )
        }

        if (scriptSessions.isEmpty()) {
            item {
                EmptyState(
                    title = "요약할 스크립트가 없습니다.",
                    description = "먼저 녹음 탭에서 스크립트를 저장해 주세요.",
                )
            }
        } else {
            items(
                items = scriptSessions,
                key = { it.id },
            ) { session ->
                SummarySessionCard(session = session)
            }
        }
    }
}

@Composable
private fun NaggingTab(
    sessions: List<RecordingSession>,
    onAnalyzeNagging: (RecordingSession) -> Unit,
) {
    val scriptSessions = sessions.filter { it.transcript.isNotBlank() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FeatureHeaderPanel(
                title = "꼰BTI 측정",
                subtitle = "스크립트 속 잔소리 패턴을 분석해 어떤 유형의 꼰대인지 분류합니다.",
                icon = Icons.Default.Security,
            )
        }

        if (scriptSessions.isEmpty()) {
            item {
                EmptyState(
                    title = "분석할 스크립트가 없습니다.",
                    description = "녹음 후 저장된 전체 스크립트를 먼저 만들어 주세요.",
                )
            }
        } else {
            items(
                items = scriptSessions,
                key = { it.id },
            ) { session ->
                NaggingSessionCard(
                    session = session,
                    onAnalyzeNagging = onAnalyzeNagging,
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    accessibilityEnabled: Boolean,
    storedProviders: Set<ApiProvider>,
    transcriptionProvider: ApiProvider,
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSelectTranscriptionProvider: (ApiProvider) -> Unit,
    onOpenApiKeyDialog: (ApiProvider) -> Unit,
    onClearApiKey: (ApiProvider) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsPanel(
                hasAudioPermission = hasAudioPermission,
                hasNotificationPermission = hasNotificationPermission,
                accessibilityEnabled = accessibilityEnabled,
                storedProviders = storedProviders,
                transcriptionProvider = transcriptionProvider,
                onRequestPermissions = onRequestPermissions,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onSelectTranscriptionProvider = onSelectTranscriptionProvider,
                onOpenApiKeyDialog = onOpenApiKeyDialog,
                onClearApiKey = onClearApiKey,
            )
        }
    }
}

@Composable
private fun RecordingHeroPanel(
    isRecording: Boolean,
    isProcessing: Boolean,
    permissionsReady: Boolean,
    hasTranscriptionApiKey: Boolean,
    onToggleRecording: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            enabled = permissionsReady && hasTranscriptionApiKey && !isProcessing,
            onClick = onToggleRecording,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (isRecording) "녹음 종료" else "녹음 시작")
        }
    }
}

@Composable
private fun FeatureHeaderPanel(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                    imageVector = icon,
                    contentDescription = null,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    accessibilityEnabled: Boolean,
    storedProviders: Set<ApiProvider>,
    transcriptionProvider: ApiProvider,
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSelectTranscriptionProvider: (ApiProvider) -> Unit,
    onOpenApiKeyDialog: (ApiProvider) -> Unit,
    onClearApiKey: (ApiProvider) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsSection(title = "권한") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(
                    icon = Icons.Default.Mic,
                    label = if (hasAudioPermission) "마이크 허용" else "마이크 필요",
                    positive = hasAudioPermission,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    StatusChip(
                        icon = Icons.Default.Security,
                        label = if (hasNotificationPermission) "알림 허용" else "알림 필요",
                        positive = hasNotificationPermission,
                    )
                }
                StatusChip(
                    icon = Icons.Default.Security,
                    label = if (accessibilityEnabled) "볼륨 감지 켜짐" else "볼륨 감지 꺼짐",
                    positive = accessibilityEnabled,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onRequestPermissions) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("권한 요청")
                }
                OutlinedButton(onClick = onOpenAccessibilitySettings) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("접근성")
                }
            }
        }

        SettingsSection(title = "전사 공급자") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ApiProvider.entries
                    .filter { it.supportsTranscription }
                    .forEach { provider ->
                        ProviderButton(
                            provider = provider,
                            selected = provider == transcriptionProvider,
                            enabled = provider in storedProviders,
                            onClick = { onSelectTranscriptionProvider(provider) },
                        )
                    }
            }
            Text(
                text = "녹음 파일 전사는 현재 Groq와 OpenAI 키를 지원합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(title = "API 키") {
            ApiProvider.entries.forEach { provider ->
                ProviderKeyRow(
                    provider = provider,
                    stored = provider in storedProviders,
                    onOpenApiKeyDialog = onOpenApiKeyDialog,
                    onClearApiKey = onClearApiKey,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun ProviderButton(
    provider: ApiProvider,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(provider.displayName)
        }
    } else {
        OutlinedButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(provider.displayName)
        }
    }
}

@Composable
private fun ProviderKeyRow(
    provider: ApiProvider,
    stored: Boolean,
    onOpenApiKeyDialog: (ApiProvider) -> Unit,
    onClearApiKey: (ApiProvider) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (stored) "저장됨" else "미입력",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpenApiKeyDialog(provider) }) {
                Text(if (stored) "수정" else "입력")
            }
            OutlinedButton(
                enabled = stored,
                onClick = { onClearApiKey(provider) },
            ) {
                Text("삭제")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptDialog(
    session: RecordingSession,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(formatSessionTime(session.createdAtEpochMillis))
        },
        text = {
            SelectionContainer {
                Text(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    text = session.transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteSessionDialog(
    session: RecordingSession,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("기록 삭제")
        },
        text = {
            Text(
                text = "${formatSessionTime(session.createdAtEpochMillis)} 녹음 파일과 스크립트 기록을 모두 삭제할까요?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(
                    text = "삭제",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyDialog(
    provider: ApiProvider,
    apiKeyDraft: String,
    onApiKeyDraftChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${provider.displayName} API 키")
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKeyDraft,
                onValueChange = onApiKeyDraftChange,
                label = { Text("API 키") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = apiKeyDraft.isNotBlank(),
                onClick = onSaveApiKey,
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    positive: Boolean,
) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = icon,
                contentDescription = null,
                tint = if (positive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
    )
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptSessionCard(
    session: RecordingSession,
    onOpenTranscript: (RecordingSession) -> Unit,
    onRequestDelete: (RecordingSession) -> Unit,
) {
    SessionShell(session = session) {
        if (session.transcript.isNotBlank()) {
            Text(
                text = session.transcript,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = when (session.state) {
                    ProcessingState.RECORDING -> "녹음 중입니다."
                    ProcessingState.TRANSCRIBING -> "스크립트를 생성하고 있습니다."
                    ProcessingState.SUMMARIZING -> "요약을 준비하고 있습니다."
                    ProcessingState.COMPLETE -> "저장된 스크립트가 비어 있습니다."
                    ProcessingState.FAILED -> "스크립트를 만들지 못했습니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                enabled = session.transcript.isNotBlank(),
                onClick = { onOpenTranscript(session) },
            ) {
                Text("전체 보기")
            }
            TextButton(
                enabled = session.state.canDelete(),
                onClick = { onRequestDelete(session) },
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "삭제",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummarySessionCard(session: RecordingSession) {
    SessionShell(session = session) {
        val businessSummary = session.businessSummary.ifBlank { session.summary }
        if (businessSummary.isNotBlank()) {
            TextSection(
                icon = Icons.Default.Description,
                title = "업무용 요약",
                body = businessSummary,
            )
        }
        if (session.comfortInterpretation.isNotBlank()) {
            TextSection(
                icon = Icons.Default.Security,
                title = "마음 보호용 해석",
                body = session.comfortInterpretation,
            )
        }
        if (businessSummary.isBlank() && session.comfortInterpretation.isBlank()) {
            Text(
                text = "요약 결과가 없습니다. 팀원이 이 버튼에 생성 로직을 연결하면 됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                enabled = false,
                onClick = {},
            ) {
                Text("업무용 요약")
            }
            OutlinedButton(
                enabled = false,
                onClick = {},
            ) {
                Text("마음 보호")
            }
        }
    }
}

@Composable
private fun NaggingSessionCard(
    session: RecordingSession,
    onAnalyzeNagging: (RecordingSession) -> Unit,
) {
    val analysis = NaggingAnalysis.fromJsonString(session.naggingAnalysis)

    SessionShell(session = session) {
        if (analysis != null) {
            NaggingResultContent(analysis = analysis)
        } else {
            Text(
                text = "아직 꼰BTI 결과가 없습니다. 이 스크립트를 분석해서 잔소리 유형을 확인해보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = { onAnalyzeNagging(session) },
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = Icons.Default.Security,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (analysis == null) "꼰BTI 측정" else "다시 측정")
        }
    }
}

@Composable
private fun NaggingResultContent(analysis: NaggingAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = analysis.resultLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "꼰대력 ${analysis.totalPercent}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NaggingScoreRow(label = "비난", percent = analysis.blamePercent)
            NaggingScoreRow(label = "비교", percent = analysis.comparisonPercent)
            NaggingScoreRow(label = "일방적 지시", percent = analysis.commandPercent)
            NaggingScoreRow(label = "라떼력", percent = analysis.lattePercent)
            NaggingScoreRow(label = "실질 조언", percent = analysis.practicalAdvicePercent)
        }

        TextSection(
            icon = Icons.Default.Security,
            title = "해석",
            body = analysis.explanation,
        )
        TextSection(
            icon = Icons.Default.Description,
            title = "바꿔 말하면",
            body = analysis.rewriteSuggestion,
        )
    }
}

@Composable
private fun NaggingScoreRow(
    label: String,
    percent: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SessionShell(
    session: RecordingSession,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatSessionTime(session.createdAtEpochMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StateBadge(state = session.state)
            }

            if (session.errorMessage != null) {
                Text(
                    text = session.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            content()
        }
    }
}

@Composable
private fun StateBadge(state: ProcessingState) {
    val container = when (state) {
        ProcessingState.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
        ProcessingState.FAILED -> MaterialTheme.colorScheme.errorContainer
        ProcessingState.RECORDING -> Color(0xFFFFE1CC)
        ProcessingState.TRANSCRIBING,
        ProcessingState.SUMMARIZING,
        -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (state) {
        ProcessingState.COMPLETE -> MaterialTheme.colorScheme.onPrimaryContainer
        ProcessingState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        ProcessingState.RECORDING -> Color(0xFF7A2E00)
        ProcessingState.TRANSCRIBING,
        ProcessingState.SUMMARIZING,
        -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = state.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TextSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        SelectionContainer {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NagBlockerTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF006D77),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8F3F0),
        onPrimaryContainer = Color(0xFF003B40),
        secondary = Color(0xFF7C5A31),
        secondaryContainer = Color(0xFFFFE5BF),
        onSecondaryContainer = Color(0xFF2E1B00),
        tertiary = Color(0xFF7357A5),
        tertiaryContainer = Color(0xFFEADCFF),
        onTertiaryContainer = Color(0xFF2B164F),
        error = Color(0xFFB3261E),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF7FAF9),
        surface = Color.White,
        onSurface = Color(0xFF17201F),
        onSurfaceVariant = Color(0xFF52605E),
    )

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

private fun formatSessionTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

private fun ProcessingState.canDelete(): Boolean =
    this != ProcessingState.RECORDING &&
        this != ProcessingState.TRANSCRIBING &&
        this != ProcessingState.SUMMARIZING

private const val RECORD_SCRIPT_TAB = 0
private const val SUMMARY_TAB = 1
private const val NAGGING_TAB = 2
private const val SETTINGS_TAB = 3
