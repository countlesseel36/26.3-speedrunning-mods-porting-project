package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.QueueConfig;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.WallScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SeedQueue status line in the F3 debug screen (1.21.4 AtumHudMixin equivalent). */
@Mixin(DebugScreenOverlay.class)
public class DebugOverlayMixin {
   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void seedqueue$drawStatus(GuiGraphicsExtractor graphics, CallbackInfo ci) {
      Minecraft minecraft = Minecraft.getInstance();

      // Only on the SeedQueue wall page; never on the in-game HUD or other screens.
      if (!QueueConfig.showHud || !(minecraft.gui.screen() instanceof WallScreen)) {
         return;
      }

      String text = "SeedQueue: " + SeedQueue.entriesCount() + " / " + QueueConfig.queueSize
         + (SeedQueue.benchmarking ? "  benchmark=" + SeedQueue.benchmarkCount : "");
      graphics.text(minecraft.font, text, 2, 2, 0xFFFFFFFF);
   }
}
