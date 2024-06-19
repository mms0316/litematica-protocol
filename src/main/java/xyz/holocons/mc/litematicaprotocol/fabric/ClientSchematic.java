package xyz.holocons.mc.litematicaprotocol.fabric;

import java.io.DataOutput;
import java.io.IOException;
import java.util.StringJoiner;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtType;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.nbt.visitor.NbtElementVisitor;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.registry.Registries;

public class ClientSchematic {

    private final LitematicaSchematic schematic;
    private final String regionName;
    private ByteBuf data;

    public ClientSchematic(final SchematicPlacement placement) {
        this.schematic = placement.getSchematic();
        this.regionName = placement.getSubRegionCount() == 1
                ? placement.getAllSubRegionsPlacements().iterator().next().getName()
                : placement.getSelectedSubRegionName();
        this.data = Unpooled.buffer(8192);
    }

    public ByteBuf getData() throws IOException {
        if (!data.isReadOnly()) {
            final var nbt = createNbtCompound(schematic, regionName);
            if (nbt.isEmpty()) {
                throw new IOException("No schematic is selected");
            }
            try (final var out = new ByteBufOutputStream(data)) {
                NbtIo.writeCompressed(nbt, out);
            }
            this.data = data.asReadOnly();
        }
        return data;
    }

    // https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md
    private static NbtCompound createNbtCompound(final LitematicaSchematic schematic, final String regionName) {
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
        final var container = schematic.getSubRegionContainer(regionName);
        final var palette = new Palette(container);
        nbt.put("Palette", palette);
        final var blockData = new BlockData(palette, container);
        nbt.put("BlockData", blockData);
        final var blockEntities = new BlockEntities(schematic, regionName);
        if (!blockEntities.isEmpty()) {
            nbt.put("BlockEntities", blockEntities);
        }
        final var entities = new Entities(schematic, regionName);
        if (!entities.isEmpty()) {
            nbt.put("Entities", entities);
        }
        return nbt;
    }

    private interface NbtSchematicElement extends NbtElement {

        static void write(final String key, final NbtElement element, final DataOutput output) throws IOException {
            final var type = element.getType();
            output.writeByte(type);
            if (type != NbtElement.END_TYPE) {
                output.writeUTF(key);
                element.write(output);
            }
        }

        @Override
        default NbtType<?> getNbtType() {
            throw new UnsupportedOperationException();
        }

        @Override
        default NbtElement copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        default int getSizeInBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        default void accept(NbtElementVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        default NbtScanner.Result doAccept(NbtScanner visitor) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Palette implements NbtSchematicElement {

        private final Object2IntArrayMap<BlockState> states;

        public Palette(final LitematicaBlockStateContainer container) {
            final var palette = container.getPalette();
            this.states = new Object2IntArrayMap<>(palette.getPaletteSize());
            states.defaultReturnValue(-1);
            for (int i = 0; i < palette.getPaletteSize(); i++) {
                final var nullableState = palette.getBlockState(i);
                final var state = nullableState != null ? nullableState : LitematicaBlockStateContainer.AIR_BLOCK_STATE;
                if (!states.containsKey(state)) {
                    final var id = states.size();
                    states.put(state, id);
                }
            }
        }

        public int getId(final BlockState state) {
            return states.getInt(state);
        }

        public int size() {
            return states.size();
        }

        @Override
        public void write(DataOutput output) throws IOException {
            for (final var entry : states.object2IntEntrySet()) {
                final var state = entry.getKey();
                final var id = entry.getIntValue();
                output.writeByte(NbtElement.INT_TYPE);
                output.writeUTF(formatBlockState(state));
                output.writeInt(id);
            }
            output.writeByte(NbtElement.END_TYPE);
        }

        private static String formatBlockState(final BlockState state) {
            final var propertyJoiner = new StringJoiner(",", "[", "]").setEmptyValue("");
            state.getEntries().forEach((property, comparable) -> propertyJoiner
                    .add(property.getName() + '=' + String.valueOf(comparable).toLowerCase()));
            return Registries.BLOCK.getId(state.getBlock()).toString() + propertyJoiner.toString();
        }

        @Override
        public byte getType() {
            return NbtElement.COMPOUND_TYPE;
        }
    }

    private static final class BlockData implements NbtSchematicElement {

        private final PacketByteBuf data;

        public BlockData(final Palette palette, final LitematicaBlockStateContainer container) {
            final var sizeX = container.getSize().getX();
            final var sizeY = container.getSize().getY();
            final var sizeZ = container.getSize().getZ();
            final var maxVarIntLength = VarInts.getSizeInBytes(palette.size() - 1);
            final var bufferSize = sizeX * sizeY * sizeZ * maxVarIntLength;
            this.data = new PacketByteBuf(Unpooled.buffer(bufferSize, bufferSize));
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        data.writeVarInt(palette.getId(container.get(x, y, z)));
                    }
                }
            }
        }

        @Override
        public void write(DataOutput output) throws IOException {
            output.writeInt(data.writerIndex());
            output.write(data.array(), 0, data.writerIndex());
        }

        @Override
        public byte getType() {
            return NbtElement.BYTE_ARRAY_TYPE;
        }
    }

    private static final class BlockEntities implements NbtSchematicElement {

        private final LitematicaSchematic schematic;
        private final String regionName;

        public BlockEntities(final LitematicaSchematic schematic, final String regionName) {
            this.schematic = schematic;
            this.regionName = regionName;
        }

        public boolean isEmpty() {
            return schematic.getBlockEntityMapForRegion(regionName).isEmpty();
        }

        @Override
        public void write(DataOutput output) throws IOException {
            final var blockEntites = schematic.getBlockEntityMapForRegion(regionName);
            final var container = schematic.getSubRegionContainer(regionName);
            output.writeByte(NbtElement.COMPOUND_TYPE);
            output.writeInt(blockEntites.size());
            for (final var entry : blockEntites.entrySet()) {
                final var position = entry.getKey();
                final var nbt = entry.getValue();
                final var x = position.getX();
                final var y = position.getY();
                final var z = position.getZ();
                writePos(x, y, z, output);
                writeId(container.get(x, y, z), output);
                writeExtra(nbt, output);
                output.writeByte(NbtElement.END_TYPE);
            }
        }

        private static void writePos(final int x, final int y, final int z, final DataOutput output)
                throws IOException {
            output.writeByte(NbtElement.INT_ARRAY_TYPE);
            output.writeUTF("Pos");
            output.writeInt(3);
            output.writeInt(x);
            output.writeInt(y);
            output.writeInt(z);
        }

        private static void writeId(final BlockState state, final DataOutput output) throws IOException {
            output.writeByte(NbtElement.STRING_TYPE);
            output.writeUTF("Id");
            output.writeUTF(Registries.BLOCK.getId(state.getBlock()).toString());
        }

        private static void writeExtra(final NbtCompound nbt, final DataOutput output) throws IOException {
            for (final var key : nbt.getKeys()) {
                switch (key) {
                    case "id", "x", "y", "z" -> {
                    }
                    default -> NbtSchematicElement.write(key, nbt.get(key), output);
                }
            }
        }

        @Override
        public byte getType() {
            return NbtElement.LIST_TYPE;
        }
    }

    private static final class Entities implements NbtSchematicElement {

        private final LitematicaSchematic schematic;
        private final String regionName;

        public Entities(final LitematicaSchematic schematic, final String regionName) {
            this.schematic = schematic;
            this.regionName = regionName;
        }

        public boolean isEmpty() {
            return schematic.getEntityListForRegion(regionName).isEmpty();
        }

        @Override
        public void write(DataOutput output) throws IOException {
            final var entities = schematic.getEntityListForRegion(regionName);
            final var regionPosition = schematic.getSubRegionPosition(regionName);
            output.writeByte(NbtElement.COMPOUND_TYPE);
            output.writeInt(entities.size());
            for (final var entity : entities) {
                final var nbt = entity.nbt;
                final var x = (double) regionPosition.getX() + entity.posVec.x;
                final var y = (double) regionPosition.getY() + entity.posVec.y;
                final var z = (double) regionPosition.getZ() + entity.posVec.z;
                writePos(x, y, z, output);
                writeId(nbt.get("id"), output);
                writeExtra(nbt, output);
                output.writeByte(NbtElement.END_TYPE);
            }
        }

        private static void writePos(final double x, final double y, final double z, final DataOutput output)
                throws IOException {
            output.writeByte(NbtElement.LIST_TYPE);
            output.writeUTF("Pos");
            output.writeByte(NbtElement.DOUBLE_TYPE);
            output.writeInt(3);
            output.writeDouble(x);
            output.writeDouble(y);
            output.writeDouble(z);
        }

        private static void writeId(final NbtElement id, final DataOutput output) throws IOException {
            NbtSchematicElement.write("Id", id, output);
        }

        private static void writeExtra(final NbtCompound nbt, final DataOutput output) throws IOException {
            for (final var key : nbt.getKeys()) {
                switch (key) {
                    case "id", "Pos" -> {
                    }
                    default -> NbtSchematicElement.write(key, nbt.get(key), output);
                }
            }
        }

        @Override
        public byte getType() {
            return NbtElement.LIST_TYPE;
        }
    }
}
