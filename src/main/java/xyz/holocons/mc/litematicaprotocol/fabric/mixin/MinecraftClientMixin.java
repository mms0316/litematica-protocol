package xyz.holocons.mc.litematicaprotocol.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.holocons.mc.litematicaprotocol.fabric.TaskManager;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "tick()V", at = @At("RETURN"))
    private void onPostKeyboardInput(CallbackInfo ci)
    {
        TaskManager.getInstance().tick();
    }
}
