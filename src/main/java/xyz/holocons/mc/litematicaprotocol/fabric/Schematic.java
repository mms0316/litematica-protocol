package xyz.holocons.mc.litematicaprotocol.fabric;

import java.io.IOException;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;

public class Schematic {

    private final LitematicaSchematic schematic;
    private final String regionName;

    public Schematic(final SchematicPlacement placement) {
        this.schematic = placement.getSchematic();
        this.regionName = placement.getSubRegionCount() == 1
                ? placement.getAllSubRegionsPlacements().iterator().next().getName()
                : placement.getSelectedSubRegionName();
    }

    public PacketByteBuf getPayload() throws IOException {
        final var nbt = toNbtCompound(schematic, regionName);
        if (nbt.isEmpty()) {
            throw new IOException("No schematic is selected");
        }
        final var data = new PacketByteBuf(Unpooled.buffer(8192));
        try (final var out = new ByteBufOutputStream(data)) {
            NbtIo.writeCompressed(nbt, out);
        }
        return data;
    }

    // https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md
    private static NbtCompound toNbtCompound(final LitematicaSchematic schematic, final String regionName) {
        final var nbt = new NbtCompound();
        if (schematic == null || regionName == null || schematic.getSubRegionContainer(regionName) == null) {
            return nbt;
        }
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
        final var size = schematic.getAreaSize(regionName);
        nbt.putShort("Width", (short) Math.abs(size.getX()));
        nbt.putShort("Height", (short) Math.abs(size.getY()));
        nbt.putShort("Length", (short) Math.abs(size.getZ()));
        final var container = new BlockContainer(schematic.getSubRegionContainer(regionName));
        nbt.put("Palette", container.getPalette());
        nbt.put("BlockData", container.getBlockData());
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

    private static final class BlockContainer {

        private final LitematicaBlockStateContainer blocks;
        private final Object2IntArrayMap<BlockState> blockStates;

        public BlockContainer(final LitematicaBlockStateContainer container) {
            this.blocks = container;
            final var palette = container.getPalette();
            this.blockStates = new Object2IntArrayMap<>(palette.getPaletteSize());
            blockStates.defaultReturnValue(-1);
            for (int i = 0; i < palette.getPaletteSize(); i++) {
                final var state = palette.getBlockState(i);
                if (!blockStates.containsKey(state)) {
                    final var id = blockStates.size();
                    blockStates.put(state, id);
                }
            }
        }

        public NbtCompound getPalette() {
            final var nbt = new NbtCompound();
            blockStates.forEach((state, id) -> nbt.putInt(formatBlockState(state), id));
            return nbt;
        }

        private static String formatBlockState(final BlockState state) {
            final var nonNullBlockState = state != null ? state : LitematicaBlockStateContainer.AIR_BLOCK_STATE;
            final var propertyJoiner = new StringJoiner(",", "[", "]").setEmptyValue("");
            nonNullBlockState.getEntries().forEach((property, comparable) -> propertyJoiner
                    .add(property.getName() + '=' + String.valueOf(comparable).toLowerCase()));
            return Registries.BLOCK.getId(nonNullBlockState.getBlock()).toString() + propertyJoiner.toString();
        }

        public NbtByteArray getBlockData() {
            final var sizeX = blocks.getSize().getX();
            final var sizeY = blocks.getSize().getY();
            final var sizeZ = blocks.getSize().getZ();
            final var maxVarIntLength = PacketByteBuf.getVarIntLength(blockStates.size() - 1);
            final var bufferSize = sizeX * sizeY * sizeZ * maxVarIntLength;
            final var data = new PacketByteBuf(Unpooled.buffer(bufferSize, bufferSize));
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        data.writeVarInt(blockStates.getInt(blocks.get(x, y, z)));
                    }
                }
            }
            return new NbtByteArray(data.getWrittenBytes());
        }
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
}
