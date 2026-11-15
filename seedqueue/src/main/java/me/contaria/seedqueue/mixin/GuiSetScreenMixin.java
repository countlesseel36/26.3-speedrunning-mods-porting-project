package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the wall on screen while a queued world is handed over. {@code doWorldLoad} normally swaps
 * to a progress screen and then a {@link LevelLoadingScreen}; both are suppressed here, so clicking
 * a card never flashes the title or loading screen.
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
   @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
   private void seedqueue$keepWallDuringJoin(Screen screen, CallbackInfo ci) {
      if (screen == null) {
         return;
      }

      // 进入实例的过程中连按 Esc 会把标题界面设回来，压在世界之上且无法继续。
      // 切换期间拒绝安装标题/暂停界面（真正的退出不受影响，那时没有切换在途）。
      if (SeedQueue.isTransitionPending()
         && (screen instanceof TitleScreen
            || screen instanceof net.minecraft.client.gui.screens.PauseScreen)) {
         SeedQueue.LOGGER.info(
            "[seedqueue] refused to show {} while a transition is pending",
            screen.getClass().getSimpleName()
         );
         ci.cancel();
         return;
      }

      // 手动创建世界：世界与玩家都已就绪，但 vanilla 仍会把加载/进度屏设回来，
      // 于是永久停在 "Preparing for world creation..."。这里直接拒绝安装这类屏幕。
      if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen) {
         net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

         if (minecraft.level != null && minecraft.player != null && !SeedQueue.isTransitionPending()) {
            SeedQueue.LOGGER.info(
               "[seedqueue] refused to show {} (world is already playable)",
               screen.getClass().getSimpleName()
            );
            ci.cancel();
            return;
         }
      }

      if (!SeedQueue.suppressLoadingScreens) {
         return;
      }

      if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof TitleScreen) {
         if (Boolean.getBoolean("seedqueue.screenLog")) {
            SeedQueue.LOGGER.info("[seedqueue] suppressed screen {}", screen.getClass().getSimpleName());
         }

         ci.cancel();
      }
   }
}
