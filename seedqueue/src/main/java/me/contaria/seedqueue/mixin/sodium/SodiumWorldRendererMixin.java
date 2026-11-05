package me.contaria.seedqueue.mixin.sodium;

import me.contaria.seedqueue.SodiumLevelCache;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium multi-manager patch: instead of letting {@code setLevel} destroy the previous
 * {@link RenderSectionManager}, keep it cached per level and swap the manager field directly when
 * returning to a level the wall already rendered.
 */
@Mixin(SodiumWorldRenderer.class)
public abstract class SodiumWorldRendererMixin {
   @Shadow
   private ClientLevel level;

   @Shadow
   private int renderDistance;

   @Shadow
   private RenderSectionManager renderSectionManager;

   private static final boolean CACHE = Boolean.getBoolean("seedqueue.sodiumCache");

   @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
   private void seedqueue$switchToCachedManager(ClientLevel target, CallbackInfo ci) {
      // Cap the preview chunk builder before the manager is created. This used to live behind the
      // manager cache flag, which meant every wall preview started a full size worker pool
      // (10 threads plus its own chunk build buffers) that was thrown away again on the next swap.
      if (target != null
         && (SodiumLevelCache.isPreview(target)
            || Minecraft.getInstance().gui.screen() instanceof me.contaria.seedqueue.WallScreen)) {
         SodiumLevelCache.setThreadLimit(me.contaria.seedqueue.QueueConfig.chunkWorkers());
      }

      if (!CACHE) {
         return;
      }

      if (this.level == target) {
         ci.cancel();
         return;
      }

      boolean oldIsPreview = this.level != null && SodiumLevelCache.isPreview(this.level);

      if (target == null) {
         // Keep managers for preview levels only; real levels go through Sodium's normal unload so
         // its uniform buffer state stays consistent.
         if (oldIsPreview && this.renderSectionManager != null) {
            SodiumLevelCache.put(this.level, this.renderSectionManager);
            this.renderSectionManager = null;
            this.level = null;
            ci.cancel();
         }

         return;
      }

      Object cached = SodiumLevelCache.isPreview(target) ? SodiumLevelCache.get(target) : null;

      if (cached instanceof RenderSectionManager manager) {
         if (this.renderSectionManager != null && this.renderSectionManager != manager) {
            if (oldIsPreview) {
               SodiumLevelCache.put(this.level, this.renderSectionManager);
            } else {
               try {
                  this.renderSectionManager.destroy();
               } catch (Throwable t) {
                  me.contaria.seedqueue.SeedQueue.LOGGER.warn("[seedqueue] failed to destroy replaced sodium manager", t);
               }

               // The field must not keep pointing at a destroyed manager: Sodium would destroy it
               // again on the next level switch ("Worker threads are not running").
               this.renderSectionManager = null;
            }
         }

         this.level = target;
         this.renderSectionManager = manager;
         this.renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();

         // Sodium deletes the uniform buffer manager when it unloads a real level. Reusing a cached
         // preview manager would otherwise render with a null uniform buffer and crash in setupTerrain.
         if (this.uniformBufferManager == null) {
            this.uniformBufferManager = new net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager(
               this.level, this.renderDistance
            );
         }

         if (Boolean.getBoolean("seedqueue.sodiumLog")) {
            me.contaria.seedqueue.SeedQueue.LOGGER.info("[seedqueue] sodium: reused cached section manager");
         }

         ci.cancel();
         return;
      }

      if (SodiumLevelCache.isPreview(target)
         || Minecraft.getInstance().gui.screen() instanceof me.contaria.seedqueue.WallScreen) {
         // New preview manager: cap its chunk builder threads so several cached managers stay cheap.
         SodiumLevelCache.setThreadLimit(me.contaria.seedqueue.QueueConfig.chunkWorkers());
      }
   }

   @Inject(method = "setLevel", at = @At("RETURN"))
   private void seedqueue$rememberNewManager(ClientLevel target, CallbackInfo ci) {
      SodiumLevelCache.clearThreadLimit();

      if (target != null && this.renderSectionManager != null) {
         SodiumLevelCache.put(target, this.renderSectionManager);
         if (Boolean.getBoolean("seedqueue.sodiumLog")) {
            me.contaria.seedqueue.SeedQueue.LOGGER.info(
               "[seedqueue] sodium: created new section manager (preview={}, cached={})",
               SodiumLevelCache.isPreview(target),
               SodiumLevelCache.size()
            );
         }
      }
   }

   /** Sodium still receives chunk-light updates for a level whose renderer state was detached. */
   @Inject(method = "scheduleRebuildForChunk", at = @At("HEAD"), cancellable = true)
   private void seedqueue$skipRebuildWithoutManager(int x, int y, int z, boolean important, CallbackInfo ci) {
      if (this.renderSectionManager == null) {
         ci.cancel();
      }
   }

   /** Defensive: culling can race a level switch on the render thread. */
   @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
   private void seedqueue$skipSetupWithoutState(CallbackInfo ci) {
      if (this.renderSectionManager == null || this.uniformBufferManager == null) {
         ci.cancel();
      }
   }

   /** Defensive: rendering can race a level switch on the render thread. */
   @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
   private void seedqueue$skipRenderWithoutState(CallbackInfo ci) {
      if (this.renderSectionManager == null || this.uniformBufferManager == null) {
         ci.cancel();
      }
   }

   @Shadow
   private net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager uniformBufferManager;

   @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
   private void seedqueue$keepPreviewManagerOnReload(CallbackInfo ci) {
      if (!CACHE) {
         return;
      }

      if (this.level != null && SodiumLevelCache.isPreview(this.level)) {
         // LevelRenderer#allChanged (called on every level switch) triggers a Sodium reload, which
         // would destroy and recreate the manager we just reused from the cache. Keep the compiled
         // geometry for preview levels; it is still valid for the same level.
         ci.cancel();
         return;
      }

      // Real levels: reload destroys and recreates the manager, so drop the stale cache entry.
      if (this.level != null) {
         SodiumLevelCache.remove(this.level);
      }
   }
}
