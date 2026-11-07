package fast_reset.client.mixin;

import fast_reset.client.FastReset;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "Quit to Title" button to the pause menu. Pressing it disconnects without saving.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
   protected PauseScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void fastreset$addQuitToTitleButton(CallbackInfo ci) {
      if (!((PauseScreen) (Object) this).showsPauseMenu()) {
         return;
      }

      int width = 102;
      int height = 20;
      int x;
      int y;
      if (FastReset.buttonLocation == 1) {
         width = 204;
         x = this.width / 2 - width / 2;
         y = this.height / 4 + 148 - height;
      } else {
         x = this.width - width - 4;
         y = this.height - height - 4;
      }

      this.addRenderableWidget(Button.builder(FastReset.quitLabel(), button -> {
         FastReset.saveOnQuit = false;
         FastReset.skipSavesNow = true;

         try {
            // Blocks until the integrated server has fully shut down, so the flag is safe to
            // restore in the finally block.
            this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
         } finally {
            FastReset.skipSavesNow = false;
            FastReset.saveOnQuit = true;
         }
      }).bounds(x, y, width, height).build());
   }
}
