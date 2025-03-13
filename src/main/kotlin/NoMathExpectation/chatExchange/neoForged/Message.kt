package NoMathExpectation.chatExchange.neoForged

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val from: String,
    val content: String,
)
