package me.contaria.seedqueue.queue;
import me.contaria.seedqueue.SeedQueue;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.WorldLoader.DataLoadOutput;
import net.minecraft.server.WorldLoader.InitConfig;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.level.progress.LevelLoadListener.Stage;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelSettings.DifficultySettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldDimensions.Complete;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import me.contaria.seedqueue.queue.*;

public final class Spike {
   private static boolean started;

   private Spike() {
   }

   public static void tick(Minecraft minecraft) {
      if (!started && Boolean.getBoolean("seedqueue.spike")) {
         if (minecraft.gui.screen() instanceof TitleScreen) {
            started = true;
            Thread thread = new Thread(Spike::run, "SeedQueue spike");
            thread.setDaemon(true);
            thread.start();
         }
      }
   }

   private static void run() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         String text = "SeedQueue-Spike-" + System.currentTimeMillis();
         LevelStorageAccess levelStorageAccess = minecraft.getLevelSource().validateAndCreateAccess(text);
         PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorageAccess);
         LevelSettings levelSettings = new LevelSettings(text, GameType.SURVIVAL, DifficultySettings.DEFAULT, false, WorldDataConfiguration.DEFAULT);
         WorldOptions worldOptions = WorldOptions.defaultWithRandomSeed();
         PackConfig packConfig = new PackConfig(packRepository, levelSettings.dataConfiguration(), false, false);
         InitConfig initConfig = new InitConfig(packConfig, CommandSelection.INTEGRATED, PermissionSet.ALL_PERMISSIONS);
         long value = System.currentTimeMillis();
         WorldStem worldStem = (WorldStem)WorldLoader.load(
               initConfig,
               arg -> {
                  WorldDimensions worldDimensions = ((WorldPreset)arg.datapackWorldRegistries()
                        .lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.NORMAL)
                        .value())
                     .createWorldDimensions();
                  Complete complete = worldDimensions.bake(arg.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
                  PrimaryLevelData primaryLevelData = new PrimaryLevelData(levelSettings, complete.specialWorldProperty(), complete.lifecycle());
                  WorldGenSettings worldGenSettings = new WorldGenSettings(worldOptions, worldDimensions);
                  return new DataLoadOutput(new WorldDataAndGenSettings(primaryLevelData, worldGenSettings), complete.dimensionsRegistryAccess());
               },
               WorldStem::new,
               Util.backgroundExecutor(),
               Util.backgroundExecutor()
            )
            .get();
         levelStorageAccess.saveDataTag(worldStem.worldDataAndGenSettings().data());
         SeedQueue.LOGGER.info("[seedqueue] spike: created world stem for {} in {} ms (seed {})", text, System.currentTimeMillis() - value, worldOptions.seed());
         LevelLoadListener listener = new LevelLoadListener() {
            public void start(Stage text, int levelStorageAccess) {
               SeedQueue.LOGGER.info("[seedqueue] spike: load start {} {}", text, levelStorageAccess);
            }

            public void update(Stage text, int levelStorageAccess, int packRepository) {
            }

            public void finish(Stage text) {
               SeedQueue.LOGGER.info("[seedqueue] spike: load finish {}", text);
            }

            public void updateFocus(ResourceKey<Level> text, ChunkPos levelStorageAccess) {
            }
         };
         long value2 = System.currentTimeMillis();
         IntegratedServer integratedServer = (IntegratedServer)MinecraftServer.spin(primaryLevelData -> {
            primaryLevelData.setName("SeedQueue spike server");
            return new IntegratedServer(primaryLevelData, minecraft, levelStorageAccess, packRepository, worldStem, Optional.empty(), minecraft.services(), listener);
         });
         long value3 = System.currentTimeMillis() + 30000L;

         while (!integratedServer.isReady() && System.currentTimeMillis() < value3) {
            Thread.sleep(100L);
         }

         SeedQueue.LOGGER.info("[seedqueue] spike: server ready={} after {} ms", integratedServer.isReady(), System.currentTimeMillis() - value2);
         integratedServer.halt(false);
      } catch (Throwable error) {
         SeedQueue.LOGGER.error("[seedqueue] spike failed", error);
      }
   }
}
