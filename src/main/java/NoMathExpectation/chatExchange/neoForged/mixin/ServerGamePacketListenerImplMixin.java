package NoMathExpectation.chatExchange.neoForged.mixin;

import NoMathExpectation.chatExchange.neoForged.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements ServerGamePacketListener {
    @Shadow
    public ServerPlayer player;

    @Unique
    private static final Logger chatExchange$LOGGER = LogManager.getLogger();

    @Inject(
            method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onHandleChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (!ChatExchangeConfig.INSTANCE.getMixinMode().get()) {
            return;
        }

        var data = ChatExchangeDataKt.getChatExchangeData(player.server);
        var string = packet.message();
        if ((!ChatExchangeConfig.INSTANCE.getChat().get() || data.isIgnoredPlayer(player.getUUID())) && !ChatExchangeConfigKt.startsWithBroadcastPrefix(string)) {
            return;
        }

        ci.cancel();

        var newString = ChatExchangeConfigKt.removeBroadcastPrefix(string);
        var playerName = ExchangeServer.Companion.componentToString(player.getName());
        ExchangeServer.Companion.sendEvent(
                new MessageEvent(
                        playerName,
                        newString
                )
        );

        Component component;
        try {
            var prefix = ChatExchangeConfig.INSTANCE
                    .getCommandBroadcastFormat()
                    .get()
                    .formatted(playerName);
            component = NeoForgeEventsKt.parseJsonToComponent(prefix);
        } catch (Exception e) {
            chatExchange$LOGGER.warn("Failed to format message from command broadcast format. Using default.", e);
            component = Component.literal("<%s>".formatted(playerName));
        }
        component = component.copy().append(newString);
        player.server.getPlayerList().broadcastSystemMessage(component, false);
    }
}
