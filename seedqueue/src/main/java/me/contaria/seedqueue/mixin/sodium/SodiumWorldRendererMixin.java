package me.contaria.seedqueue.mixin.sodium;

import me.contaria.seedqueue.preview.PreviewState;
import me.contaria.seedqueue.config.QueueConfig;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.render.SodiumLevelCache;
import me.contaria.seedqueue.wall.WallScreen;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SodiumWorldRenderer.class})
public abstract class SodiumWorldRendererMixin {
   @Shadow
   private ClientLevel level;
   @Shadow
   private int renderDistance;
   @Shadow
   private RenderSectionManager renderSectionManager;
   private static final boolean CACHE = Boolean.getBoolean("seedqueue.sodiumCache");
   @Shadow
   private UniformBufferManager uniformBufferManager;

   @Inject(
      method = {"setLevel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$switchToCachedManager(ClientLevel level, CallbackInfo callback) {
      if (level != null && (SodiumLevelCache.isPreview(level) || Minecraft.getInstance().gui.screen() instanceof WallScreen)) {
         SodiumLevelCache.setThreadLimit(QueueConfig.chunkWorkers());
      }

      if (CACHE) {
         if (this.level == level) {
            callback.cancel();
         } else {
            boolean flag = this.level != null && SodiumLevelCache.isPreview(this.level);
            if (level == null) {
               if (flag && this.renderSectionManager != null) {
                  SodiumLevelCache.put(this.level, this.renderSectionManager);
                  this.renderSectionManager = null;
                  this.level = null;
                  callback.cancel();
               }
            } else if ((SodiumLevelCache.isPreview(level) ? SodiumLevelCache.get(level) : null) instanceof RenderSectionManager renderSectionManager) {
               if (this.renderSectionManager != null && this.renderSectionManager != renderSectionManager) {
                  if (flag) {
                     SodiumLevelCache.put(this.level, this.renderSectionManager);
                  } else {
                     try {
                        this.renderSectionManager.destroy();
                     } catch (Throwable error) {
                        SeedQueue.LOGGER.warn("[seedqueue] failed to destroy replaced sodium manager", error);
                     }

                     this.renderSectionManager = null;
                  }
               }

               this.level = level;
               this.renderSectionManager = renderSectionManager;
               int count = Minecraft.getInstance().options.getEffectiveRenderDistance();
               int count2 = PreviewState.renderDistance;
               boolean flag2 = level != Minecraft.getInstance().level;
               this.renderDistance = count2 > 0 && flag2 ? Math.min(count, count2) : count;
               if (this.uniformBufferManager == null) {
                  this.uniformBufferManager = new UniformBufferManager(this.level, this.renderDistance);
               }

               if (Boolean.getBoolean("seedqueue.sodiumLog")) {
                  SeedQueue.LOGGER.info("[seedqueue] sodium: reused cached section manager");
               }

               callback.cancel();
            } else {
               if (SodiumLevelCache.isPreview(level) || Minecraft.getInstance().gui.screen() instanceof WallScreen) {
                  SodiumLevelCache.setThreadLimit(QueueConfig.chunkWorkers());
               }
            }
         }
      }
   }

   @Inject(
      method = {"setLevel"},
      at = {@At("RETURN")}
   )
   private void seedqueue$rememberNewManager(ClientLevel level, CallbackInfo callback) {
      SodiumLevelCache.clearThreadLimit();
      if (level != null && this.renderSectionManager != null) {
         SodiumLevelCache.put(level, this.renderSectionManager);
         if (Boolean.getBoolean("seedqueue.sodiumLog")) {
            SeedQueue.LOGGER
               .info("[seedqueue] sodium: created new section manager (preview={}, cached={})", SodiumLevelCache.isPreview(level), SodiumLevelCache.size());
         }
      }
   }

   @Inject(
      method = {"scheduleRebuildForChunk"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$skipRebuildWithoutManager(int count, int count2, int count3, boolean flag, CallbackInfo callback) {
      if (this.renderSectionManager == null) {
         callback.cancel();
      }
   }

   @Inject(
      method = {"setupTerrain"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$skipSetupWithoutState(CallbackInfo callback) {
      if (this.renderSectionManager == null || this.uniformBufferManager == null) {
         callback.cancel();
      }
   }

   @Inject(
      method = {"renderLayer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$skipRenderWithoutState(CallbackInfo callback) {
      if (this.renderSectionManager == null || this.uniformBufferManager == null) {
         callback.cancel();
      }
   }

   @Inject(
      method = {"reload"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$keepPreviewManagerOnReload(CallbackInfo callback) {
      if (CACHE) {
         if (this.level != null && SodiumLevelCache.isPreview(this.level)) {
            callback.cancel();
         } else {
            if (this.level != null) {
               SodiumLevelCache.remove(this.level);
            }
         }
      }
   }
}
