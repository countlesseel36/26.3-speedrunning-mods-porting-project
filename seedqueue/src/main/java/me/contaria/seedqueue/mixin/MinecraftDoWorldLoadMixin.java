package me.contaria.seedqueue.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Function;
import me.contaria.seedqueue.QueueEntry;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.TimerCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hands a queued, already running integrated server to the client instead of starting a new one.
 *
 * <p>{@code Minecraft.doWorldLoad} normally calls {@code MinecraftServer.spin(factory)} to create
 * the integrated server, which then loads the world from disk and prepares spawn chunks behind the
 * loading screen. For a queue entry both already happened in the background, so the existing server
 * is returned here; {@code doWorldLoad} then only waits for {@code isReady()} (already true) and
 * connects the client, which is what makes the join instant.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDoWorldLoadMixin {
   @WrapOperation(
      method = "doWorldLoad(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/WorldStem;Ljava/util/Optional;Z)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/MinecraftServer;spin(Ljava/util/function/Function;)Lnet/minecraft/server/MinecraftServer;"
      )
   )
   private MinecraftServer seedqueue$takeQueuedServer(Function<Thread, MinecraftServer> factory, Operation<MinecraftServer> original) {
      QueueEntry entry = SeedQueue.joining;

      if (entry != null && entry.server != null) {
         SeedQueue.LOGGER.info("[seedqueue] adopting live integrated server for entry #{}", entry.number);
         // Start a fresh SpeedRunIGT timer for this world; otherwise the RTA timer of the previous
         // queued world keeps running (the hand-off does not go through IGT's normal join hook).
         TimerCompat.start(entry);
         return entry.server;
      }

      return original.call(factory);
   }
}
