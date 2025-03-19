package NoMathExpectation.chatExchange.neoForged

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager

class ExchangeServer(
    private val minecraftServer: MinecraftServer,
    private val language: Language,
) : CoroutineScope {
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
                    sendChannel.writeExchangeEvent(AuthenticateEvent(required = true))
                    val actualToken = (receiveChannel.readExchangeEvent() as? AuthenticateEvent)?.token
                    if (token != actualToken) {
                        sendChannel.writeExchangeEvent(AuthenticateEvent(success = false))
                        return@runCatching
                    }
                    sendChannel.writeExchangeEvent(AuthenticateEvent(success = true))
                } else {
                    sendChannel.writeExchangeEvent(AuthenticateEvent(required = false))
                }

                channelMutex.withLock {
                    sendChannels += sendChannel
                }

                receiveRoutine(receiveChannel)
            }.onFailure {
                logger.info("Exception during handling connection.", it)
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
                val event = channel.readExchangeEvent()
                logger.info("Received event: $event")
                if (event !is MessageEvent) {
                    return@runCatching
                }

                val formatted = "<${event.from}> ${event.content}"
                logger.info(formatted)
                minecraftServer.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(formatted))
                }
            }.onFailure {
                if (channel.isClosedForRead) {
                    return
                }
                logger.error("Failed to receive message from a client.", it)
            }
        }
    }

    suspend fun sendEvent(event: ExchangeEvent) {
        logger.info("Sending event: $event")
        channelMutex.withLock {
            sendChannels.forEach {
                kotlin.runCatching {
                    it.writeExchangeEvent(event)
                }.onFailure {
                    logger.error("Failed to send message to a client.", it)
                }
            }
        }
    }

    fun componentToString(component: Component) = component.getStringWithLanguage(language)

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

            val languageName = ChatExchangeConfig.language.get()
            val language = if (languageName.isNotBlank()) {
                languageOfOrDefault(languageName, server)
            } else {
                Language.getInstance()
            }

            instance = ExchangeServer(server, language)
            instance?.launch()
        }

        fun stopInstance() {
            logger.info("Stopping exchange server...")
            instance?.cancel()
            instance = null
        }

        fun sendEvent(event: ExchangeEvent) {
            val instance = instance ?: return
            instance.launch {
                instance.sendEvent(event)
            }
        }

        fun componentToString(component: Component): String = instance?.componentToString(component) ?: component.string
    }
}