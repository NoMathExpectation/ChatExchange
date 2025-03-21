package NoMathExpectation.chatExchange.neoForged.mixin;

import NoMathExpectation.chatExchange.neoForged.*;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements ServerGamePacketListener {
    @Shadow
    public ServerPlayer player;

    @ModifyVariable(
            method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private ServerboundChatPacket onHandleChat(ServerboundChatPacket packet) {
        if (!ChatExchangeConfig.INSTANCE.getMixinMode().get()) {
            return packet;
        }

        var data = ChatExchangeDataKt.getChatExchangeData(player.server);
        var string = packet.message();
        if ((!ChatExchangeConfig.INSTANCE.getChat().get() || data.isIgnoredPlayer(player.getUUID())) && !ChatExchangeConfigKt.startsWithBroadcastPrefix(string)) {
            return packet;
        }

        var newString = ChatExchangeConfigKt.removeBroadcastPrefix(string);
        ExchangeServer.Companion.sendEvent(
                new MessageEvent(
                        ExchangeServer.Companion.componentToString(player.getName()),
                        newString
                )
        );

        if (!ChatExchangeConfig.INSTANCE.getChat().get() || data.isIgnoredPlayer(player.getUUID())) {
            newString = ChatExchangeConfig.INSTANCE.getBroadcastPrefix().get() + newString;
        }
        return new ServerboundChatPacket(newString, packet.timeStamp(), packet.salt(), packet.signature(), packet.lastSeenMessages());
    }
}
