package xyz.holocons.mc.litematicaprotocol.paper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import xyz.holocons.mc.litematicaprotocol.Constants;

public class ServerSchematic {

    private static final Object2ObjectArrayMap<UUID, ServerSchematic> schematics = new Object2ObjectArrayMap<>();

    private final byte[] data;
    private int remainingBytes;

    private ServerSchematic(final int capacity) {
        this.data = new byte[capacity];
        this.remainingBytes = capacity;
    }

    public static Clipboard getClipboard(final Player player, final DataInputStream in) throws IOException {
        final var remainingBytes = in.readInt();
        if (remainingBytes > Constants.MAX_SCHEMATIC_SIZE) {
            player.kick();
            return null;
        }
        final var schematic = get(player, remainingBytes);
        final var offset = schematic.data.length - schematic.remainingBytes;
        schematic.remainingBytes -= in.readNBytes(schematic.data, offset, schematic.remainingBytes);
        if (schematic.remainingBytes > 0) {
            return null;
        }
        release(player);
        return createClipboard(schematic);
    }

    private static ServerSchematic get(final Player player, final int remainingBytes) {
        final var id = player.getUniqueId();
        if (schematics.containsKey(id)) {
            final var schematic = schematics.get(id);
            if (schematic.remainingBytes == remainingBytes) {
                return schematic;
            }
        }
        final var schematic = new ServerSchematic(remainingBytes);
        schematics.put(id, schematic);
        return schematic;
    }

    private static void release(final Player player) {
        final var id = player.getUniqueId();
        schematics.remove(id);
    }

    private static Clipboard createClipboard(final ServerSchematic schematic) throws IOException {
        final Clipboard clipboard;
        try (final var in = new ByteArrayInputStream(schematic.data)) {
            clipboard = BuiltInClipboardFormat.SPONGE_V2_SCHEMATIC.getReader(in).read();
        }
        return clipboard;
    }
}
