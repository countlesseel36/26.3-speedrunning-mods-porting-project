package me.contaria.seedqueue.mixin;

import java.util.List;
import me.contaria.seedqueue.QueueConfig;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the SeedQueue scratch worlds from the vanilla world list (1.21.4 reduceLevelList). */
@Mixin(LevelStorageSource.class)
public class LevelListMixin {
   @Inject(method = "findLevelCandidates", at = @At("RETURN"), cancellable = true)
   private void seedqueue$filter(CallbackInfoReturnable<LevelStorageSource.LevelCandidates> cir) {
      if (!QueueConfig.reduceLevelList) {
         return;
      }

      LevelStorageSource.LevelCandidates candidates = cir.getReturnValue();

      if (candidates == null || candidates.isEmpty()) {
         return;
      }

      // Only hide the worlds that are currently being queued. Worlds that were already played are
      // transferred out of the queue and must stay visible in the singleplayer list.
      java.util.Set<String> active = me.contaria.seedqueue.SeedQueue.activeWorldFolders();
      List<LevelStorageSource.LevelDirectory> filtered = candidates
         .levels()
         .stream()
         .filter(directory -> !active.contains(directory.directoryName()))
         .toList();

      if (filtered.size() != candidates.levels().size()) {
         cir.setReturnValue(new LevelStorageSource.LevelCandidates(filtered));
      }
   }
}
