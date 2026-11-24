package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.wall.WallPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin(
   targets = {"com.mojang.blaze3d.framegraph.FrameGraphBuilder$Pass"}
)
public class FrameGraphPassMixin {
   @ModifyVariable(
      method = {"executes(Ljava/lang/Runnable;)V"},
      at = {@At("HEAD")},
      argsOnly = true
   )
   private Runnable seedqueue$wrapTask(Runnable runnable) {
      return WallPreview.wrapPass(runnable, String.valueOf(this));
   }
}
