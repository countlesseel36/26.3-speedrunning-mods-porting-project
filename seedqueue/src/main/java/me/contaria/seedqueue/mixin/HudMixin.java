package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.QueueConfig;
import me.contaria.seedqueue.QueueEntry;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.WallScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunkmap overlay (1.16.1 parity): while in a world and queued worlds are ready, draw a small
 * top-down map of the newest queued world in the top-left corner. Toggled by the
 * {@code showChunkMaps} option.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void seedqueue$chunkMap(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      if (!QueueConfig.showChunkMaps) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();

      if (minecraft.level == null || minecraft.gui.screen() instanceof WallScreen) {
         return;
      }

      QueueEntry best = null;

      for (QueueEntry entry : SeedQueue.entriesSnapshot()) {
         if (entry.mapColors != null && (best == null || entry.number > best.number)) {
            best = entry;
         }
      }

      if (best == null) {
         return;
      }

      int scale = 3;

      for (int row = 0; row < best.mapHeight; row++) {
         for (int column = 0; column < best.mapWidth; column++) {
            int color = best.mapColors[row * best.mapWidth + column];
            int x = 4 + column * scale;
            int y = 4 + row * scale;
            graphics.fill(x, y, x + scale, y + scale, (color & 0x00FFFFFF) | 0xB0000000);
         }
      }
   }
}
