package NoMathExpectation.chatExchange.neoForged

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager

class ExchangeServer(private val minecraftServer: MinecraftServer) : CoroutineScope {
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("ChatExchangeServer")
    override val coroutineContext get() = scope.coroutineContext

    private var launched = false

    private suspend fun serverRoutine() {
        val host = ChatExchangeConfig.host.get()
        val port = ChatExchangeConfig.port.get()

        logger.info("Starting exchange server on $host:$port...")
        val serverSocket = aSocket(manager).tcp().bind(host, port)
        serverSocket.use {
            while (isActive) {
                val socket = serverSocket.accept()
                logger.info("New connection from ${socket.remoteAddress}")
                scope.launch {
                    handleRoutine(socket)
                }
            }
        }
    }

    private val token = ChatExchangeConfig.token.get()

    private val sendChannels: MutableList<ByteWriteChannel> = mutableListOf()
    private val channelMutex = Mutex()

    private suspend fun handleRoutine(socket: Socket) {
        socket.use {
            val sendChannel = socket.openWriteChannel(autoFlush = true)
            kotlin.runCatching {
                val receiveChannel = socket.openReadChannel()

                if (token.isNotBlank()) {
                    sendChannel.writeBoolean(true)
                    val len = receiveChannel.readInt()
                    val text = receiveChannel.readPacket(len).readText()
                    if (text != token) {
                        return@runCatching
                    }
                } else {
                    sendChannel.writeBoolean(false)
                }

                channelMutex.withLock {
                    sendChannels += sendChannel
                }

                receiveRoutine(receiveChannel)
            }
            withContext(NonCancellable) {
                channelMutex.withLock {
                    sendChannels -= sendChannel
                }
            }
        }
        logger.info("A connection closed.")
    }

    private suspend fun receiveRoutine(channel: ByteReadChannel) {
        while (isActive) {
            kotlin.runCatching {
                val len = channel.readInt()
                val text = channel.readPacket(len).readText()
                val message = Json.decodeFromString<Message>(text)

                val formatted = "<${message.from}> ${message.content}"
                logger.info(formatted)
                minecraftServer.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(formatted))
                }
            }.onFailure {
                logger.error("Failed to receive message from a client.")
                logger.error(it)
            }
        }
    }

    private suspend fun sendMessage(message: Message) {
        val json = Json.encodeToString(message)
        channelMutex.withLock {
            sendChannels.forEach {
                kotlin.runCatching {
                    it.writeInt(json.length)
                    it.writeStringUtf8(json)
                }.onFailure {
                    logger.error("Failed to send message to a client.")
                    logger.error(it)
                }
            }
        }
    }

    fun launch() {
        if (launched) {
            error("Server is already launched!")
        }

        scope.launch { serverRoutine() }
        launched = true
    }

    companion object {
        private val logger = LogManager.getLogger(ChatExchange.ID)
        private val manager = SelectorManager(Dispatchers.IO)

        private var instance: ExchangeServer? = null

        fun startNewInstance(server: MinecraftServer) {
            instance?.cancel()
            instance = ExchangeServer(server)
            instance?.launch()
        }

        fun stopInstance() {
            logger.info("Stopping exchange server...")
            instance?.cancel()
            instance = null
        }

        fun sendMessage(message: Message) {
            val instance = instance ?: return
            instance.launch {
                instance.sendMessage(message)
            }
        }
    }
}