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

    private static final int MAX_PAYLOAD_LENGTH = 32767;

    private final Schematic schematic;

    public TaskSendSchematic(final Schematic schematic) {
        this.finished = false;
        this.schematic = schematic;
    }

    @Override
    public boolean execute() {
        try {
            final var payload = schematic.getPayload();
            sendPacket(payload);
        } catch (IOException e) {
            this.mc.inGameHud.getChatHud().addMessage(Text.of(e.getMessage()));
        }
        this.finished = true;
        return this.finished;
    }

    private static void sendPacket(final PacketByteBuf payload) throws IOException {
        final var message = new PacketByteBuf(Unpooled.buffer(4096));
        try (final var out = new ByteBufOutputStream(message)) {
            out.writeUTF(Constants.SPONGE_SCHEMATIC);
            if (out.writtenBytes() + payload.readableBytes() > MAX_PAYLOAD_LENGTH) {
                throw new IOException("Schematic is too large");
            }
            out.write(payload.getWrittenBytes());
        }
        ClientPlayNetworking.send(LitematicaProtocolMod.CHANNEL_MAIN, message);
    }
}
