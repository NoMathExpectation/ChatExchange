package NoMathExpectation.chatExchange.neoForged

import NoMathExpectation.chatExchange.neoForged.chatImage.tryParseCICodeFileToData
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import kotlin.time.Duration.Companion.seconds

class ExchangeServer(
    private val minecraftServer: MinecraftServer,
    private val language: Language,
) : CoroutineScope {
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("ChatExchangeServer")
    override val coroutineContext get() = scope.coroutineContext

    private var launched = false

    private suspend fun serverRoutine() {
        while (isActive) {
            kotlin.runCatching {
                val host = ChatExchangeConfig.host.get()
                val port = ChatExchangeConfig.port.get()

                logger.info("Starting exchange server on $host:$port...")
                val serverSocket = aSocket(manager).tcp().bind(host, port)
                serverSocket.use {
                    while (isActive) {
                        val socket = serverSocket.accept()
                        scope.launch {
                            handleRoutine(socket)
                        }
                    }
                }
            }.onFailure {
                if (!isActive) {
                    return
                }
                logger.error("Server crashed! Will try to restart in 30 seconds.", it)
                delay(30.seconds)
            }
        }
    }

    private val token = ChatExchangeConfig.token.get()

    private val sendChannels: MutableList<ByteWriteChannel> = mutableListOf()
    private val channelMutex = Mutex()

    private val maxSafeReadBytes = ChatExchangeConfig.maxSafeReadBytesPerEvent.get()
    private val maxConnectionPerAddress = ChatExchangeConfig.maxConnectionsPerAddress.get()

    private suspend fun ByteReadChannel.readExchangeEventSafe() = readExchangeEvent(maxSafeReadBytes)

    private val clientConnectionCount = mutableMapOf<String, Int>()
    private val clientConnectionMutex = Mutex()

    private suspend fun handleRoutine(socket: Socket) {
        socket.use {
            val remoteAddress = socket.remoteAddress.toJavaAddress().address
            val remoteAddressDisplay = socket.remoteAddress.toString().takeIf { it.isNotEmpty() } ?: "<unknown>"

            if (remoteAddress.isNotEmpty()) {
                clientConnectionMutex.withLock {
                    val currentCount = clientConnectionCount.getOrDefault(remoteAddress, 0)
                    if (currentCount >= maxConnectionPerAddress) {
                        logger.info("Address $remoteAddressDisplay attempted to connect but has reached the max connection limit. Connection refused.")
                        return@use
                    }

                    clientConnectionCount[remoteAddress] = currentCount + 1
                }
            }

            logger.info("New connection from $remoteAddressDisplay .")
            kotlin.runCatching {
                val sendChannel = socket.openWriteChannel(autoFlush = true)
                kotlin.runCatching inner@{
                    val receiveChannel = socket.openReadChannel()

                    if (token.isNotBlank()) {
                        sendChannel.writeExchangeEvent(AuthenticateEvent(required = true))
                        val actualToken = (receiveChannel.readExchangeEventSafe() as? AuthenticateEvent)?.token
                        if (token != actualToken) {
                            sendChannel.writeExchangeEvent(AuthenticateEvent(success = false))
                            return@inner
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
                    logger.info("Exception during handling connection from $remoteAddressDisplay :", it)
                }
                withContext(NonCancellable) {
                    channelMutex.withLock {
                        sendChannels -= sendChannel
                    }
                }
            }.onFailure {
                logger.info("Exception during handling connection from $remoteAddressDisplay :", it)
            }

            clientConnectionMutex.withLock {
                val currentCount = clientConnectionCount.getOrDefault(remoteAddress, 0)
                if (currentCount <= 1) {
                    clientConnectionCount.remove(remoteAddress)
                } else {
                    clientConnectionCount[remoteAddress] = currentCount - 1
                }
            }
            logger.info("A connection from $remoteAddressDisplay closed.")
        }
    }

    private suspend fun receiveRoutine(channel: ByteReadChannel) {
        while (isActive) {
            kotlin.runCatching {
                val event = channel.readExchangeEventSafe()
                logger.info("Received event: $event")

                if (event is StatusPingEvent) {
                    sendEvent(buildStatusEvent())
                    return@runCatching
                }

                if (event !is MessageEvent) {
                    return@runCatching
                }

                val formatted = kotlin.runCatching {
                    ChatExchangeConfig.receiveMessageFormat
                        .get()
                        .format(event.from)
                        .parseJsonToComponent()
                        .copy()
                        .append(event.content)
                }.getOrElse {
                    logger.warn("Failed to format message from receive message format. Using default.", it)
                    Component.literal("<${event.from}> ${event.content}")
                }
                logger.info(formatted.getStringWithLanguage(language))
                minecraftServer.playerList.players.forEach {
                    it.sendSystemMessage(formatted)
                }
            }.onFailure {
                if (channel.isClosedForRead) {
                    return
                }
                if (it is ExchangeDisconnectException) {
                    throw it
                }
                logger.error("Failed to receive message from a client.", it)
            }
        }
    }

    suspend fun sendEvent(event: ExchangeEvent) {
        var finalEvent = event
        if (finalEvent is MessageEvent) {
            finalEvent = finalEvent.copy(
                content = finalEvent.content.tryParseCICodeFileToData()
            )
        }

        logger.info("Sending event: $finalEvent")
        channelMutex.withLock {
            sendChannels.forEach {
                kotlin.runCatching {
                    it.writeExchangeEvent(finalEvent)
                }.onFailure {
                    logger.error("Failed to send message to a client.", it)
                }
            }
        }
    }

    fun componentToString(component: Component) = component.getStringWithLanguage(language)

    private fun buildStatusEvent(): StatusEvent {
        val players = minecraftServer.playerList
        return StatusEvent(
            version = minecraftServer.serverVersion,
            brand = minecraftServer.serverModName,
            playerNumber = players.playerCount,
            maxPlayerNumber = players.maxPlayers,
            playerNames = players.playerNamesArray.filterNotNull(),
        )
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

        val language: Language get() = instance?.language ?: Language.getInstance()

        fun componentToString(component: Component): String = instance?.componentToString(component) ?: component.string
    }
}

fun String.toExchangeServerTranslatedLiteral(vararg args: Any?): Component =
    toTranslatedLiteral(*args, language = ExchangeServer.language)