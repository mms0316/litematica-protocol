package xyz.holocons.mc.litematicaprotocol.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;

public class SendSchematicPacketTask implements ITask {
    public int offset;
    public PacketByteBuf byteBuf;

    public SendSchematicPacketTask(PacketByteBuf byteBuf, int tickOffset) {
        this.byteBuf = byteBuf;
        this.offset = tickOffset;
    }

    @Override
    public int getTickOffset() {
        return offset;
    }

    @Override
    public void setTickOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public void run() {

        /*
        //DEBUG
        try {
            FileOutputStream outputStream = new FileOutputStream("schematic parts.hex", true);
            byteBuf.markReaderIndex();
            byteBuf.markWriterIndex();
            byteBuf.skipBytes(2 + 1 + 8 + 8 + 4 + 4);
            byteBuf.getBytes(byteBuf.readerIndex(), outputStream, byteBuf.readableBytes());
            byteBuf.resetReaderIndex();
            byteBuf.resetWriterIndex();
            outputStream.close();
        } catch (Exception ignored) {
        }
        //DEBUG
        */

        ClientPlayNetworking.send(LitematicaProtocolMod.CHANNEL_MAIN, byteBuf);
    }
}
