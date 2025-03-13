package NoMathExpectation.chatExchange.neoForged

import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import thedarkcolour.kotlinforforge.neoforge.forge.LOADING_CONTEXT

internal fun ChatExchangeConfig.registerOnClient() {
    val modContainer = LOADING_CONTEXT.activeContainer
    val supplier = {
        IConfigScreenFactory { container, screen ->
            ConfigurationScreen(container, screen)
        }
    }
    modContainer.registerExtensionPoint(IConfigScreenFactory::class.java, supplier)
}