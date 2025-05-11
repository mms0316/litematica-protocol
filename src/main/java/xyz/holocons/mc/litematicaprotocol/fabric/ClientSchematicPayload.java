package xyz.holocons.mc.litematicaprotocol.fabric;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import xyz.holocons.mc.litematicaprotocol.Constants;

public record ClientSchematicPayload(ByteBuf buf) implements CustomPayload {
    public static final CustomPayload.Id<ClientSchematicPayload> PACKET_ID = new CustomPayload.Id<>(Identifier.of(Constants.NAMESPACE, Constants.MAIN));
    public static final PacketCodec<RegistryByteBuf, ClientSchematicPayload> PACKET_CODEC = PacketCodec.of(ClientSchematicPayload::write, ClientSchematicPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public void write(RegistryByteBuf buf) {
        buf.writeBytes(this.buf);
    }
}
