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
    val language: ModConfigSpec.ConfigValue<String> = builder.comment("The language the exchange server messages will be.", "Leave blank to use the language the game is using.")
        .translation("modid.config.language")
        .worldRestart()
        .define("language", "")

    val mixinMode: ModConfigSpec.BooleanValue = builder.comment("Whether to use mixin instead of event to listen to server chats.", "If the exchange server isn't sending server chat, try turn this on.")
        .translation("modid.config.mixinMode")
        .define("mixinMode", false)

    val ignoreBotRegex: ModConfigSpec.ConfigValue<String> = builder.comment("The regex to match and ignore the bot players.", "Leave blank to disable.")
        .translation("modid.config.ignoreBotRegex")
        .define("ignoreBotRegex", "") {
            kotlin.runCatching {
                val str = it as String
                if (str.isBlank()) {
                    return@runCatching true
                }

                it.toRegex()
                true
            }.getOrDefault(false)
        }
    val chat: ModConfigSpec.BooleanValue = builder.comment("Whether to broadcast player chatting.", "Players can also broadcast their message by prefixing @broadcast.")
        .translation("modid.config.chat")
        .define("chat", true)
    val joinLeave: ModConfigSpec.BooleanValue = builder.comment("Whether to broadcast player joining and leaving.")
        .translation("modid.config.joinLeave")
        .define("joinLeave", true)
    val death: ModConfigSpec.BooleanValue = builder.comment("Whether to broadcast player deaths.")
        .translation("modid.config.death")
        .define("death", true)
    val advancement: ModConfigSpec.BooleanValue = builder.comment("Whether to broadcast player advancements.")
        .translation("modid.config.advancement")
        .define("advancement", true)

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

    fun checkIgnoreBot(name: String): Boolean {
        val regexStr = ignoreBotRegex.get()
        if (regexStr.isBlank()) {
            return false
        }

        val regex = regexStr.toRegex()
        return regex.matches(name)
    }
}