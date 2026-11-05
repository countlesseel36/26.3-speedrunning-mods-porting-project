package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the preview world is bound, dirty-section notifications would be forwarded to the real
 * section renderer (e.g. Sodium's SodiumWorldRenderer), which has no section manager yet during
 * world loading and throws.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(
      method = "extract",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;applyFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)V",
         shift = At.Shift.AFTER
      )
   )
   private void worldpreview$fillVisibleSections(DeltaTracker deltaTracker, Camera camera, float partialTicks, CallbackInfo ci) {
      WorldPreview.populateVisibleSections(this.minecraft);
   }

   @Inject(method = "setSectionRangeDirty(IIIIII)V", at = @At("HEAD"), cancellable = true)
   private void worldpreview$skipRangeDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
      if (worldpreview$guarded()) {
         ci.cancel();
      }
   }

   @Inject(method = "setSectionDirty(III)V", at = @At("HEAD"), cancellable = true)
   private void worldpreview$skipDirty(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
      if (worldpreview$guarded()) {
         ci.cancel();
      }
   }

   @Inject(method = "setSectionDirtyWithNeighbors(III)V", at = @At("HEAD"), cancellable = true)
   private void worldpreview$skipDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
      if (worldpreview$guarded()) {
         ci.cancel();
      }
   }

   @Inject(method = "blockChanged(Lnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"), cancellable = true)
   private void worldpreview$skipBlockChanged(net.minecraft.core.BlockPos pos, int flags, CallbackInfo ci) {
      if (worldpreview$guarded()) {
         ci.cancel();
      }
   }

   @Inject(
      method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void worldpreview$skipSetBlockDirty(
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.level.block.state.BlockState oldState,
      net.minecraft.world.level.block.state.BlockState newState,
      CallbackInfo ci
   ) {
      if (worldpreview$guarded()) {
         ci.cancel();
      }
   }

   private static boolean worldpreview$guarded() {
      return (WorldPreview.isActive() || WorldPreview.isBuilding())
         && me.voidxwalker.worldpreview.SodiumCompat.PRESENT
         && !me.voidxwalker.worldpreview.SodiumCompat.isDriving();
   }
}
