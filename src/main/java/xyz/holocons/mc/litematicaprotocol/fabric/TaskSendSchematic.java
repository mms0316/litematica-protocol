package xyz.holocons.mc.litematicaprotocol.fabric;

import java.io.IOException;

import fi.dy.masa.litematica.scheduler.tasks.TaskBase;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import xyz.holocons.mc.litematicaprotocol.Constants;

public class TaskSendSchematic extends TaskBase {

    private static final int MAX_PAYLOAD_SIZE = 32767;

    private final ClientSchematic schematic;

    public TaskSendSchematic(final ClientSchematic schematic) {
        this.finished = false;
        this.schematic = schematic;
    }

    @Override
    public boolean execute() {
        try {
            this.finished = sendPacket(schematic);
        } catch (IOException e) {
            mc.inGameHud.getChatHud().addMessage(Text.of(e.getMessage()));
            this.finished = true;
        }
        return finished;
    }

    private static boolean sendPacket(final ClientSchematic schematic) throws IOException {
        final var data = schematic.getData();
        if (data.readableBytes() > Constants.MAX_SCHEMATIC_SIZE) {
            throw new IOException("Schematic is too large");
        }
        final var message = new PacketByteBuf(Unpooled.buffer(MAX_PAYLOAD_SIZE, MAX_PAYLOAD_SIZE));
        try (final var out = new ByteBufOutputStream(message)) {
            out.writeUTF(Constants.SPONGE_V2_SCHEMATIC_PART);
            out.writeInt(data.readableBytes());
            data.readBytes(out, Math.min(data.readableBytes(), MAX_PAYLOAD_SIZE - out.writtenBytes()));
        }
        ClientPlayNetworking.send(new ClientSchematicPayload(message));
        return data.readableBytes() == 0;
    }
}
