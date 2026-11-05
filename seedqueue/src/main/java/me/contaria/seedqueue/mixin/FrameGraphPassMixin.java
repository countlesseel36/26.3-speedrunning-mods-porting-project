package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.WallPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wraps the task of every frame-graph pass so the log shows exactly which passes execute for an
 * instance render (the graph culls passes that no external output depends on).
 */
@Mixin(targets = "com.mojang.blaze3d.framegraph.FrameGraphBuilder$Pass")
public class FrameGraphPassMixin {
   @ModifyVariable(method = "executes(Ljava/lang/Runnable;)V", at = @At("HEAD"), argsOnly = true)
   private Runnable seedqueue$wrapTask(Runnable task) {
      return WallPreview.wrapPass(task, String.valueOf(this));
   }
}
