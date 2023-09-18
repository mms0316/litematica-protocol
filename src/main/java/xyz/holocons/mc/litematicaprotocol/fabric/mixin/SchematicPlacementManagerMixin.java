package xyz.holocons.mc.litematicaprotocol.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.EntityUtils;
import net.minecraft.client.MinecraftClient;
import xyz.holocons.mc.litematicaprotocol.fabric.LitematicaProtocolMod;
import xyz.holocons.mc.litematicaprotocol.fabric.ClientSchematic;
import xyz.holocons.mc.litematicaprotocol.fabric.TaskSendSchematic;

@Mixin(value = SchematicPlacementManager.class, remap = false)
abstract class SchematicPlacementManagerMixin {

    @Inject(method = "pastePlacementToWorld", at = @At("HEAD"), cancellable = true)
    private void injectProtocol(final SchematicPlacement placement, MinecraftClient client, CallbackInfo info) {
        if (!LitematicaProtocolMod.isProtocolAvailable() || client.isIntegratedServerRunning()) {
            return;
        }
        info.cancel();
        final var scheduler = TaskScheduler.getInstanceClient();
        if (client.player == null || !EntityUtils.isCreativeMode(client.player) || placement == null
                || scheduler.hasTask(TaskSendSchematic.class)) {
            return;
        }
        final var schematic = new ClientSchematic(placement);
        final var task = new TaskSendSchematic(schematic);
        scheduler.scheduleTask(task, 1);
    }
}
