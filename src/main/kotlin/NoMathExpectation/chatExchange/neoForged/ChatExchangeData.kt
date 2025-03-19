package NoMathExpectation.chatExchange.neoForged

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.*

class ChatExchangeData : SavedData() {
    private val ignoredPlayers: MutableSet<UUID> = mutableSetOf()

    fun addIgnoredPlayer(player: UUID) {
        ignoredPlayers += player
        setDirty()
    }

    fun removeIgnoredPlayer(player: UUID) {
        ignoredPlayers -= player
        setDirty()
    }

    fun isIgnoredPlayer(player: UUID): Boolean {
        return player in ignoredPlayers
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val listTag = ListTag()
        ignoredPlayers.forEach {
            listTag += StringTag.valueOf(it.toString())
        }
        tag.put(IGNORED_PLAYERS_KEY, listTag)
        return tag
    }

    companion object {
        const val DATA_STORAGE_KEY = "ChatExchangeData"

        const val IGNORED_PLAYERS_KEY = "IgnoredPlayers"

        private fun load(tag: CompoundTag, lookupProvider: HolderLookup.Provider): ChatExchangeData {
            val data = ChatExchangeData()
            tag.getList(IGNORED_PLAYERS_KEY, Tag.TAG_STRING.toInt()).forEach {
                data.ignoredPlayers += UUID.fromString(it.asString)
            }
            return data
        }

        val factory = Factory(::ChatExchangeData, ::load)
    }
}

val MinecraftServer.chatExchangeData: ChatExchangeData
    get() = overworld().dataStorage.computeIfAbsent(
        ChatExchangeData.factory,
        ChatExchangeData.DATA_STORAGE_KEY,
    )