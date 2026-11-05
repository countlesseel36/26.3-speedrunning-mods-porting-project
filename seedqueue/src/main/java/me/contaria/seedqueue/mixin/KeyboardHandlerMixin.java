package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F6 is Atum's reset key; the wall only borrows it while the player is actually playing in a world.
 *
 * <p>Inside a world F6 goes straight back to the wall (no saving screen). Everywhere else - title
 * screen, wall, loading screens - the key is left to Atum, so F6 keeps starting a new world exactly
 * like it does without SeedQueue. The wall no longer has a key of its own (F7 was removed).</p>
 */
@Mixin(value = KeyboardHandler.class, priority = 500)
public class KeyboardHandlerMixin {
   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void seedqueue$backToWallKey(long window, int key, KeyEvent event, CallbackInfo ci) {
      if (event.key() != com.mojang.blaze3d.platform.InputConstants.KEY_F6) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();

      if (SeedQueue.returnToWallFromJoinedWorld(minecraft)) {
         ci.cancel();
         return;
      }

      // Outside a world the key is disabled entirely: it must not reset the game either.
      ci.cancel();
   }
}
