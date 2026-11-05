package me.contaria.standardsettings.mixin;

import me.contaria.standardsettings.StandardSettings;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3 moved screen switching from Minecraft to Gui. */
@Mixin(Gui.class)
public abstract class GuiMixin {
   @Inject(method = "setScreen", at = @At("TAIL"))
   private void standardsettings$autoF3EscOnPreview(Screen screen, CallbackInfo ci) {
      if (screen instanceof LevelLoadingScreen && StandardSettings.config.autoF3Esc) {
         Component backToGame = Component.translatable("menu.returnToGame");

         for (GuiEventListener element : screen.children()) {
            if (element instanceof Button button && backToGame.equals(button.getMessage())) {
               button.onPress(null);
               break;
            }
         }
      }

      StandardSettings.autoF3EscPending &= !(screen instanceof PauseScreen);
   }
}