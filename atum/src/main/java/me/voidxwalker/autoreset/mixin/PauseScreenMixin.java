package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
   protected PauseScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void atum$addStopButton(CallbackInfo ci) {
      if (!Atum.isRunning || !((PauseScreen) (Object) this).showsPauseMenu()) {
         return;
      }

      this.addRenderableWidget(Button.builder(Atum.text("menu.stop_resets", "Stop Resets & Quit"), button -> {
         Atum.isRunning = false;
         Atum.shouldReset = false;
         if (this.minecraft.level != null) {
            this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
         }
      }).bounds(4, this.height - 24, 150, 20).build());
   }
}
