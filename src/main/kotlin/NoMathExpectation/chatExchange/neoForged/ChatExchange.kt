package NoMathExpectation.chatExchange.neoForged

import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(ChatExchange.ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object ChatExchange {
    const val ID = "chatexchange"

    // the logger for our mod
    private val logger: Logger = LogManager.getLogger(ID)

    init {
        ChatExchangeConfig.register()
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        logger.log(Level.INFO, "Hello! This is working!")
    }
}

internal fun String.replaceModId() = replace("modid", ChatExchange.ID)
