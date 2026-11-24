package me.contaria.seedqueue.queue;

import me.contaria.seedqueue.SeedQueue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import me.voidxwalker.worldpreview.ExternalPreview;
import me.voidxwalker.worldpreview.WorldPreviewProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.WorldLoader.DataLoadOutput;
import net.minecraft.server.WorldLoader.InitConfig;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.level.progress.LevelLoadListener.Stage;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelSettings.DifficultySettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.WorldDimensions.Complete;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

/**
 * 实例的预览图捕获：地图俯视图、透视预览与静态卡片图上传。
 * 从 QueueEntry 拆分出来，行为保持不变。
 */
final class EntryCapture {
   private EntryCapture() {
   }

   static void captureMap(QueueEntry entry, ServerLevel level, int chunkX, int chunkZ) {
      try {
         int[] colors = new int[640];
         int[] heights = new int[640];
         int centerX = chunkX * 16 + 8;
         int centerZ = chunkZ * 16 + 8;
         byte step = 2;

         for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 32; j++) {
               int x = centerX + (j - 16) * step;
               int z = centerZ + (i - 10) * step;
               LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
               int surfaceY = chunk.getHeight(Types.WORLD_SURFACE, x & 15, z & 15);
               BlockState blockState = chunk.getBlockState(new BlockPos(x, Math.max(surfaceY - 1, level.getMinY()), z));
               colors[i * 32 + j] = colorFor(blockState);
               heights[i * 32 + j] = surfaceY;
            }
         }

         entry.mapHeights = heights;
         entry.mapColors = colors;
         entry.cameraX = (double)centerX + 0.5;
         entry.cameraZ = (double)centerZ + 0.5;
         entry.cameraY = (double)level.getHeight(Types.WORLD_SURFACE, centerX, centerZ) + 1.7;
         if (!Boolean.parseBoolean(System.getProperty("seedqueue.freezeCapture", "true"))) {
            entry.capturePerspective();
         }

         SeedQueue.LOGGER.info("[seedqueue] entry #{} captured preview map", entry.number);
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to capture preview map for #{}", entry.number, error);
      }
   }

   static void capturePerspective(QueueEntry entry) {
      try {
         int[] mapHeights = entry.mapHeights;
         int[] mapColors = entry.mapColors;
         if (mapHeights == null || mapColors == null) {
            return;
         }

         int[] pixels = new int[14400];
         double fovRadians = Math.toRadians(70.0);
         double focalLength = 160.0 / (2.0 * Math.tan(fovRadians / 2.0));
         double horizon = 40.5;
         double maxDistance = (double)Math.max(32, 20) * 2.0;

         for (int i = 0; i < 160; i++) {
            double angleX = Math.atan(((double)i - 80.0) / focalLength);
            double sinX = Math.sin(angleX);
            double cosX = Math.cos(angleX);

            for (int j = 0; j < 90; j++) {
               double angleY = Math.atan((horizon - (double)j) / focalLength);
               int pixel = skyColor(j);

               for (double distance = 1.0; distance <= maxDistance; distance++) {
                  double worldX = entry.cameraX + sinX * distance;
                  double worldZ = entry.cameraZ + cosX * distance;
                  double worldY = entry.cameraY + Math.tan(angleY) * distance;
                  int mapX = (int)Math.floor((worldX - (entry.cameraX - 32.0)) / 2.0);
                  int mapZ = (int)Math.floor((worldZ - (entry.cameraZ - 20.0)) / 2.0);
                  if (mapX >= 0 && mapX < 32 && mapZ >= 0 && mapZ < 20) {
                     int mapIndex = mapZ * 32 + mapX;
                     double heightAt = (double)mapHeights[mapIndex];
                     if (worldY <= heightAt) {
                        double fog = Math.max(0.35, 1.0 - distance / maxDistance);
                        pixel = applyFog(mapColors[mapIndex], fog);
                        break;
                     }
                  }
               }

               pixels[j * 160 + i] = pixel;
            }
         }

         SeedQueue.LOGGER.info("[seedqueue] rendered perspective preview for #{} (seed {}, {}x{})", entry.number, entry.seed, 160, 90);
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to render perspective preview for #{}", entry.number, error);
      }
   }

   static int skyColor(int count) {
      int channel = Math.max(0, Math.min(255, count * 255 / 90));
      int count3 = 120 + channel * 100 / 255;
      int count4 = 170 + channel * 75 / 255;
      short blue = 235;
      return 0xFF000000 | count3 << 16 | count4 << 8 | blue;
   }

   static int applyFog(int count, double ratio) {
      int count2 = (int)((double)(count >> 16 & 0xFF) * ratio);
      int count3 = (int)((double)(count >> 8 & 0xFF) * ratio);
      int count4 = (int)((double)(count & 0xFF) * ratio);
      return 0xFF000000 | count2 << 16 | count3 << 8 | count4;
   }

   static int colorFor(BlockState blockState) {
      String text = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).getPath();
      if (text.contains("water")) {
         return -12618012;
      } else if (text.contains("lava")) {
         return -2860526;
      } else if (text.contains("sand")) {
         return -1582944;
      } else if (text.contains("snow")) {
         return -986896;
      } else if (text.contains("grass") || text.contains("leaves") || text.contains("moss")) {
         return -10777538;
      } else if (text.contains("log") || text.contains("wood")) {
         return -9744598;
      } else if (text.contains("stone") || text.contains("deepslate") || text.contains("gravel")) {
         return -7829368;
      } else {
         return text.contains("dirt") ? -7640246 : -8750470;
      }
   }

   static void uploadNativePreview(QueueEntry entry) {
      if (entry.nativePreview != null) {
         try {
            NativeImage image = entry.nativePreview;
            entry.nativePreview = null;
            if (Boolean.getBoolean("seedqueue.dumpCaptures")) {
               try {
                  image.writeToFile(new File("seedqueue_capture_" + entry.number + ".png"));
               } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }
            }

            entry.captureWidth = image.getWidth();
            entry.captureHeight = image.getHeight();
            DynamicTexture texture = new DynamicTexture(() -> "seedqueue preview", image);
            Identifier identifier = Identifier.fromNamespaceAndPath("seedqueue", "capture_" + entry.number + "_" + entry.previewUploads++);
            Minecraft minecraft = Minecraft.getInstance();
            if (entry.previousPreviewTexture != null) {
               try {
                  minecraft.getTextureManager().release(entry.previousPreviewTexture);
               } catch (Throwable error2) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error2);
      }
            }

            entry.previousPreviewTexture = entry.previewTexture;
            minecraft.getTextureManager().register(identifier, texture);
            entry.previewTexture = identifier;
            SeedQueue.LOGGER.info("[seedqueue] uploaded captured preview for entry #{}", entry.number);
         } catch (Throwable error3) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to upload captured preview for #{}", entry.number, error3);
         }
      }
   }
}
