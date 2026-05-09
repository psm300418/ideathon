package com.ideathon.kondaeshield.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ideathon.kondaeshield.MainActivity
import com.ideathon.kondaeshield.R
import com.ideathon.kondaeshield.ai.GroqClient
import com.ideathon.kondaeshield.ai.OpenAiClient
import com.ideathon.kondaeshield.audio.AudioRecorder
import com.ideathon.kondaeshield.data.ProcessingState
import com.ideathon.kondaeshield.data.RecordingRepository
import com.ideathon.kondaeshield.data.RecordingSession
import com.ideathon.kondaeshield.settings.ApiProvider
import com.ideathon.kondaeshield.settings.ApiKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class RecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var repository: RecordingRepository
    private lateinit var apiKeyStore: ApiKeyStore

    private var currentSessionId: String? = null
    private var isProcessing = false

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        repository = RecordingRepository(this)
        apiKeyStore = ApiKeyStore(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            ACTION_TOGGLE, null -> {
                if (audioRecorder.isRecording) stopRecording() else startRecording()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        audioRecorder.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (!hasRecordAudioPermission()) {
            stopSelf()
            return
        }

        if (!apiKeyStore.hasTranscriptionApiKey()) {
            stopSelf()
            return
        }

        if (audioRecorder.isRecording || isProcessing) return

        val sessionId = UUID.randomUUID().toString()
        val audioFile = createAudioFile(sessionId)
        val session = RecordingSession(
            id = sessionId,
            createdAtEpochMillis = System.currentTimeMillis(),
            audioFilePath = audioFile.absolutePath,
            state = ProcessingState.RECORDING,
        )

        runCatching {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(
                    title = "녹음 중",
                    message = "볼륨 버튼 더블클릭으로 종료",
                    isRecording = true,
                ),
            )
            audioRecorder.start(audioFile)
            currentSessionId = sessionId
            repository.addSession(session)
            notifySessionsChanged()
        }.onFailure { throwable ->
            repository.addSession(
                session.copy(
                    state = ProcessingState.FAILED,
                    errorMessage = throwable.message ?: "녹음을 시작하지 못했습니다.",
                ),
            )
            notifySessionsChanged()
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopRecording() {
        val sessionId = currentSessionId ?: return
        val audioFile = audioRecorder.stop()
        currentSessionId = null

        if (audioFile == null) {
            repository.updateSession(sessionId) {
                it.copy(
                    state = ProcessingState.FAILED,
                    errorMessage = "녹음 파일이 생성되지 않았습니다.",
                )
            }
            notifySessionsChanged()
            stopForegroundCompat()
            stopSelf()
            return
        }

        repository.updateSession(sessionId) {
            it.copy(state = ProcessingState.TRANSCRIBING, audioFilePath = audioFile.absolutePath)
        }
        notifySessionsChanged()
        isProcessing = true

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                title = "전사 중",
                message = "스크립트를 생성하고 있습니다.",
                isRecording = false,
            ),
        )

        serviceScope.launch {
            processRecording(sessionId, audioFile)
        }
    }

    private fun processRecording(sessionId: String, audioFile: File) {
        runCatching {
            val transcript = transcribe(audioFile)
            repository.updateSession(sessionId) {
                it.copy(
                    state = ProcessingState.COMPLETE,
                    transcript = transcript,
                    summary = "",
                    errorMessage = null,
                )
            }
        }.onFailure { throwable ->
            repository.updateSession(sessionId) {
                it.copy(
                    state = ProcessingState.FAILED,
                    errorMessage = throwable.message ?: "전사 처리에 실패했습니다.",
                )
            }
        }

        notifySessionsChanged()
        isProcessing = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun transcribe(audioFile: File): String {
        val apiKey = apiKeyStore.getTranscriptionApiKey()
        return when (apiKeyStore.getTranscriptionProvider()) {
            ApiProvider.GROQ -> GroqClient(apiKey).transcribe(audioFile)
            ApiProvider.OPENAI -> OpenAiClient(apiKey).transcribe(audioFile)
            ApiProvider.GEMINI,
            ApiProvider.CLAUDE,
            ApiProvider.CUSTOM,
            -> error("현재 전사는 Groq 또는 OpenAI API 키로만 지원됩니다.")
        }
    }

    private fun createAudioFile(sessionId: String): File {
        val safeTimestamp = DateTimeFormatter.ISO_INSTANT
            .format(Instant.now())
            .replace(":", "-")
        return File(filesDir, "recordings/$safeTimestamp-$sessionId.m4a")
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun notifySessionsChanged() {
        sendBroadcast(
            Intent(ACTION_SESSIONS_CHANGED)
                .setPackage(packageName),
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        message: String,
        isRecording: Boolean,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setOngoing(isRecording)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (isRecording) {
                    addAction(R.drawable.ic_stat_mic, "종료", stopIntent)
                }
            }
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.ideathon.kondaeshield.action.TOGGLE_RECORDING"
        const val ACTION_START = "com.ideathon.kondaeshield.action.START_RECORDING"
        const val ACTION_STOP = "com.ideathon.kondaeshield.action.STOP_RECORDING"
        const val ACTION_SESSIONS_CHANGED = "com.ideathon.kondaeshield.action.SESSIONS_CHANGED"

        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001

        fun toggle(context: Context) {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            if (!ApiKeyStore(context).hasTranscriptionApiKey()) {
                return
            }

            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_TOGGLE)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
