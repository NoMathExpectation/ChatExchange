package NoMathExpectation.chatExchange.neoForged

import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.config.ModConfigEvent
import thedarkcolour.kotlinforforge.forge.LOADING_CONTEXT
import thedarkcolour.kotlinforforge.forge.runWhenOn

@Mod.EventBusSubscriber(
    modid = ChatExchange.ID,
    bus = Mod.EventBusSubscriber.Bus.MOD
)
object ChatExchangeConfig {
    private val builder = ForgeConfigSpec.Builder()

    val host: ForgeConfigSpec.ConfigValue<String> = builder.comment("The host to bind the exchange server to.")
        .translation("modid.config.host")
        .worldRestart()
        .define("host", "0.0.0.0")
    val port: ForgeConfigSpec.IntValue = builder.comment("The port to bind the exchange server to.")
        .translation("modid.config.port")
        .worldRestart()
        .defineInRange("port", 9002, 0, 65535)
    val token: ForgeConfigSpec.ConfigValue<String> =
        builder.comment("The token to authenticate with the exchange server.", "Leave blank to disable authentication.")
            .translation("modid.config.token")
            .worldRestart()
            .define("token", "")
    val language: ForgeConfigSpec.ConfigValue<String> = builder.comment(
        "The language the exchange server messages will be.",
        "Leave blank to use the language the game is using."
    )
        .translation("modid.config.language")
        .worldRestart()
        .define("language", "")

    val mixinMode: ForgeConfigSpec.BooleanValue = builder.comment(
        "Whether to use mixin instead of event to listen to server chats.",
        "If the exchange server isn't sending server chat, try turn this on."
    )
        .translation("modid.config.mixinMode")
        .define("mixinMode", false)

    val ignoreBotRegex: ForgeConfigSpec.ConfigValue<String> =
        builder.comment("The regex to match and ignore the bot players.", "Leave blank to disable.")
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
    val chat: ForgeConfigSpec.BooleanValue = builder.comment(
        "Whether to broadcast player chatting.",
        "Players can also broadcast their message by prefixing @broadcast."
    )
        .translation("modid.config.chat")
        .define("chat", true)
    val joinLeave: ForgeConfigSpec.BooleanValue = builder.comment("Whether to broadcast player joining and leaving.")
        .translation("modid.config.joinLeave")
        .define("joinLeave", true)
    val death: ForgeConfigSpec.BooleanValue = builder.comment("Whether to broadcast player deaths.")
        .translation("modid.config.death")
        .define("death", true)
    val advancement: ForgeConfigSpec.BooleanValue = builder.comment("Whether to broadcast player advancements.")
        .translation("modid.config.advancement")
        .define("advancement", true)

    val broadcastTriggerPrefix: ForgeConfigSpec.ConfigValue<MutableList<out String>> =
        builder.comment("The prefix to recognize to trigger broadcast in chat message.")
            .translation("modid.config.broadcastTriggerPrefix")
            .defineListAllowEmpty(
                "broadcastTriggerPrefix",
                { mutableListOf("@广播", "@bc", "@broadcast") },
                { true }
            )
    val broadcastPrefix: ForgeConfigSpec.ConfigValue<String> =
        builder.comment("The prefix to prepend when displaying manually broadcast chat message.")
            .translation("modid.config.broadcastPrefix")
            .define("broadcastPrefix", "")
    val commandBroadcastFormat: ForgeConfigSpec.ConfigValue<String> = builder.comment(
        "The message format when player broadcast message using the command.",
        "Will not prepend broadcast prefix."
    )
        .translation("modid.config.commandBroadcastFormat")
        .define("commandBroadcastFormat", "\"<%s> %s\"") {
            (it as? String)?.parseJsonToComponent() != null
        }
    val receiveMessageFormat: ForgeConfigSpec.ConfigValue<String> =
        builder.comment("The message format when receiving message from outside.")
            .translation("modid.config.receiveMessageFormat")
            .define("receiveMessageFormat", "\"<%s> %s\"") {
                (it as? String)?.parseJsonToComponent() != null
            }

    val spec: ForgeConfigSpec = builder.build()

    private var registered = false
    internal fun register() {
        if (registered) {
            error("Config is already registered!")
        }

        LOADING_CONTEXT.registerConfig(ModConfig.Type.COMMON, spec)
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

fun String.startsWithBroadcastPrefix() =
    ChatExchangeConfig.broadcastTriggerPrefix
        .get()
        .any { startsWith(it) }

fun String.removeBroadcastPrefix() = run {
    ChatExchangeConfig.broadcastTriggerPrefix
        .get()
        .forEach {
            if (startsWith(it)) {
                return@run removePrefix(it)
            }
        }
    this
}.trimStart()