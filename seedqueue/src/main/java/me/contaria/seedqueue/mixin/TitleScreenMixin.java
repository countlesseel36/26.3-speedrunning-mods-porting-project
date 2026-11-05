package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the SeedQueue wall button to the top right corner of the title screen. */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
   @Inject(method = "init", at = @At("TAIL"))
   private void seedqueue$addWallButton(CallbackInfo ci) {
      Screen self = (Screen)(Object)this;
      Button button = Button.builder(
         Component.literal("SeedQueue"),
         pressed -> SeedQueue.openWall(Minecraft.getInstance())
      ).bounds(self.width - 110, 6, 100, 20).build();
      ((ScreenInvoker)(Object)this).seedqueue$addRenderableWidget(button);
   }
}