package fast_reset.client.mixin;

import fast_reset.client.FastReset;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a small button to the options screen that moves the "Quit to Title" button.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
   protected OptionsScreenMixin(Component title) {
      super(title);
   }

   private static Component fastreset$buttonText() {
      return Component.translatable(
         FastReset.buttonLocation == 1 ? "fast_reset.button.center" : "fast_reset.button.bottom_right");
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void fastreset$addPositionButton(CallbackInfo ci) {
      this.addRenderableWidget(Button.builder(fastreset$buttonText(), button -> {
         FastReset.updateButtonLocation();
         button.setMessage(fastreset$buttonText());
      }).bounds(4, this.height - 24, 150, 20).build());
   }
}
