package ro.puk3p.polaraddon.infrastructure.discord

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.roundToLong

class DiscordWebhookNotifier(
    private val plugin: JavaPlugin,
    private val webhookUrl: String,
    private val username: String,
    private val avatarUrl: String?,
) {
    fun sendEmbed(embed: DiscordEmbed) {
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                val payload = buildPayload(embed)
                post(payload)
            },
        )
    }

    private fun buildPayload(embed: DiscordEmbed): String {
        val usernameJson = "\"username\":\"${escape(username)}\""
        val avatarJson =
            if (avatarUrl.isNullOrBlank()) {
                ""
            } else {
                ",\"avatar_url\":\"${escape(avatarUrl)}\""
            }

        val fieldsJson =
            embed.fields.joinToString(",") { field ->
                "{\"name\":\"${escape(field.name)}\",\"value\":\"${escape(field.value)}\",\"inline\":${field.inline}}"
            }

        val thumbnailJson =
            embed.thumbnailUrl?.let { ",\"thumbnail\":{\"url\":\"${escape(it)}\"}" } ?: ""

        val footerJson =
            embed.footerText?.let { ",\"footer\":{\"text\":\"${escape(it)}\"}" } ?: ""

        val timestampJson =
            embed.timestampIso8601?.let { ",\"timestamp\":\"${escape(it)}\"}" } ?: ""

        return "{" +
            usernameJson +
            avatarJson +
            ",\"embeds\":[{" +
            "\"title\":\"${escape(embed.title)}\"," +
            "\"description\":\"${escape(embed.description)}\"," +
            "\"color\":${embed.color}," +
            "\"fields\":[$fieldsJson]" +
            thumbnailJson +
            footerJson +
            timestampJson +
            "}]" +
            "}"
    }

    private fun post(payload: String) {
        try {
            synchronized(SEND_LOCK) {
                sendWithRetry(payload)
            }
        } catch (exception: IOException) {
            plugin.logger.warning("Failed to send Discord webhook: ${exception.message}")
        }
    }

    @Throws(IOException::class)
    private fun sendWithRetry(payload: String) {
        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            waitForRateLimitWindow()
            val response = executeRequest(payload)
            if (response.code in 200..299 || response.code == 204) {
                return
            }

            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                val retryAfterMillis = parseRetryAfterMillis(response.body)
                nextAllowedRequestAtMillis = System.currentTimeMillis() + retryAfterMillis
                attempt += 1
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    Thread.sleep(retryAfterMillis)
                    continue
                }
            }

            val suffix =
                if (response.body.isBlank()) {
                    ""
                } else {
                    " Response: ${response.body}"
                }
            plugin.logger.warning("Discord webhook returned HTTP ${response.code}.$suffix")
            if (response.code == HttpURLConnection.HTTP_FORBIDDEN) {
                plugin.logger.warning(
                    "Discord webhook is forbidden (403). Check webhook URL/token, channel access, and thread permissions.",
                )
            }
            return
        }
    }

    @Throws(IOException::class)
    private fun executeRequest(payload: String): HttpResponse {
        val connection = URL(webhookUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", DISCORD_WEBHOOK_USER_AGENT)
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                it.write(payload)
            }

            return HttpResponse(
                code = connection.responseCode,
                body = readResponseBody(connection),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun waitForRateLimitWindow() {
        val waitMillis = nextAllowedRequestAtMillis - System.currentTimeMillis()
        if (waitMillis > 0L) {
            try {
                Thread.sleep(waitMillis)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for Discord rate limit window", interrupted)
            }
        }
    }

    private fun parseRetryAfterMillis(responseBody: String): Long {
        val match = RETRY_AFTER_REGEX.find(responseBody) ?: return DEFAULT_RETRY_AFTER_MILLIS
        val seconds = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return DEFAULT_RETRY_AFTER_MILLIS
        return (seconds * 1000.0).roundToLong().coerceAtLeast(MIN_RETRY_AFTER_MILLIS)
    }

    private fun readResponseBody(connection: HttpURLConnection): String {
        return try {
            val stream = connection.errorStream ?: connection.inputStream ?: return ""
            stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.readText().trim().take(MAX_LOG_RESPONSE_LENGTH)
            }
        } catch (_: IOException) {
            ""
        }
    }

    private fun escape(value: String): String {
        val escaped = StringBuilder(value.length + 16)
        value.forEach { char ->
            when (char) {
                '\\' -> escaped.append("\\\\")
                '"' -> escaped.append("\\\"")
                '\b' -> escaped.append("\\b")
                '\u000C' -> escaped.append("\\f")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        escaped.append("\\u%04x".format(char.code))
                    } else {
                        escaped.append(char)
                    }
                }
            }
        }
        return escaped.toString()
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )

    data class DiscordField(
        val name: String,
        val value: String,
        val inline: Boolean = true,
    )

    data class DiscordEmbed(
        val title: String,
        val description: String,
        val color: Int,
        val fields: List<DiscordField>,
        val thumbnailUrl: String? = null,
        val footerText: String? = null,
        val timestampIso8601: String? = null,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5000
        const val READ_TIMEOUT_MILLIS = 5000
        const val MAX_LOG_RESPONSE_LENGTH = 400
        const val DISCORD_WEBHOOK_USER_AGENT =
            "Mozilla/5.0 (compatible; PolarAddon/1.0; +https://github.com/Puk3p/polar-addon)"
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RETRY_ATTEMPTS = 3
        const val MIN_RETRY_AFTER_MILLIS = 250L
        const val DEFAULT_RETRY_AFTER_MILLIS = 1000L
        val RETRY_AFTER_REGEX = Regex("\"retry_after\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
        val SEND_LOCK = Any()
    }

    @Volatile
    private var nextAllowedRequestAtMillis: Long = 0L
}
