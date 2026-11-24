package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.config.QueueConfig;
import me.contaria.seedqueue.queue.QueueEntry;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.wall.WallScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
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

@Mixin({Hud.class})
public abstract class HudMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void seedqueue$chunkMap(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker, CallbackInfo callback) {
      if (QueueConfig.showChunkMaps) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.level != null && !(minecraft.gui.screen() instanceof WallScreen)) {
            QueueEntry entry = null;

            for (QueueEntry entry2 : SeedQueue.entriesSnapshot()) {
               if (entry2.mapColors != null && (entry == null || entry2.number > entry.number)) {
                  entry = entry2;
               }
            }

            if (entry != null) {
               byte byteValue = 3;

               for (int i = 0; i < 20; i++) {
                  for (int j = 0; j < 32; j++) {
                     int count = entry.mapColors[i * 32 + j];
                     int count2 = 4 + j * byteValue;
                     int count3 = 4 + i * byteValue;
                     guiGraphicsExtractor.fill(count2, count3, count2 + byteValue, count3 + byteValue, count & 16777215 | -1342177280);
                  }
               }
            }
         }
      }
   }
}
