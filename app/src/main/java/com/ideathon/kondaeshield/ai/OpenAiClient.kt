package com.ideathon.kondaeshield.ai

import com.ideathon.kondaeshield.BuildConfig
import com.ideathon.kondaeshield.analysis.JinsangAnalysis
import com.ideathon.kondaeshield.analysis.JinsangEmpathyContext
import com.ideathon.kondaeshield.summary.SummaryMode
import com.ideathon.kondaeshield.summary.SummaryPromptContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class OpenAiClient(
    private val apiKey: String,
    private val transcriptionModel: String = BuildConfig.OPENAI_TRANSCRIPTION_MODEL,
    private val summaryModel: String = BuildConfig.OPENAI_SUMMARY_MODEL,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    fun transcribe(audioFile: File): String {
        ensureConfigured()

        val boundary = "KondaeShield-${UUID.randomUUID()}"
        val connection = (URL("https://api.openai.com/v1/audio/transcriptions").openConnection() as HttpsURLConnection).apply {
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
            output.writeFilePart(boundary, "file", audioFile, "audio/mp4")
            output.write("--$boundary--\r\n".toByteArray())
            output.flush()
        }

        val response = connection.readBodyOrThrow()
        return JSONObject(response).getString("text").trim()
    }

    fun generateSummary(
        transcript: String,
        mode: SummaryMode,
    ): String {
        ensureConfigured()
        val context = SummaryPromptContext.forMode(transcript, mode)

        val request = JSONObject()
            .put("model", summaryModel)
            .put("store", false)
            .put("max_output_tokens", 500)
            .put("instructions", AiPromptTemplates.summaryInstructions(mode))
            .put(
                "input",
                AiPromptTemplates.buildSummaryPrompt(
                    context = context,
                    mode = mode,
                ),
            )

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(request.toString())
        }

        return formatSummaryOutput(
            rawOutput = extractOutputText(JSONObject(connection.readBodyOrThrow())),
            mode = mode,
        )
    }

    fun generateJinsangEmpathy(
        customerTranscript: String,
        analysis: JinsangAnalysis,
    ): String {
        ensureConfigured()
        val empathyContext = JinsangEmpathyContext.fromCustomerTranscript(customerTranscript)

        val request = JSONObject()
            .put("model", summaryModel)
            .put("store", false)
            .put("max_output_tokens", 120)
            .put("instructions", AiPromptTemplates.empathyInstructions)
            .put(
                "input",
                AiPromptTemplates.buildEmpathyPrompt(
                    empathyContext = empathyContext,
                    analysis = analysis,
                ),
            )

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(request.toString())
        }

        return extractOutputText(JSONObject(connection.readBodyOrThrow())).trim()
    }

    private fun ensureConfigured() {
        check(isConfigured) {
            "OpenAI API 키가 비어 있습니다. 앱 설정 탭에서 API 키를 입력하세요."
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
            error("OpenAI API error $status: $message")
        }

        return body
    }

    private fun extractOutputText(response: JSONObject): String {
        response.optString("output_text")
            .takeIf { it.isNotBlank() }
            ?.let { return it }

        val output = response.optJSONArray("output") ?: JSONArray()
        val chunks = buildList {
            for (outputIndex in 0 until output.length()) {
                val item = output.optJSONObject(outputIndex) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    val contentItem = content.optJSONObject(contentIndex) ?: continue
                    contentItem.optString("text")
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        }

        return chunks.joinToString("\n").ifBlank {
            "요약 결과를 읽지 못했습니다."
        }
    }

    private fun formatSummaryOutput(
        rawOutput: String,
        mode: SummaryMode,
    ): String {
        val key = when (mode) {
            SummaryMode.BUSINESS -> "items"
            SummaryMode.COMFORT -> "interpretations"
        }
        val lines = runCatching {
            JSONObject(rawOutput.extractJsonObject())
                .optJSONArray(key)
                ?.toStringList()
                .orEmpty()
        }.getOrDefault(emptyList())

        return lines
            .map { it.cleanSummaryLine() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_SUMMARY_LINES)
            .joinToString("\n") { "- $it" }
            .ifBlank { rawOutput.cleanPlainSummary() }
    }

    private fun String.extractJsonObject(): String {
        val trimmed = trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end >= start) {
            trimmed.substring(start, end + 1)
        } else {
            trimmed
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) {
                optString(index)
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }

    private fun String.cleanSummaryLine(): String =
        trim()
            .removePrefix("-")
            .removePrefix("•")
            .trim()

    private fun String.cleanPlainSummary(): String =
        lineSequence()
            .map { it.cleanSummaryLine() }
            .filter { it.isNotBlank() }
            .take(MAX_SUMMARY_LINES)
            .joinToString("\n") { "- $it" }

    private companion object {
        private const val MAX_SUMMARY_LINES = 7
    }
}
