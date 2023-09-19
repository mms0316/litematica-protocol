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
        try (final var in = new DataInputStream(new ByteArrayInputStream(message))) {
            final var type = in.readUTF();
            switch (type) {
                case Constants.SPONGE_V2_SCHEMATIC_PART -> receiveSchematicPart(player, in);
                default -> throw new IOException("Unrecognized message type");
            }
        } catch (Exception e) {
            getLogger().warning(e.getMessage());
            player.sendMessage("Failed to read schematic");
        }
    }

    private static void receiveSchematicPart(final Player player, final DataInputStream in) throws IOException {
        final var clipboard = hasSchematicPermission(player) ? ServerSchematic.getClipboard(player, in) : null;
        if (clipboard != null) {
            WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                    .setClipboard(new ClipboardHolder(clipboard));
            player.sendMessage("Schematic sent to clipboard");
        }
    }

    private static boolean hasSchematicPermission(final Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("worldedit.schematic.protocol");
    }
}
