package fast_reset.client.mixin;

import fast_reset.client.FastReset;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Skips the three save steps of {@code MinecraftServer#stopServer}: player data, the
 * wait-for-chunk-work loop and the final chunk save.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
   @Redirect(
      method = "stopServer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;saveAll()V")
   )
   private void fastreset$skipPlayerSave(PlayerList playerList) {
      if (FastReset.saveOnQuit) {
         playerList.saveAll();
      }
   }

   @Redirect(
      method = "stopServer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z")
   )
   private boolean fastreset$skipChunkSave(MinecraftServer server, boolean silent, boolean flush, boolean force) {
      return FastReset.saveOnQuit && server.saveAllChunks(silent, flush, force);
   }

   @Redirect(
      method = "stopServer",
      at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z")
   )
   private boolean fastreset$skipChunkWait(Stream<ServerLevel> stream, Predicate<? super ServerLevel> predicate) {
      return FastReset.saveOnQuit && stream.anyMatch(predicate);
   }
}
