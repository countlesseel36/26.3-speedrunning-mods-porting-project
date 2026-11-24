package me.contaria.seedqueue.mixin;

import java.util.List;
import java.util.Set;
import me.contaria.seedqueue.config.QueueConfig;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelCandidates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({LevelStorageSource.class})
public class LevelListMixin {
   @Inject(
      method = {"findLevelCandidates"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void seedqueue$filter(CallbackInfoReturnable<LevelCandidates> callback) {
      if (QueueConfig.reduceLevelList) {
         LevelCandidates levelCandidates = (LevelCandidates)callback.getReturnValue();
         if (levelCandidates != null && !levelCandidates.isEmpty()) {
            Set set = SeedQueue.activeWorldFolders();
            List list = levelCandidates.levels().stream().filter(arg -> !set.contains(arg.directoryName())).toList();
            if (list.size() != levelCandidates.levels().size()) {
               callback.setReturnValue(new LevelCandidates(list));
            }
         }
      }
   }
}
