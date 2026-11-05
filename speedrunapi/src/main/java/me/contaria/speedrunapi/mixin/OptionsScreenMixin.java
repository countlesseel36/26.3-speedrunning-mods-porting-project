package me.contaria.speedrunapi.mixin;

import me.contaria.speedrunapi.config.screen.SpeedrunModConfigsScreen;
import me.contaria.speedrunapi.util.TextUtil;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Mod Configs" button to the vanilla options screen.
 *
 * <p>It sits in the top left corner. The bottom row is already used by FastReset's own button and by
 * the screen's "Done" button, and overlapping widgets made real mouse clicks land on the other
 * widget instead of opening the config list.</p>
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
   protected OptionsScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void speedrunapi$addConfigButton(CallbackInfo ci) {
      // The footer row is split by the centered "Done" button, so use the free right corner.
      // The bottom left corner belongs to FastReset's own options button; overlapping widgets made
      // real mouse clicks land on the other widget instead of opening the config list.
      int width = 100;
      int x = Math.max(4, this.width - width - 4);
      int y = this.height - 24;

      this.addRenderableWidget(
         Button.builder(TextUtil.translatable("speedrunapi.gui.config.button"), button -> this.minecraft.gui.setScreen(new SpeedrunModConfigsScreen(this)))
            .bounds(x, y, width, 20)
            .build()
      );
   }
}
