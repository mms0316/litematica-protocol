package xyz.holocons.mc.litematicaprotocol.fabric.mixin;

import java.io.IOException;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

import io.netty.buffer.ByteBufInputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.EntityUtils;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import xyz.holocons.mc.litematicaprotocol.Constants;
import xyz.holocons.mc.litematicaprotocol.fabric.ITask;
import xyz.holocons.mc.litematicaprotocol.fabric.LitematicaProtocolMod;
import xyz.holocons.mc.litematicaprotocol.fabric.SendSchematicPacketTask;
import xyz.holocons.mc.litematicaprotocol.fabric.TaskManager;

@Mixin(value = SchematicPlacementManager.class, remap = false)
abstract class SchematicPlacementManagerMixin {

    private static final int MAX_PAYLOAD_LENGTH = 32767;

    public static final class BlockStateFormatter {

        private final BlockState blockState;

        public BlockStateFormatter(BlockState blockState) {
            this.blockState = blockState != null ? blockState : LitematicaBlockStateContainer.AIR_BLOCK_STATE;
        }

        @Override
        public String toString() {
            final var propertyJoiner = new StringJoiner(",", "[", "]").setEmptyValue("");
            blockState.getEntries().forEach((property, comparable) -> propertyJoiner
                    .add(property.getName() + '=' + String.valueOf(comparable).toLowerCase()));
            return Registries.BLOCK.getId(blockState.getBlock()).toString() + propertyJoiner.toString();
        }
    }

    @Inject(method = "pastePlacementToWorld", at = @At("HEAD"), cancellable = true)
    private void injectProtocol(final SchematicPlacement placement, MinecraftClient client, CallbackInfo info) {
        if (!LitematicaProtocolMod.isProtocolAvailable() || client.isIntegratedServerRunning()) {
            return;
        }
        info.cancel();
        if (client.player == null || !EntityUtils.isCreativeMode(client.player) || placement == null) {
            return;
        }
        final var regionName = placement.getSubRegionCount() == 1
                ? placement.getAllSubRegionsPlacements().iterator().next().getName()
                : placement.getSelectedSubRegionName();
        final var schematic = convertSchematic(placement.getSchematic(), regionName);
        try {
            sendSchematic(schematic);
        } catch (IOException e) {
            client.inGameHud.getChatHud().addMessage(Text.of(e.getMessage()));
        }
    }

    // https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md
    private static NbtCompound convertSchematic(final LitematicaSchematic schematic, final String regionName) {
        if (schematic == null || regionName == null || schematic.getSubRegionContainer(regionName) == null) {
            return null;
        }
        final var nbt = new NbtCompound();
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
        final var size = schematic.getAreaSize(regionName);
        nbt.putShort("Width", (short) Math.abs(size.getX()));
        nbt.putShort("Height", (short) Math.abs(size.getY()));
        nbt.putShort("Length", (short) Math.abs(size.getZ()));
        nbt.put("Palette", getPalette(schematic, regionName));
        nbt.put("BlockData", getBlockData(schematic, regionName));
        final var blockEntities = getBlockEntities(schematic, regionName);
        if (!blockEntities.isEmpty()) {
            nbt.put("BlockEntities", blockEntities);
        }
        final var entities = getEntities(schematic, regionName);
        if (!entities.isEmpty()) {
            nbt.put("Entities", entities);
        }
        return nbt;
    }

    private static NbtCompound getPalette(final LitematicaSchematic schematic, final String regionName) {
        final var nbt = new NbtCompound();
        final var palette = schematic.getSubRegionContainer(regionName).getPalette();
        for (int i = 0; i < palette.getPaletteSize(); i++) {
            nbt.putInt(new BlockStateFormatter(palette.getBlockState(i)).toString(), i);
        }
        return nbt;
    }

    private static NbtByteArray getBlockData(final LitematicaSchematic schematic, final String regionName) {
        final var blockData = new PacketByteBuf(Unpooled.buffer());
        final var blockStateContainer = schematic.getSubRegionContainer(regionName);
        for (int y = 0; y < Math.abs(blockStateContainer.getSize().getY()); y++) {
            for (int z = 0; z < Math.abs(blockStateContainer.getSize().getZ()); z++) {
                for (int x = 0; x < Math.abs(blockStateContainer.getSize().getX()); x++) {
                    blockData.writeVarInt(blockStateContainer.getPalette().idFor(blockStateContainer.get(x, y, z)));
                }
            }
        }
        return new NbtByteArray(blockData.array());
    }

    private static NbtList getBlockEntities(final LitematicaSchematic schematic, final String regionName) {
        return schematic.getBlockEntityMapForRegion(regionName).values().stream()
                .map(blockEntity -> {
                    final var nbt = blockEntity.copy();
                    final var position = new int[] {
                            nbt.getInt("x"),
                            nbt.getInt("y"),
                            nbt.getInt("z")
                    };
                    final var id = nbt.getString("id");
                    nbt.remove("x");
                    nbt.remove("y");
                    nbt.remove("z");
                    nbt.remove("id");
                    nbt.putIntArray("Pos", position);
                    nbt.putString("Id", id);
                    return nbt;
                })
                .collect(Collectors.toCollection(NbtList::new));
    }

    private static NbtList getEntities(final LitematicaSchematic schematic, final String regionName) {
        final var positionOffset = schematic.getSubRegionPosition(regionName);
        return schematic.getEntityListForRegion(regionName).stream()
                .map(entity -> {
                    final var nbt = entity.nbt.copy();
                    final var position = DoubleList.of(
                            entity.posVec.x + positionOffset.getX(),
                            entity.posVec.y + positionOffset.getY(),
                            entity.posVec.z + positionOffset.getZ())
                            .doubleStream()
                            .mapToObj(NbtDouble::of)
                            .collect(Collectors.toCollection(NbtList::new));
                    final var id = nbt.getString("id");
                    nbt.remove("id");
                    nbt.put("Pos", position);
                    nbt.putString("Id", id);
                    return nbt;
                })
                .collect(Collectors.toCollection(NbtList::new));
    }

    private static void sendSchematic(final NbtCompound nbt) throws IOException {
        if (nbt == null || nbt.isEmpty()) {
            throw new IOException("No schematic is selected");
        }
        final var message = new PacketByteBuf(Unpooled.buffer(4096));
        final var out = new ByteBufOutputStream(message);
        out.writeUTF(Constants.SPONGE_SCHEMATIC);
        NbtIo.writeCompressed(nbt, out);
        int totalPayload = out.writtenBytes();
        out.close();

        if (totalPayload <= MAX_PAYLOAD_LENGTH) {
            // Best effort - One packet is enough
            ClientPlayNetworking.send(LitematicaProtocolMod.CHANNEL_MAIN, message);
        } else {
            // Need to split into several packets

            // Strip header (Constants.SPONGE_SCHEMATIC)
            final var in = new ByteBufInputStream(message);
            in.readUTF();
            totalPayload = in.available();

            final int splitHeaderSize =
                2 + 1 + //Constants.SPONGE_SCHEMATIC_SPLIT (2 bytes for length, 1 for 0x00-0x7F string values)
                8 + 8 + //UUID
                4 + 4;  //Current part and total parts

            // Generate UUID.
            // If two packets with different UUIDs from a same player arrive at the server, the more recent one will be used.
            final var uuid = UUID.randomUUID();

            final int splitPayloadMaxSize = MAX_PAYLOAD_LENGTH - splitHeaderSize;
            final int splitLastPayloadSize = totalPayload % splitPayloadMaxSize;
            final int splitQtty = totalPayload / splitPayloadMaxSize + (splitLastPayloadSize != 0 ? 1 : 0);

            // To reduce spam and not get kicked by Paper
            int tickOffset = TaskManager.getInstance().getTopTickOffset();
            final int tickIncrement = 1; //TODO: configuration to set amount of ticks to wait between packets

            for (int splitPart = 0; splitPart < splitQtty; splitPart++) {
                final int bufferCapacity = splitPart < splitQtty - 1 ? MAX_PAYLOAD_LENGTH : (splitLastPayloadSize + splitHeaderSize);

                final var splitMessage = new PacketByteBuf(Unpooled.buffer(bufferCapacity));
                final var splitOut = new ByteBufOutputStream(splitMessage);

                splitOut.writeUTF(Constants.SPONGE_SCHEMATIC_SPLIT);
                splitOut.writeLong(uuid.getMostSignificantBits());
                splitOut.writeLong(uuid.getLeastSignificantBits());
                splitOut.writeInt(splitPart + 1); //current split
                splitOut.writeInt(splitQtty); //total splits
                final var payload = in.readNBytes(bufferCapacity - splitHeaderSize); //is this too memory consuming?
                splitOut.write(payload);
                splitOut.close();

                final var task = new SendSchematicPacketTask(splitMessage, tickOffset);
                TaskManager.getInstance().register(task);

                tickOffset += tickIncrement;
            }

            in.close();
        }
    }
}
