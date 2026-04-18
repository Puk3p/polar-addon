package ro.puk3p.polaraddon.infrastructure.discord

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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
            val connection = URL(webhookUrl).openConnection() as HttpURLConnection
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

            val code = connection.responseCode
            if (code !in 200..299 && code != 204) {
                val errorBody = readResponseBody(connection)
                val suffix =
                    if (errorBody.isBlank()) {
                        ""
                    } else {
                        " Response: $errorBody"
                    }
                plugin.logger.warning("Discord webhook returned HTTP $code.$suffix")
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    plugin.logger.warning(
                        "Discord webhook is forbidden (403). Check webhook URL/token, channel access, and thread permissions.",
                    )
                }
            }
        } catch (exception: IOException) {
            plugin.logger.warning("Failed to send Discord webhook: ${exception.message}")
        }
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
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

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
    }
}
