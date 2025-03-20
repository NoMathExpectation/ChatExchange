package NoMathExpectation.chatExchange.neoForged

import com.mojang.brigadier.arguments.BoolArgumentType
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
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
import kotlin.jvm.optionals.getOrNull

@EventBusSubscriber(modid = ChatExchange.ID)
object NeoForgeEvents {
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
        val data = event.player.server.chatExchangeData
        var message = event.message
        val string = ExchangeServer.componentToString(message)
        if ((!ChatExchangeConfig.chat.get() || data.isIgnoredPlayer(event.player.uuid)) && !(string.startsWith("@广播") || string.startsWith(
                "@broadcast"
            ) || string.startsWith("@bc"))
        ) {
            return
        }

        val contents = message.contents
        if (contents is PlainTextContents) {
            val text = contents.text()
            val newText = if (text.startsWith("@广播")) {
                text.removePrefix("@广播")
            } else if (text.startsWith("@broadcast")) {
                text.removePrefix("@broadcast")
            } else if (text.startsWith("@bc")) {
                text.removePrefix("@bc")
            } else {
                text
            }.trimStart()
            val newMessage = Component.literal(newText)
                .setStyle(message.style)
            newMessage.siblings += message.siblings
            message = newMessage
        }

        event.message = message

        ExchangeServer.sendEvent(
            MessageEvent(
                event.username,
                ExchangeServer.componentToString(message)
            )
        )
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
        if (!ChatExchangeConfig.joinLeave.get()) {
            return
        }

        val name = event.entity.name.string
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

        val name = event.entity.name.string
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
        val name = player.name.string
        if (ChatExchangeConfig.checkIgnoreBot(name)) {
            return
        }

        val cause = event.source
        val playerName = ExchangeServer.componentToString(player.name)
        val text = ExchangeServer.componentToString(cause.getLocalizedDeathMessage(player))
        ExchangeServer.sendEvent(
            PlayerDieEvent(playerName, text)
        )
    }

    @SubscribeEvent
    fun onAdvancementEarn(event: AdvancementEarnEvent) {
        if (!ChatExchangeConfig.advancement.get()) {
            return
        }

        val player = event.entity
        val name = player.name.string
        if (ChatExchangeConfig.checkIgnoreBot(name)) {
            return
        }

        val advancement = event.advancement.value
        if (advancement.display.getOrNull()?.shouldAnnounceChat() != true) {
            return
        }

        val advancementName =
            advancement.display.getOrNull()?.title?.let { ExchangeServer.componentToString(it) } ?: return
        val playerName = ExchangeServer.componentToString(player.name)
        ExchangeServer.sendEvent(
            PlayerAdvancementEvent(playerName, advancementName)
        )
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        if (event.commandSelection != Commands.CommandSelection.DEDICATED) {
            return
        }

        val dispatcher = event.dispatcher

        val command = Commands.literal("chatexchange")
            .then(
                Commands.literal("status").executes { context ->
                    val player = context.source.player ?: return@executes 0

                    fun Boolean.toProperLiteral() =
                        (if (this) "chatexchange.const.enabled" else "chatexchange.const.disabled").toTranslatableComponent()

                    player.sendSystemMessage(
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
                        val player = context.source.player ?: return@executes 0
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
                    val player = context.source.player ?: return@executes 0
                    val data = player.server.chatExchangeData
                    if (data.isIgnoredPlayer(player.uuid)) {
                        player.sendSystemMessage("chatexchange.command.chatexchange.broadcastme.isoff".toExchangeServerTranslatedLiteral())
                    } else {
                        player.sendSystemMessage("chatexchange.command.chatexchange.broadcastme.ison".toExchangeServerTranslatedLiteral())
                    }

                    1
                }
            ).executes { context ->
                val player = context.source.player ?: return@executes 0

                player.sendSystemMessage(
                    "chatexchange.command.chatexchange.description".toExchangeServerTranslatedLiteral()
                )
                1
            }
        dispatcher.register(command)
    }
}