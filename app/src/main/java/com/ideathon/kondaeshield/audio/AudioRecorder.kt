package com.ideathon.kondaeshield.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(file: File) {
        check(recorder == null) { "Recorder is already running." }
        file.parentFile?.mkdirs()

        val mediaRecorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        outputFile = file
        recorder = mediaRecorder
    }

    fun stop(): File? {
        val file = outputFile
        val currentRecorder = recorder ?: return file

        runCatching { currentRecorder.stop() }
            .onFailure { file?.delete() }

        currentRecorder.release()
        recorder = null
        outputFile = null
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    fun release() {
        recorder?.release()
        recorder = null
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
