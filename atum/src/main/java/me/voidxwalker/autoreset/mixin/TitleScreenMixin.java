package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the golden boots button next to "Singleplayer". Left click starts a reset, shift + left
 * click opens the autoreset settings screen.
 *
 * <p>The icon is drawn from the vanilla item texture instead of an {@code ItemStack}: in 26.3 the
 * item components of {@code Items.GOLDEN_BOOTS} are not bound yet when the title screen is first
 * shown, so creating an ItemStack there throws. A plain texture works from the very first frame.</p>
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
   @Unique
   private static boolean atum$bootsTextureLoaded;
   @Unique
   private static boolean atum$bootsTextureFailed;

   @Unique
   private Button atum$resetButton;

   protected TitleScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void atum$init(CallbackInfo ci) {
      // Do not schedule a reset here: while a reset chain is running the title screen is shown
      // for a single transition frame, and scheduling from init re-opened the create-world screen
      // (and flashed the title again) on every appearance.
      this.atum$resetButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
         if (this.minecraft.hasShiftDown()) {
            Atum.openOptionsScreen(this);
         } else if (Atum.SEEDQUEUE_PRESENT) {
            // With SeedQueue installed the wall owns world creation and resets, so the single-instance
            // reset from the boots is disabled; shift-click still opens Atum's options.
            Atum.LOGGER.info("[atum] single-instance reset disabled while SeedQueue is installed");
         } else {
            Atum.scheduleReset();
         }
      }).bounds(this.width / 2 - 124, this.height / 4 + 48, 20, 20).build());
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void atum$drawButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
      if (this.atum$resetButton == null) {
         return;
      }

      Identifier boots = Identifier.withDefaultNamespace("textures/item/golden_boots.png");
      int x = this.atum$resetButton.getX() + 2;
      int y = this.atum$resetButton.getY() + 2;

      if (!atum$bootsTextureLoaded && !atum$bootsTextureFailed) {
         try {
            this.minecraft.getTextureManager().registerAndLoad(boots, new SimpleTexture(boots));
            atum$bootsTextureLoaded = true;
         } catch (Throwable t) {
            atum$bootsTextureFailed = true;
            Atum.LOGGER.warn("could not load the golden boots texture: {}", t.toString());
         }
      }

      if (atum$bootsTextureLoaded) {
         graphics.blit(RenderPipelines.GUI_TEXTURED, boots, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
      } else {
         // Opaque golden tile as a last resort (alpha must not be 0, or it would be invisible).
         graphics.fill(x, y, x + 16, y + 16, 0xFFFFD54A);
         graphics.outline(x, y, 16, 16, 0xFFFFAA00);
      }
   }
}
