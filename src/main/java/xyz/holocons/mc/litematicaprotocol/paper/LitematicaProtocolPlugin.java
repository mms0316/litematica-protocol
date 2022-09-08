package xyz.holocons.mc.litematicaprotocol.paper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.session.ClipboardHolder;

import xyz.holocons.mc.litematicaprotocol.Constants;

public final class LitematicaProtocolPlugin extends JavaPlugin implements PluginMessageListener {

    public static final String CHANNEL_MAIN = new NamespacedKey(Constants.NAMESPACE, Constants.MAIN).toString();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL_MAIN, this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL_MAIN)) {
            return;
        }
        final Clipboard clipboard;
        try (final var in = new DataInputStream(new ByteArrayInputStream(message))) {
            final var type = in.readUTF();
            clipboard = switch (type) {
                case Constants.SPONGE_SCHEMATIC ->
                    player.getGameMode() == GameMode.CREATIVE && player.hasPermission("worldedit.clipboard.protocol")
                            ? BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(in).read()
                            : null;
                default -> throw new IOException("Unrecognized message type");
            };
        } catch (Exception e) {
            getLogger().warning(e.getMessage());
            player.sendMessage("Failed to read schematic");
            return;
        }
        if (clipboard == null) {
            return;
        }
        WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                .setClipboard(new ClipboardHolder(clipboard));
        player.sendMessage("Schematic sent to clipboard");
    }
}
