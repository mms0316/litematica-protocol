package xyz.holocons.mc.litematicaprotocol.fabric;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.Identifier;
import xyz.holocons.mc.litematicaprotocol.Constants;

public final class LitematicaProtocolMod implements ClientModInitializer {

    public static final Identifier CHANNEL_MAIN = new Identifier(Constants.NAMESPACE, Constants.MAIN);

    private static boolean isProtocolAvailable = false;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register(this::onPlayReady);
        C2SPlayChannelEvents.REGISTER.register(this::onChannelRegister);
    }

    public static boolean isProtocolAvailable() {
        return isProtocolAvailable;
    }

    public void onPlayReady(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        isProtocolAvailable = false;
    }

    public void onChannelRegister(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client,
            List<Identifier> channels) {
        if (channels.contains(CHANNEL_MAIN)) {
            isProtocolAvailable = true;
        }
    }
}
