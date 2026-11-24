package me.contaria.seedqueue.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({Minecraft.class})
public abstract class MinecraftTickMixin {
   @WrapOperation(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;tick()V"
      )}
   )
   private void seedqueue$skipGameModeTickWhileJoining(MultiPlayerGameMode multiPlayerGameMode, Operation<Void> operation) {
      if (!SeedQueue.suppressLoadingScreens || Minecraft.getInstance().player != null) {
         operation.call(new Object[]{multiPlayerGameMode});
      }
   }
}
