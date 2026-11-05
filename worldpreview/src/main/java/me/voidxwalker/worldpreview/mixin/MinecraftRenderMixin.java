package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.PreviewBuilder;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the preview world in for the whole frame (extract + render). 26.3 renders the level in two
 * passes and both of them read {@code Minecraft.level}, so the swap has to cover both.
 */
@Mixin(Minecraft.class)
public class MinecraftRenderMixin {
   @Unique
   private static final boolean WORLDPREVIEW_SMOKE = Boolean.getBoolean("worldpreview.smoke");
   @Unique
   private static final boolean WORLDPREVIEW_SKINCHECK = Boolean.getBoolean("worldpreview.skincheck");
   @Unique
   private static final boolean WORLDPREVIEW_DEBUGFRAMES = Boolean.getBoolean("worldpreview.debugframes");
   @Unique
   private static final String WORLDPREVIEW_OPENTEST = System.getProperty("worldpreview.opentest");
   @Unique
   private static int worldpreview$frame;
   @Unique
   private static int worldpreview$activeFrames;
   @Unique
   private static int worldpreview$postTicks;
   @Unique
   private static int worldpreview$postFrames;
   @Unique
   private static int worldpreview$sequenceShots;
   @Unique
   private static boolean worldpreview$skinLogged;
   @Unique
   private static boolean worldpreview$skinShot;
   @Unique
   private static boolean worldpreview$itemSpawned;
   @Unique
   private static boolean worldpreview$openTestDone;

   @Inject(method = "tick", at = @At("HEAD"))
   private void worldpreview$tick(CallbackInfo ci) {
      Minecraft minecraft = (Minecraft) (Object) this;
      WorldPreview.checkRealLevel(minecraft);
      WorldPreview.ensureRealLevelBound(minecraft);
      WorldPreview.tick(minecraft);
   }

   @Inject(method = "renderFrame", at = @At("HEAD"))
   private void worldpreview$swapIn(boolean advanceGameTime, CallbackInfo ci) {
      Minecraft minecraft = (Minecraft) (Object) this;
      worldpreview$frame++;

      if (WORLDPREVIEW_OPENTEST != null && !worldpreview$openTestDone && minecraft.gui.screen() instanceof TitleScreen) {
         worldpreview$openTestDone = true;
         WorldPreview.LOGGER.info("[preview] opentest: opening existing world {}", WORLDPREVIEW_OPENTEST);
         minecraft.createWorldOpenFlows().openWorld(WORLDPREVIEW_OPENTEST, () -> { });
      }

      if (!WorldPreview.isActive() && worldpreview$frame % 10 == 0) {
         WorldPreview.tickPending(minecraft);
      }

      if (WORLDPREVIEW_DEBUGFRAMES && WorldPreview.isActive() && worldpreview$activeFrames < 160) {
         WorldPreview.LOGGER.info(
            "[preview] frame {}: active={}, final={}, hide={}, screen={}, level={}",
            worldpreview$activeFrames,
            WorldPreview.isActive(),
            WorldPreview.hasFinalSpawn(),
            WorldPreview.shouldHideBackground(),
            minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getSimpleName(),
            minecraft.level == null ? "null"
               : (WorldPreview.properties() != null && minecraft.level == WorldPreview.properties().level ? "preview" : "real")
         );
      }

      if (WorldPreview.frameTickHook != null) {
         WorldPreview.frameTickHook.run();
      }

      WorldPreview.swapIn(minecraft);
      WorldPreview.populateVisibleSections(minecraft);

      if (WorldPreview.isActive() && worldpreview$frame % 10 == 0) {
         PreviewBuilder.refresh(WorldPreview.properties());
      }
   }

   @Inject(method = "renderFrame", at = @At("RETURN"))
   private void worldpreview$swapOut(boolean advanceGameTime, CallbackInfo ci) {
      Minecraft minecraft = (Minecraft) (Object) this;
      WorldPreview.swapOut(minecraft);
      WorldPreview.maybeComplete(minecraft);

      if (!WorldPreview.isActive() && WorldPreview.shouldHideBackground()) {
         worldpreview$postFrames++;
         if (WORLDPREVIEW_SMOKE && (worldpreview$postFrames == 1 || worldpreview$postFrames == 3 || worldpreview$postFrames == 6)) {
            Screenshot.grab(minecraft, false);
            WorldPreview.LOGGER.info("[preview] captured hand-over frame {}", worldpreview$postFrames);
         }
      } else if (WorldPreview.isActive()) {
         worldpreview$postFrames = 0;
      }

      if (WorldPreview.isActive()) {
         worldpreview$activeFrames++;
         worldpreview$postTicks = 0;

         if (WORLDPREVIEW_SMOKE && (worldpreview$activeFrames == 30 || worldpreview$activeFrames == 60
            || worldpreview$activeFrames == 90 || worldpreview$activeFrames == 120)) {
            Screenshot.grab(minecraft, false);
         }

         if (WORLDPREVIEW_SMOKE && worldpreview$frame % 8 == 0 && worldpreview$sequenceShots < 40
            && minecraft.gui.screen() instanceof LevelLoadingScreen) {
            worldpreview$sequenceShots++;
            Screenshot.grab(minecraft, false);
            WorldPreview.LOGGER.info("[preview] sequence frame {}: active={}, final={}", worldpreview$sequenceShots,
               WorldPreview.isActive(), WorldPreview.hasFinalSpawn());
         }

         return;
      }

      if (!WORLDPREVIEW_SKINCHECK || minecraft.player == null || minecraft.level == null) {
         return;
      }

      worldpreview$postTicks++;

      if (worldpreview$postTicks == 20 && !worldpreview$itemSpawned) {
         worldpreview$itemSpawned = true;
         worldpreview$testProbe(minecraft);
      }

      if (worldpreview$postTicks == 26 && !worldpreview$skinShot) {
         worldpreview$skinShot = true;
         Screenshot.grab(minecraft, false);
         var entity = minecraft.level == null ? null : minecraft.level.getEntity(999999);
         WorldPreview.LOGGER.info("[preview] captured entity/crack probe frame, item={}", entity == null ? "missing" : entity.position());
      }
   }

   @Unique
   private static void worldpreview$testProbe(Minecraft minecraft) {
      try {
         var player = minecraft.player;
         var level = minecraft.level;

         if (player == null || level == null) {
            return;
         }

         var direction = player.getDirection();
         var pos = player.blockPosition().relative(direction, 2).above();
         level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);

         var item = new ItemEntity(
            level, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, new ItemStack(Items.DIAMOND)
         );
         item.setId(999999);
         level.addEntity(item);

         WorldPreview.LOGGER.info("[preview] probe: block={} dir={} item={} registered={}", pos, direction, item.position(), level.getEntity(999999) != null);

         if (minecraft.gameMode != null) {
            minecraft.gameMode.startDestroyBlock(pos, Direction.UP);

            for (int i = 0; i < 20; i++) {
               minecraft.gameMode.continueDestroyBlock(pos, Direction.UP);
            }

            WorldPreview.LOGGER.info("[preview] probe: crack started");
         }
      } catch (Throwable t) {
         WorldPreview.LOGGER.warn("[preview] probe failed", t);
      }
   }
}