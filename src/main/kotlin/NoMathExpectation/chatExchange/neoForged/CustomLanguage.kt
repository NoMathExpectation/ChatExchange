package NoMathExpectation.chatExchange.neoForged

import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.StringDecomposer
import net.neoforged.fml.i18n.I18nManager
import org.apache.logging.log4j.LogManager
import java.util.*

class CustomLanguage(
    private val textMap: Map<String, String>,
    private val componentMap: Map<String, Component>,
    private val defaultRightToLeft: Boolean = false,
) : Language() {
    override fun getOrDefault(key: String, defaultValue: String) = textMap.getOrDefault(key, defaultValue)

    override fun has(id: String) = id in textMap

    override fun isDefaultRightToLeft() = defaultRightToLeft

    override fun getVisualOrder(text: FormattedText) = FormattedCharSequence {
        text.visit(
            { style, string ->
                if (StringDecomposer.iterateFormatted(
                        string,
                        style,
                        it
                    )
                ) Optional.empty() else FormattedText.STOP_ITERATION
            },
            Style.EMPTY
        ).isPresent
    }

    override fun getLanguageData() = textMap

    override fun getComponent(key: String) = componentMap[key]
}

private val logger = LogManager.getLogger(ChatExchange.ID)

fun languageOf(lang: String, server: MinecraftServer): Language {
    val textMap = mutableMapOf<String, String>()
    val componentMap = mutableMapOf<String, Component>()

    fun loadFrom(path: String) {
        CustomLanguage::class.java.getResourceAsStream(path)?.use {
            Language.loadFromJson(it, textMap::put, componentMap::put)
        } ?: logger.warn("Unable to load language file $path")
    }

    loadFrom("/assets/chatexchange/mclang/$lang.json") // vanilla
    loadFrom("/assets/chatexchange/fmllang/$lang.json") // fml
    loadFrom("/assets/chatexchange/neolang/$lang.json") // neoforge

    textMap += I18nManager.loadTranslations(lang) // i18n

    // mods
    val langFile = String.format(Locale.ROOT, "lang/%s.json", lang)
    val resourceManager = server.serverResources.resourceManager
    val clientResources = MultiPackResourceManager(PackType.CLIENT_RESOURCES, resourceManager.listPacks().toList())
    val loaded = clientResources.namespaces.map { namespace ->
        runCatching {
            val langResource = ResourceLocation.fromNamespaceAndPath(namespace, langFile)
            clientResources.getResourceStack(langResource).forEach { resource ->
                resource.open().use {
                    Language.loadFromJson(it, textMap::put, componentMap::put)
                }
            }
        }.onFailure {
            logger.warn("Skipped language file: {}:{}", namespace, langFile, it)
            return@map 0
        }
        1
    }.sum()
    logger.debug("Loaded {} mod language files for {}", loaded, lang)

    return CustomLanguage(textMap, componentMap)
}

fun languageOfOrDefault(lang: String, server: MinecraftServer): Language = runCatching {
    languageOf(lang, server)
}.getOrElse {
    logger.error("Failed to load language: $lang", it)
    Language.getInstance()
}

fun Component.getStringWithLanguage(language: Language): String {
    val current = Language.getInstance()
    Language.inject(language)
    val result = string
    Language.inject(current)
    return result
}

fun Component.toLiteral(language: Language = Language.getInstance()): Component =
    Component.literal(getStringWithLanguage(language))

fun String.toTranslatableComponent(vararg args: Any?): MutableComponent = Component.translatable(this, *args)

fun String.toTranslatedLiteral(vararg args: Any?, language: Language = Language.getInstance()): Component =
    Component.translatable(this, *args).toLiteral(language)