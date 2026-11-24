package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.wall.WallKeybindings;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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

@Mixin(
   value = {KeyboardHandler.class},
   priority = 500
)
public class KeyboardHandlerMixin {
   @Inject(
      method = {"keyPress"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$backToWallKey(long window, int keyCode, KeyEvent event, CallbackInfo callback) {
      Minecraft minecraft = Minecraft.getInstance();
      if (SeedQueue.isTransitionPending() && event.isEscape()) {
         callback.cancel();
      } else if (minecraft.level != null) {
         Screen screen = minecraft.gui.screen();
         if (screen == null || screen instanceof PauseScreen) {
            if (SeedQueue.benchmarking) {
               int count = WallKeybindings.key(WallKeybindings.Action.CANCEL_BENCHMARK);
               int count2 = WallKeybindings.key(WallKeybindings.Action.QUIT);
               int count3 = WallKeybindings.keyOf(event);
               if (count != -1 && count3 == count || count2 != -1 && count3 == count2) {
                  SeedQueue.cancelBenchmark();
                  callback.cancel();
                  return;
               }
            }

            int count4 = WallKeybindings.key(WallKeybindings.Action.BACK_TO_WALL);
            int count5 = WallKeybindings.key(WallKeybindings.Action.PLAY_NEXT_LOCK);
            int count6 = WallKeybindings.keyOf(event);
            if (count5 != -1 && count6 == count5) {
               if (!SeedQueue.hasLockedCandidate()) {
                  SeedQueue.LOGGER.info("[seedqueue] play next lock: no locked entry, staying in the world");
                  callback.cancel();
               } else {
                  if (SeedQueue.returnToWallFromJoinedWorld(minecraft)) {
                     if (screen != null) {
                        minecraft.gui.setScreen(null);
                     }

                     SeedQueue.requestPlayNextLock();
                     callback.cancel();
                  }
               }
            } else if (count4 != -1 && count6 == count4) {
               if (SeedQueue.returnToWallFromJoinedWorld(minecraft)) {
                  if (screen != null) {
                     minecraft.gui.setScreen(null);
                  }

                  callback.cancel();
               }
            }
         }
      }
   }
}
