package me.contaria.seedqueue.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a hand-over is waiting for the local player, the level already exists but {@code player} is
 * still null; ticking the game mode then throws inside {@code ensureHasSentCarriedItem}. Vanilla
 * avoids this because the receiving-level screen pauses the client tick, but the wall deliberately
 * skips that screen.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
   @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;tick()V"))
   private void seedqueue$skipGameModeTickWhileJoining(MultiPlayerGameMode gameMode, Operation<Void> original) {
      if (SeedQueue.suppressLoadingScreens && Minecraft.getInstance().player == null) {
         return;
      }

      original.call(gameMode);
   }
}
