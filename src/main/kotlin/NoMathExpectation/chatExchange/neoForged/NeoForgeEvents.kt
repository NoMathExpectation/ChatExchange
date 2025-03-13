package NoMathExpectation.chatExchange.neoForged

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

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

    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {
        ExchangeServer.sendEvent(
            MessageEvent(
                event.username,
                event.message.string
            )
        )
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
        ExchangeServer.sendEvent(
            PlayerJoinEvent(event.entity.name.string)
        )
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerLoggedOutEvent) {
        ExchangeServer.sendEvent(
            PlayerLeaveEvent(event.entity.name.string)
        )
    }
}