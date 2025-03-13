package NoMathExpectation.chatExchange.neoForged

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

@EventBusSubscriber(modid = ChatExchange.ID)
object Events {
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
        ExchangeServer.sendMessage(
            Message(
                event.username,
                event.message.string
            )
        )
    }
}