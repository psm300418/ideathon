package com.ideathon.kondaeshield.ai

import com.ideathon.kondaeshield.BuildConfig
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class GroqClient(
    private val apiKey: String,
    private val transcriptionModel: String = BuildConfig.GROQ_TRANSCRIPTION_MODEL,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    fun transcribe(audioFile: File): String {
        ensureConfigured()

        val boundary = "NagBlocker-${UUID.randomUUID()}"
        val connection = (URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        BufferedOutputStream(connection.outputStream).use { output ->
            output.writeTextPart(boundary, "model", transcriptionModel)
            output.writeTextPart(boundary, "language", "ko")
            output.writeTextPart(boundary, "response_format", "json")
            output.writeFilePart(boundary, "file", audioFile, "audio/mp4")
            output.write("--$boundary--\r\n".toByteArray())
            output.flush()
        }

        return JSONObject(connection.readBodyOrThrow())
            .getString("text")
            .trim()
    }

    private fun ensureConfigured() {
        check(isConfigured) {
            "Groq API 키가 비어 있습니다. 앱 설정 탭에서 API 키를 입력하세요."
        }
    }

    private fun BufferedOutputStream.writeTextPart(boundary: String, name: String, value: String) {
        write("--$boundary\r\n".toByteArray())
        write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        write(value.toByteArray(Charsets.UTF_8))
        write("\r\n".toByteArray())
    }

    private fun BufferedOutputStream.writeFilePart(
        boundary: String,
        name: String,
        file: File,
        contentType: String,
    ) {
        write("--$boundary\r\n".toByteArray())
        write(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n"
                .toByteArray(),
        )
        write("Content-Type: $contentType\r\n\r\n".toByteArray())
        file.inputStream().use { input -> input.copyTo(this) }
        write("\r\n".toByteArray())
    }

    private fun HttpURLConnection.readBodyOrThrow(): String {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        if (status !in 200..299) {
            val message = runCatching {
                JSONObject(body).getJSONObject("error").getString("message")
            }.getOrDefault(body)
            error("Groq API error $status: $message")
        }

        return body
    }
}
