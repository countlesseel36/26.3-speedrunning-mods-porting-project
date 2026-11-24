package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.config.QueueConfig;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.wall.WallScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({DebugScreenOverlay.class})
public class DebugOverlayMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void seedqueue$drawStatus(GuiGraphicsExtractor guiGraphicsExtractor, CallbackInfo callback) {
      Minecraft minecraft = Minecraft.getInstance();
      if (QueueConfig.showHud && minecraft.gui.screen() instanceof WallScreen) {
         String text = "SeedQueue: "
            + SeedQueue.entriesCount()
            + " / "
            + QueueConfig.queueSize
            + (SeedQueue.benchmarking ? "  benchmark=" + SeedQueue.benchmarkCount : "");
         guiGraphicsExtractor.text(minecraft.font, text, 2, 2, -1);
      }
   }
}
