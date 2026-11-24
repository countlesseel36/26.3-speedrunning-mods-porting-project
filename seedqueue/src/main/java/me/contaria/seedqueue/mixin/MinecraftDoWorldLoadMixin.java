package me.contaria.seedqueue.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Function;
import me.contaria.seedqueue.queue.QueueEntry;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.render.TimerCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({Minecraft.class})
public abstract class MinecraftDoWorldLoadMixin {
   @WrapOperation(
      method = {"doWorldLoad(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/WorldStem;Ljava/util/Optional;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/MinecraftServer;spin(Ljava/util/function/Function;)Lnet/minecraft/server/MinecraftServer;"
      )}
   )
   private MinecraftServer seedqueue$takeQueuedServer(Function<Thread, MinecraftServer> function, Operation<MinecraftServer> operation) {
      QueueEntry entry = SeedQueue.joining;
      if (entry != null && entry.server != null) {
         SeedQueue.LOGGER.info("[seedqueue] adopting live integrated server for entry #{}", entry.number);
         TimerCompat.start(entry);
         return entry.server;
      } else {
         return (MinecraftServer)operation.call(new Object[]{function});
      }
   }
}
