package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.WallKeybindings;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles the configurable "back to wall" key while the player is actually playing in a world.
 *
 * <p>Escape used to be handled here as well because it looked broken, but 26.3 screens decide with
 * {@code KeyEvent#isEscape()} and the pause code works too, so the extra handler only processed every
 * press twice and made the screen flicker. Escape is left to vanilla again.</p>
 */
@Mixin(value = KeyboardHandler.class, priority = 500)
public class KeyboardHandlerMixin {
   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void seedqueue$backToWallKey(long window, int key, KeyEvent event, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();

      if (client.level == null) {
         return;
      }

      // The wall key must also work from the in-game pause screen (F6 there did nothing). Any other
      // screen keeps the press for itself: in the wall key binding screen it has to bind, not join.
      net.minecraft.client.gui.screens.Screen screen = client.gui.screen();

      if (screen != null && !(screen instanceof net.minecraft.client.gui.screens.PauseScreen)) {
         return;
      }

      // 跑分覆盖层不是 WallScreen，N / Esc 收不到，所以在全局按键层处理取消
      if (SeedQueue.benchmarking) {
         int cancel = WallKeybindings.key(WallKeybindings.Action.CANCEL_BENCHMARK);
         int quit = WallKeybindings.key(WallKeybindings.Action.QUIT);
         int code = WallKeybindings.keyOf(event);

         if ((cancel != WallKeybindings.UNKNOWN && code == cancel)
            || (quit != WallKeybindings.UNKNOWN && code == quit)) {
            SeedQueue.cancelBenchmark();
            ci.cancel();
            return;
         }
      }

      int backToWall = WallKeybindings.key(WallKeybindings.Action.BACK_TO_WALL);

      int playNextLock = WallKeybindings.key(WallKeybindings.Action.PLAY_NEXT_LOCK);
      int pressed = WallKeybindings.keyOf(event);

      if (playNextLock != WallKeybindings.UNKNOWN && pressed == playNextLock) {
         // 世界里没有锁定实例时什么都不做（不要回墙）
         if (!SeedQueue.hasLockedCandidate()) {
            SeedQueue.LOGGER.info("[seedqueue] play next lock: no locked entry, staying in the world");
            ci.cancel();
            return;
         }

         // 在世界里按"进入下个锁定实例"：跳过保存直接回墙并立即进入下一张锁定卡
         if (SeedQueue.returnToWallFromJoinedWorld(client)) {
            if (screen != null) {
               client.gui.setScreen(null);
            }

            SeedQueue.requestPlayNextLock();
            ci.cancel();
         }

         return;
      }

      if (backToWall == WallKeybindings.UNKNOWN || pressed != backToWall) {
         return;
      }

      if (SeedQueue.returnToWallFromJoinedWorld(client)) {
         if (screen != null) {
            client.gui.setScreen(null);
         }

         ci.cancel();
      }
   }
}
