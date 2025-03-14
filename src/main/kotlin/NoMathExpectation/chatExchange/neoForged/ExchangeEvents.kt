package NoMathExpectation.chatExchange.neoForged

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface ExchangeEvent

suspend fun ByteReadChannel.readExchangeEvent(): ExchangeEvent {
    val len = readInt()
    val text = readPacket(len).readBytes().decodeToString()
    val message = Json.decodeFromString<ExchangeEvent>(text)
    return message
}

suspend fun ByteWriteChannel.writeExchangeEvent(event: ExchangeEvent) {
    val json = Json.encodeToString(event)
    val bytes = json.encodeToByteArray()
    writeInt(bytes.size)
    writeFully(bytes)
}

@Serializable
@SerialName("authenticate")
data class AuthenticateEvent(
    val required: Boolean? = null,
    val token: String? = null,
    val success: Boolean? = null,
) : ExchangeEvent

@Serializable
@SerialName("message")
data class MessageEvent(
    val from: String,
    val content: String,
) : ExchangeEvent

@Serializable
@SerialName("player_join")
data class PlayerJoinEvent(
    val name: String,
) : ExchangeEvent

@Serializable
@SerialName("player_leave")
data class PlayerLeaveEvent(
    val name: String,
) : ExchangeEvent

@Serializable
@SerialName("player_die")
data class PlayerDieEvent(
    val name: String,
    val text: String,
) : ExchangeEvent

@Serializable
@SerialName("player_advancement")
data class PlayerAdvancementEvent(
    val name: String,
    val advancement: String,
) : ExchangeEvent