package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

@Mixin({Minecraft.class})
public abstract class MinecraftSetLevelMixin {
   @Inject(
      method = {"setLevel"},
      at = {@At("TAIL")}
   )
   private void seedqueue$closeWallAfterJoin(ClientLevel level, CallbackInfo callback) {
      if (level != null) {
         SeedQueue.onLevelSet();
      }
   }
}
