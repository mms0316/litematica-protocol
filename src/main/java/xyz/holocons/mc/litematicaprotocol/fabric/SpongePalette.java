package xyz.holocons.mc.litematicaprotocol.fabric;

import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import net.minecraft.block.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Palettes in .litematic may have repeated entries, but in .schem all palettes must be unique
 */
public class SpongePalette {

    List<String> uniqueIds = new ArrayList<>();

    public SpongePalette(ILitematicaBlockStatePalette palette) {
        for (int i = 0; i < palette.getPaletteSize(); i++) {
            final var blockState = palette.getBlockState(i);
            final var blockStateString = new BlockStateFormatter(blockState).toString();

            if (uniqueIds.contains(blockStateString))
                continue;

            uniqueIds.add(blockStateString);
        }
    }

    public int idFor(BlockState blockState) {
        final var blockStateString = new BlockStateFormatter(blockState).toString();

        return uniqueIds.indexOf(blockStateString);
    }

    @Nullable
    public String getId(int i) {
        return uniqueIds.get(i);
    }

    public int getPaletteSize() {
        return uniqueIds.size();
    }
}
