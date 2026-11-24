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
 * 实例的世界数据：Atum 世界选项、存档目录判断与删除。
 * 从 QueueEntry 拆分出来，行为保持不变。
 */
final class EntryWorldData {
   private EntryWorldData() {
   }

   static int atumDifficultyId() {
      try {
         Class clazz = Class.forName("me.voidxwalker.autoreset.Atum");
         return clazz.getField("difficulty").getInt(null);
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] could not read Atum difficulty, defaulting to EASY", error);
         return 1;
      }
   }

   static int atumGeneratorType() {
      try {
         Class clazz = Class.forName("me.voidxwalker.autoreset.Atum");
         return clazz.getField("generatorType").getInt(null);
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] could not read Atum generatorType, using NORMAL", error);
         return 0;
      }
   }

   static Holder<WorldPreset> selectPreset(RegistryLookup<WorldPreset> holder, int count) {
      String text = switch (count) {
         case 0 -> "normal";
         case 1 -> "flat";
         case 2 -> "large_biomes";
         case 3 -> "amplified";
         case 4 -> "single_biome_surface";
         case 5 -> "single_biome_caves";
         case 6 -> "single_biome_floating_islands";
         default -> null;
      };
      java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.level.levelgen.presets.WorldPreset>> list = holder.listElements().toList();
      if (text != null) {
         for (net.minecraft.core.Holder.Reference<net.minecraft.world.level.levelgen.presets.WorldPreset> holderReference : list) {
            if (holderReference.unwrapKey().map(preset -> preset.identifier().getPath().equals(text)).orElse(false)) {
               return holderReference;
            }
         }
      }

      java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.level.levelgen.presets.WorldPreset>> list2 = list.stream().filter(preset -> preset.is(WorldPresetTags.NORMAL)).toList();
      java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.level.levelgen.presets.WorldPreset>> list3 = list.stream().filter(preset -> preset.is(WorldPresetTags.EXTENDED)).toList();
      if (count >= 0 && count < list2.size()) {
         return (Holder<WorldPreset>)list2.get(count);
      } else {
         int count2 = count - list2.size();
         if (count2 >= 0 && count2 < list3.size()) {
            return (Holder<WorldPreset>)list3.get(count2);
         } else {
            return list2.isEmpty() ? null : (Holder)list2.get(0);
         }
      }
   }

   static Difficulty difficultyOf(int count) {
      return switch (count) {
         case -1, 3 -> Difficulty.HARD;
         case 0 -> Difficulty.PEACEFUL;
         default -> Difficulty.EASY;
         case 2 -> Difficulty.NORMAL;
      };
   }

   public static boolean hasPlayerData(Path path) {
      try {
         if (Files.exists(path.resolve("seedqueue_played.marker"))) {
            return true;
         } else {
            Path path2 = path.resolve("level.dat");
            if (Files.isRegularFile(path2) && Files.size(path2) > 1024L) {
               return true;
            } else {
               Path path3 = path.resolve("players");
               if (!Files.isDirectory(path3)) {
                  return false;
               } else {
                  boolean flag;
                  try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(path3)) {
                     flag = stream.anyMatch(arg -> Files.isRegularFile(arg));
                  }

                  return flag;
               }
            }
         }
      } catch (Throwable error) {
         return true;
      }
   }

   static void deleteFolderIfUnplayed(QueueEntry entry) {
      try {
         if (entry.worldDirectory == null || !Files.exists(entry.worldDirectory)) {
            return;
         }

         if (hasPlayerData(entry.worldDirectory)) {
            SeedQueue.LOGGER.info("[seedqueue] keeping {} (played or contains player data)", entry.worldDirectory.getFileName());
            return;
         }

         entry.deleteWorldFolder();
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to clean up entry #{}", entry.number, error);
      }
   }

   static void deleteWorldFolder(QueueEntry entry) {
      if (entry.worldDirectory != null) {
         SeedQueue.enqueueWorldDeletion(entry.worldDirectory);
      }
   }
}
