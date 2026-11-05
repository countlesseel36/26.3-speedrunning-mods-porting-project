package me.contaria.standardsettings.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import me.contaria.standardsettings.StandardSettings;
import me.contaria.standardsettings.interfaces.LevelLoadingScreenEx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
   @Shadow
   private IntegratedServer singleplayerServer;

   @Shadow
   public abstract boolean isWindowActive();

   @Shadow
   public abstract void pauseGame(boolean var1);

   @Inject(method = "doWorldLoad", at = @At("HEAD"))
   private void reset(
      LevelStorageAccess session, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, boolean newWorld, CallbackInfo ci
   ) {
      if (Minecraft.getInstance().isSameThread()) {
         if (newWorld) {
            StandardSettings.createCache();
            if (StandardSettings.isEnabled()) {
               StandardSettings.reset();
            }
         } else {
            StandardSettings.loadCache(session.getLevelId());
         }

         StandardSettings.resetPendingActions();
      }
   }

   @ModifyExpressionValue(
      method = "doWorldLoad",
      at = @At(
         value = "NEW",
         target = "(Lnet/minecraft/client/multiplayer/LevelLoadTracker;Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;)Lnet/minecraft/client/gui/screens/LevelLoadingScreen;"
      )
   )
   private LevelLoadingScreen setupOnWorldJoin(
      LevelLoadingScreen original, @Local(argsOnly = true) boolean newWorld, @Local(argsOnly = true) LevelStorageAccess session
   ) {
      LevelLoadingScreenEx tracker = (LevelLoadingScreenEx)original;
      tracker.standardsettings$setLevelId(session.getLevelId());
      tracker.standardsettings$setNewWorld(newWorld);
      return original;
   }

   @Inject(method = "resizeGui", at = @At("RETURN"))
   private void onWorldJoin_onResize(CallbackInfo ci) {
      if (StandardSettings.onWorldJoinPending && StandardSettings.config.triggerOnResize) {
         StandardSettings.onWorldJoin();
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void autoF3Esc(CallbackInfo ci) {
      StandardSettings.autoF3EscPending = StandardSettings.autoF3EscPending & (this.singleplayerServer != null && !this.isWindowActive());
      if (StandardSettings.autoF3EscPending) {
         if (StandardSettings.config.autoF3EscDelay > 0) {
            StandardSettings.config.autoF3EscDelay--;
         } else {
            this.pauseGame(true);
         }
      }
   }
}
