package NoMathExpectation.chatExchange.neoForged

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.ModConfigSpec
import thedarkcolour.kotlinforforge.neoforge.forge.LOADING_CONTEXT
import thedarkcolour.kotlinforforge.neoforge.forge.runWhenOn

@EventBusSubscriber(
    modid = ChatExchange.ID,
    bus = EventBusSubscriber.Bus.MOD
)
object ChatExchangeConfig {
    private val builder = ModConfigSpec.Builder()

    val host: ModConfigSpec.ConfigValue<String> = builder.comment("The host to bind the exchange server to.")
        .translation("modid.config.host")
        .worldRestart()
        .define("host", "0.0.0.0")
    val port: ModConfigSpec.IntValue = builder.comment("The port to bind the exchange server to.")
        .translation("modid.config.port")
        .worldRestart()
        .defineInRange("port", 9002, 0, 65535)
    val token: ModConfigSpec.ConfigValue<String> =
        builder.comment("The token to authenticate with the exchange server.", "Leave blank to disable authentication.")
            .translation("modid.config.token")
            .worldRestart()
            .define("token", "")

    val spec = builder.build()

    private var registered = false
    internal fun register() {
        if (registered) {
            error("Config is already registered!")
        }

        val modContainer = LOADING_CONTEXT.activeContainer
        modContainer.registerConfig(ModConfig.Type.COMMON, spec)
        runWhenOn(Dist.CLIENT) {
            registerOnClient()
        }

        registered = true
    }

    @SubscribeEvent
    fun onConfig(event: ModConfigEvent) {
    }
}