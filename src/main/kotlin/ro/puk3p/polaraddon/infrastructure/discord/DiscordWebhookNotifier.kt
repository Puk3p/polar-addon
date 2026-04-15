package ro.puk3p.polaraddon.infrastructure.discord

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
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
    fun sendEmbed(
        title: String,
        description: String,
        color: Int,
        fields: List<DiscordField>,
    ) {
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
                val payload =
                    buildPayload(
                        title = title,
                        description = description,
                        color = color,
                        fields = fields,
                    )
                post(payload)
            },
        )
    }

    private fun buildPayload(
        title: String,
        description: String,
        color: Int,
        fields: List<DiscordField>,
    ): String {
        val usernameJson = "\"username\":\"${escape(username)}\""
        val avatarJson =
            if (avatarUrl.isNullOrBlank()) {
                ""
            } else {
                ",\"avatar_url\":\"${escape(avatarUrl)}\""
            }

        val fieldsJson =
            fields.joinToString(",") { field ->
                "{\"name\":\"${escape(field.name)}\",\"value\":\"${escape(field.value)}\",\"inline\":${field.inline}}"
            }

        return "{" +
            usernameJson +
            avatarJson +
            ",\"embeds\":[{" +
            "\"title\":\"${escape(title)}\"," +
            "\"description\":\"${escape(description)}\"," +
            "\"color\":$color," +
            "\"fields\":[$fieldsJson]" +
            "}]" +
            "}"
    }

    private fun post(payload: String) {
        try {
            val connection = URL(webhookUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                it.write(payload)
            }

            val code = connection.responseCode
            if (code !in 200..299 && code != 204) {
                plugin.logger.warning("Discord webhook returned HTTP $code.")
            }
        } catch (exception: Exception) {
            plugin.logger.warning("Failed to send Discord webhook: ${exception.message}")
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

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5000
        const val READ_TIMEOUT_MILLIS = 5000
    }
}
