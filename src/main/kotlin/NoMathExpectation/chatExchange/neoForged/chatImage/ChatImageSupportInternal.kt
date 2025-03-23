package NoMathExpectation.chatExchange.neoForged.chatImage

import NoMathExpectation.chatExchange.neoForged.ChatExchange
import io.github.kituin.ChatImageCode.ChatImageCode
import io.github.kituin.ChatImageCode.ServerStorage
import kotlinx.coroutines.delay
import org.apache.logging.log4j.LogManager
import kotlin.time.Duration.Companion.seconds

private val logger = LogManager.getLogger(ChatExchange.ID)

private val ciCodeRegex = ChatImageCode.pattern.toRegex()

private suspend fun findImageData(query: String): String? {
    repeat(10) {
        delay(0.5.seconds)

        ServerStorage.SERVER_BLOCK_CACHE.getImage(query)?.let {
            return it
        }
    }
    //logger.info(ServerStorage.SERVER_BLOCK_CACHE.blockCache)
    return null
}

internal suspend fun tryParseCICodeFileToData0(string: String) = buildString {
    var codeEnd = 0
    ciCodeRegex.findAll(string).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1

        if (codeEnd < start) {
            append(string.substring(codeEnd, start))
        }

        val data = match.value
            .removeSurrounding("[[", "]]")
            .trim()
            .split(",")
            .filter { "=" in it }
            .map { it.split("=", limit = 2) }
            .associate { it[0].trim() to it[1].trim() }
            .toMutableMap()

        var url = data["url"]
        if (url?.startsWith("file:///") == true) {
            val query = url.removePrefix("file:///")
            url = "invalid"
            findImageData(query)?.let {
                url = "base64://$it"
            }
        }
        data["url"] = url ?: "invalid"
        if (url == null || url == "invalid") {
            logger.warn("Unable to resolve CICode: ${match.value}")
        }

        val code = data.map { (k, v) -> "$k=$v" }
            .joinToString(",", "[[CICode,", "]]")
        append(code)

        codeEnd = end
    }
    append(string.substring(codeEnd))
}