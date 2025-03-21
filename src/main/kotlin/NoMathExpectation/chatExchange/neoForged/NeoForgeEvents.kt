package NoMathExpectation.chatExchange.neoForged

import NoMathExpectation.chatExchange.neoForged.NeoForgeEvents.registries
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands
import net.minecraft.commands.ParserUtils
import net.minecraft.core.HolderLookup
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import org.apache.logging.log4j.LogManager
import kotlin.jvm.optionals.getOrNull

@EventBusSubscriber(modid = ChatExchange.ID)
object NeoForgeEvents {
    private val logger = LogManager.getLogger(ChatExchange.ID)

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        ExchangeServer.startNewInstance(event.server)
    }

    @SubscribeEvent
    fun onServerStop(event: ServerStoppingEvent) {
        ExchangeServer.stopInstance()
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onServerChat(event: ServerChatEvent) {
        if (ChatExchangeConfig.mixinMode.get()) {
            return
        }

        val data = event.player.server.chatExchangeData
        var message = event.message
        val string = ExchangeServer.componentToString(message)
        if ((!ChatExchangeConfig.chat.get() || data.isIgnoredPlayer(event.player.uuid)) && !string.startsWithBroadcastPrefix()) {
            return
        }

        val contents = message.contents
        if (contents is PlainTextContents) {
            val text = contents.text()
            val newText = text.removeBroadcastPrefix()
            val newMessage = Component.literal(newText)
                .setStyle(message.style)
            newMessage.siblings += message.siblings
            message = newMessage
        }

        event.message = if (!ChatExchangeConfig.chat.get() || data.isIgnoredPlayer(event.player.uuid)) {
            Component.literal(ChatExchangeConfig.broadcastPrefix.get()).append(message)
        } else {
            message
        }

        ExchangeServer.sendEvent(
            MessageEvent(
                ExchangeServer.componentToString(event.player.name),
                ExchangeServer.componentToString(message),
            )
        )
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
        if (!ChatExchangeConfig.joinLeave.get()) {
            return
        }

        val name = ExchangeServer.componentToString(event.entity.name)
        if (ChatExchangeConfig.checkIgnoreBot(name)) {
            return
        }

        ExchangeServer.sendEvent(
            PlayerJoinEvent(name)
        )
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerLoggedOutEvent) {
        if (!ChatExchangeConfig.joinLeave.get()) {
            return
        }

        val name = ExchangeServer.componentToString(event.entity.name)
        if (ChatExchangeConfig.checkIgnoreBot(name)) {
            return
        }

        ExchangeServer.sendEvent(
            PlayerLeaveEvent(name)
        )
    }

    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        if (!ChatExchangeConfig.death.get()) {
            return
        }

        val player = event.entity as? Player ?: return
        val name = ExchangeServer.componentToString(player.name)
        if (ChatExchangeConfig.checkIgnoreBot(name)) {
            return
        }

        val cause = event.source
        val text = ExchangeServer.componentToString(cause.getLocalizedDeathMessage(player))
        ExchangeServer.sendEvent(
            PlayerDieEvent(name, text)
        )
    }

    @SubscribeEvent
    fun onAdvancementEarn(event: AdvancementEarnEvent) {
        if (!ChatExchangeConfig.advancement.get()) {
            return
        }

        val player = event.entity
        val name = ExchangeServer.componentToString(player.name)
        if (ChatExchangeConfig.checkIgnoreBot(name)) {
            return
        }

        val advancement = event.advancement.value
        if (advancement.display.getOrNull()?.shouldAnnounceChat() != true) {
            return
        }

        val advancementName =
            advancement.display.getOrNull()?.title?.let { ExchangeServer.componentToString(it) } ?: return
        ExchangeServer.sendEvent(
            PlayerAdvancementEvent(name, advancementName)
        )
    }

    internal lateinit var registries: HolderLookup.Provider
        private set

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val buildContext = event.buildContext
        registries = buildContext

        if (event.commandSelection != Commands.CommandSelection.DEDICATED) {
            return
        }

        val dispatcher = event.dispatcher

        val command = Commands.literal("chatexchange")
            .then(
                Commands.literal("send").then(
                    Commands.argument("message", StringArgumentType.greedyString()).executes { context ->
                        val message = StringArgumentType.getString(context, "message")

                        val name = ExchangeServer.componentToString(context.source.displayName)

                        val component = kotlin.runCatching {
                            val formatted = ChatExchangeConfig.commandBroadcastFormat
                                .get()
                                .format(
                                    name,
                                    message,
                                )
                            formatted.parseJsonToComponent()
                        }.getOrElse {
                            logger.error("Unable to resolve component from command broadcast format. Using default.", it)
                            context.source.sendSystemMessage("chatexchange.const.exception".toExchangeServerTranslatedLiteral())
                            Component.literal("<$name> $message")
                        }

                        logger.info(component.getStringWithLanguage(ExchangeServer.language))
                        ExchangeServer.sendEvent(
                            MessageEvent(name, message)
                        )
                        context.source.server.playerList.players.forEach {
                            it.sendSystemMessage(component)
                        }

                        1
                    }
                )
            ).then(
                Commands.literal("status").executes { context ->
                    fun Boolean.toProperLiteral() =
                        (if (this) "chatexchange.const.enabled" else "chatexchange.const.disabled").toTranslatableComponent()

                    context.source.sendSystemMessage(
                        "chatexchange.command.chatexchange.status".toExchangeServerTranslatedLiteral(
                            ChatExchangeConfig.chat.get().toProperLiteral(),
                            ChatExchangeConfig.joinLeave.get().toProperLiteral(),
                            ChatExchangeConfig.death.get().toProperLiteral(),
                            ChatExchangeConfig.advancement.get().toProperLiteral()
                        )
                    )

                    1
                }
            ).then(
                Commands.literal("broadcastme").then(
                    Commands.argument("toggle", BoolArgumentType.bool()).executes { context ->
                        val player = context.source.player ?: kotlin.run {
                            context.source.sendSystemMessage("chatexchange.const.onlyPlayer".toExchangeServerTranslatedLiteral())
                            return@executes 0
                        }

                        val data = player.server.chatExchangeData
                        val toggle = BoolArgumentType.getBool(context, "toggle")
                        if (toggle) {
                            data.removeIgnoredPlayer(player.uuid)
                            player.sendSystemMessage("chatexchange.command.chatexchange.broadcastme.on".toExchangeServerTranslatedLiteral())
                        } else {
                            data.addIgnoredPlayer(player.uuid)
                            player.sendSystemMessage("chatexchange.command.chatexchange.broadcastme.off".toExchangeServerTranslatedLiteral())
                        }

                        1
                    }
                ).executes { context ->
                    val player = context.source.player ?: kotlin.run {
                        context.source.sendSystemMessage("chatexchange.const.onlyPlayer".toExchangeServerTranslatedLiteral())
                        return@executes 0
                    }

                    val data = player.server.chatExchangeData
                    if (data.isIgnoredPlayer(player.uuid)) {
                        player.sendSystemMessage("chatexchange.command.chatexchange.broadcastme.isoff".toExchangeServerTranslatedLiteral())
                    } else {
                        player.sendSystemMessage("chatexchange.command.chatexchange.broadcastme.ison".toExchangeServerTranslatedLiteral())
                    }

                    1
                }
            ).executes { context ->
                context.source.sendSystemMessage(
                    "chatexchange.command.chatexchange.description".toExchangeServerTranslatedLiteral(
                        ChatExchangeConfig.broadcastTriggerPrefix.get().joinToString("/")
                    )
                )
                1
            }
        dispatcher.register(command)
    }
}

fun String.parseJsonToComponent(): Component {
    return ParserUtils.parseJson(registries, StringReader(this), ComponentSerialization.CODEC)
}