package xyz.holocons.mc.litematicaprotocol.paper;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import org.bukkit.entity.Player;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.HashMap;
import java.util.UUID;

public class SpongeSchematicSplit {
    private static final SpongeSchematicSplit INSTANCE = new SpongeSchematicSplit();

    private final HashMap<Player, SpongeSchematicData> splits = new HashMap<>();

    public static SpongeSchematicSplit getInstance() {
        return INSTANCE;
    }
    public Clipboard push(DataInputStream in, Player player, UUID uuid, int currentSplit, int totalSplits) throws IOException {
        SpongeSchematicData data = splits.get(player);
        if (data == null) {
            if (currentSplit != 1) {
                throw new IOException(String.format("Out of order: %d / %d. Expected 1 / any.", currentSplit, totalSplits));
            }

            //First part, makes sense
            if (totalSplits == 1) {
                //Finished. Compatibility in case there is really only one part.
                return BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(in).read();
            }

            //Populate hashMap initially
            data = new SpongeSchematicData();
            data.uuid = uuid;
            data.inputStream = new ByteArrayInputStream(in.readAllBytes());
            data.currentSplit = currentSplit;
            data.totalSplits = totalSplits;
            splits.put(player, data);
            return null;
        }

        if (data.uuid.equals(uuid) && data.currentSplit + 1 == currentSplit && data.totalSplits == totalSplits) {
            //Next part, makes sense

            final var currentReader = new SequenceInputStream(data.inputStream, new ByteArrayInputStream(in.readAllBytes()));

            if (currentSplit == totalSplits) {
                //Finished.

                /*
                //DEBUG - will break code
                try {
                    FileOutputStream outputStream = new FileOutputStream("schematic parts.litematic", false);
                    currentReader.transferTo(outputStream);
                } catch (Exception ignored) {
                }
                //DEBUG
                 */

                splits.remove(player);

                return BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(currentReader).read();
            }

            //Concatenate on hashMap
            data.inputStream = currentReader;
            data.currentSplit = currentSplit;
            return null;
        }

        //To reach here, something uncommon happened

        //UUID changed?
        if (!data.uuid.equals(uuid)) {
            //Change of UUID - latency too high and player pasted a different schematic or just spam?
            if (data.currentSplit != 1) {
                throw new IOException(String.format("Unexpected %d / %d (%s). Expected %d / %d (%s).",
                    currentSplit, totalSplits, uuid,
                    data.currentSplit + 1, data.totalSplits, data.uuid));
            }

            //First part
            if (totalSplits == 1) {
                //Finished. Compatibility in case there is really only one part.

                splits.remove(player);

                return BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(in).read();
            }
            //Replace previous data
            data.uuid = uuid;
            data.inputStream.close();
            data.inputStream = new ByteArrayInputStream(in.readAllBytes());
            data.currentSplit = currentSplit;
            data.totalSplits = totalSplits;
            return null;
        }

        throw new IOException(String.format("Out of order: %d / %d. Expected %d / %d.",
            currentSplit, totalSplits, data.currentSplit + 1, data.totalSplits));
    }
}
