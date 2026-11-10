package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.WallKeybindings;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles the configurable "back to wall" key while the player is actually playing in a world.
 *
 * <p>Escape used to be handled here as well because it looked broken, but 26.3 screens decide with
 * {@code KeyEvent#isEscape()} and the pause code works too, so the extra handler only processed every
 * press twice and made the screen flicker. Escape is left to vanilla again.</p>
 */
@Mixin(value = KeyboardHandler.class, priority = 500)
public class KeyboardHandlerMixin {
   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void seedqueue$backToWallKey(long window, int key, KeyEvent event, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();

      if (client.level == null) {
         return;
      }

      // The wall key must also work from the in-game pause screen (F6 there did nothing). Any other
      // screen keeps the press for itself: in the wall key binding screen it has to bind, not join.
      net.minecraft.client.gui.screens.Screen screen = client.gui.screen();

      if (screen != null && !(screen instanceof net.minecraft.client.gui.screens.PauseScreen)) {
         return;
      }

      int backToWall = WallKeybindings.key(WallKeybindings.Action.BACK_TO_WALL);

      if (backToWall == WallKeybindings.UNKNOWN || WallKeybindings.keyOf(event) != backToWall) {
         return;
      }

      if (SeedQueue.returnToWallFromJoinedWorld(client)) {
         if (screen != null) {
            client.gui.setScreen(null);
         }

         ci.cancel();
      }
   }
}
