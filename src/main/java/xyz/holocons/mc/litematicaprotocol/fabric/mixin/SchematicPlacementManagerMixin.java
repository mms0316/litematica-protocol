package xyz.holocons.mc.litematicaprotocol.fabric.mixin;

import java.io.IOException;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.EntityUtils;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.registry.Registry;
import xyz.holocons.mc.litematicaprotocol.Constants;
import xyz.holocons.mc.litematicaprotocol.fabric.LitematicaProtocolMod;

@Mixin(value = SchematicPlacementManager.class, remap = false)
abstract class SchematicPlacementManagerMixin {

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
        sendSchematic(convertSchematic(placement.getSchematic(), regionName), client);
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
        final var palette = schematic.getSubRegionContainer(regionName).getPalette().writeToNBT();
        final var nbt = new NbtCompound();
        for (int i = 0; i < palette.size(); i++) {
            final var stringBuilder = new StringBuilder();
            final var blockState = NbtHelper.toBlockState(palette.getCompound(i));
            final var properties = blockState.getEntries().entrySet().iterator();
            stringBuilder.append(Registry.BLOCK.getId(blockState.getBlock()));
            if (properties.hasNext()) {
                stringBuilder.append('[');
                while (properties.hasNext()) {
                    final var property = properties.next();
                    stringBuilder.append(property.getKey().getName())
                            .append('=')
                            .append(String.valueOf(property.getValue()).toLowerCase())
                            .append(properties.hasNext() ? ',' : ']');
                }
            }
            nbt.putInt(stringBuilder.toString(), i);
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
                    final var position = IntList.of(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
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
        return schematic.getEntityListForRegion(regionName).stream()
                .map(entity -> {
                    final var nbt = entity.nbt.copy();
                    final var id = nbt.getString("id");
                    nbt.remove("id");
                    nbt.putString("Id", id);
                    return nbt;
                })
                .collect(Collectors.toCollection(NbtList::new));
    }

    private static void sendSchematic(final NbtCompound nbt, final MinecraftClient client) {
        if (nbt == null || nbt.isEmpty()) {
            client.inGameHud.getChatHud().addMessage(Text.of("No schematic is selected"));
            return;
        }
        final var message = new PacketByteBuf(Unpooled.buffer());
        try (final var out = new ByteBufOutputStream(message)) {
            out.writeUTF(Constants.SPONGE_SCHEMATIC);
            NbtIo.writeCompressed(nbt, out);
        } catch (IOException e) {
        }
        ClientPlayNetworking.send(LitematicaProtocolMod.CHANNEL_MAIN, message);
    }
}
