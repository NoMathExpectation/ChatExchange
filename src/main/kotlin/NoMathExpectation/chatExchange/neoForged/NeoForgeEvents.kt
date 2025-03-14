package NoMathExpectation.chatExchange.neoForged

import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
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

    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.entity as? Player ?: return
        val cause = event.source

        val text = cause.getLocalizedDeathMessage(player).string
        ExchangeServer.sendEvent(
            PlayerDieEvent(player.name.string, text)
        )
    }

    @SubscribeEvent
    fun onAdvancementEarn(event: AdvancementEarnEvent) {
        val player = event.entity
        val advancement = event.advancement.value
        if (advancement.display.getOrNull()?.shouldAnnounceChat() != true) {
            return
        }

        val advancementName = advancement.display.getOrNull()?.title?.string ?: return
        ExchangeServer.sendEvent(
            PlayerAdvancementEvent(player.name.string, advancementName)
        )
    }
}