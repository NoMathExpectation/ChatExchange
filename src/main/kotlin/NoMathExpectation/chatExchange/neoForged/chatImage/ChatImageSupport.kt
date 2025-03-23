package NoMathExpectation.chatExchange.neoForged.chatImage

import net.neoforged.fml.ModList

internal val chatImageLoaded
    get() = ModList.get().isLoaded("chatimage")

suspend fun String.tryParseCICodeFileToData(): String {
    if (!chatImageLoaded) {
        return this
    }
    return tryParseCICodeFileToData0(this)
}