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
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({TitleScreen.class})
public class TitleScreenMixin {
   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void seedqueue$addWallButton(CallbackInfo callback) {
      Screen screen = ((Screen)(Object)this);
      Button button = Button.builder(Component.literal("SeedQueue"), arg -> SeedQueue.openWall(Minecraft.getInstance()))
         .bounds(screen.width - 110, 6, 100, 20)
         .build();
      (((ScreenInvoker)(Object)this)).seedqueue$addRenderableWidget(button);
   }
}
