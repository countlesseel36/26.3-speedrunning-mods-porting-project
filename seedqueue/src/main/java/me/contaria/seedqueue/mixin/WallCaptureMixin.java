package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the world-only frame into the card texture right before the GUI is drawn.
 *
 * <p>For one frame the wall lets the queued world become {@code Minecraft.level}, exactly like the
 * world-creation preview: the normal frame pipeline renders it into the main render target through
 * 26.3's frame graph. At this point the target holds the world render but the wall has not been
 * drawn yet, so copying it into the entry's own {@code displayTarget} gives a clean card image.
 * The wall then samples that copy, which is never a render attachment (no framebuffer feedback).</p>
 */
@Mixin(GuiRenderer.class)
public class WallCaptureMixin {
   @Inject(method = "render", at = @At("HEAD"))
   private void seedqueue$captureWorldForWall(CallbackInfo ci) {
      SeedQueue.captureWallFrame(Minecraft.getInstance());
   }
}
