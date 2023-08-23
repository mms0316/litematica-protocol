package xyz.holocons.mc.litematicaprotocol.fabric;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;

import java.util.StringJoiner;

public class BlockStateFormatter {

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
