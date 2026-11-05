package me.contaria.seedqueue;

import com.mojang.serialization.Lifecycle;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

/**
 * Feasibility spike: builds a world stem for a queued world on a background thread, exactly like
 * vanilla's fresh-level flow does, and reports whether that works in 26.3 off the render thread.
 */
public final class Spike {
   private static boolean started;

   private Spike() {
   }

   public static void tick(Minecraft minecraft) {
      if (started || !Boolean.getBoolean("seedqueue.spike")) {
         return;
      }

      if (!(minecraft.gui.screen() instanceof TitleScreen)) {
         return;
      }

      started = true;
      Thread thread = new Thread(Spike::run, "SeedQueue spike");
      thread.setDaemon(true);
      thread.start();
   }

   private static void run() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         String folder = "SeedQueue-Spike-" + System.currentTimeMillis();
         LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().validateAndCreateAccess(folder);
         PackRepository packs = ServerPacksSource.createPackRepository(access);
         LevelSettings settings = new LevelSettings(
            folder, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, false, WorldDataConfiguration.DEFAULT
         );
         WorldOptions options = WorldOptions.defaultWithRandomSeed();
         WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packs, settings.dataConfiguration(), false, false);
         WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(
            packConfig, Commands.CommandSelection.INTEGRATED, PermissionSet.ALL_PERMISSIONS
         );
         long before = System.currentTimeMillis();
         WorldStem stem = WorldLoader.load(
               initConfig,
               context -> {
                  WorldDimensions dimensions = context.datapackWorldRegistries()
                     .lookupOrThrow(Registries.WORLD_PRESET)
                     .getOrThrow(WorldPresets.NORMAL)
                     .value()
                     .createWorldDimensions();
                  WorldDimensions.Complete complete = dimensions.bake(
                     context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM)
                  );
                  PrimaryLevelData data = new PrimaryLevelData(
                     settings, complete.specialWorldProperty(), complete.lifecycle()
                  );
                  WorldGenSettings genSettings = new WorldGenSettings(options, dimensions);
                  return new WorldLoader.DataLoadOutput<>(
                     new LevelDataAndDimensions.WorldDataAndGenSettings(data, genSettings),
                     complete.dimensionsRegistryAccess()
                  );
               },
               WorldStem::new,
               Util.backgroundExecutor(),
               Util.backgroundExecutor()
            )
            .get();
         access.saveDataTag(stem.worldDataAndGenSettings().data());
         SeedQueue.LOGGER.info(
            "[seedqueue] spike: created world stem for {} in {} ms (seed {})",
            folder,
            System.currentTimeMillis() - before,
            options.seed()
         );

         net.minecraft.server.level.progress.LevelLoadListener listener = new net.minecraft.server.level.progress.LevelLoadListener() {
            @Override
            public void start(net.minecraft.server.level.progress.LevelLoadListener.Stage stage, int i) {
               SeedQueue.LOGGER.info("[seedqueue] spike: load start {} {}", stage, i);
            }

            @Override
            public void update(net.minecraft.server.level.progress.LevelLoadListener.Stage stage, int i, int j) {
            }

            @Override
            public void finish(net.minecraft.server.level.progress.LevelLoadListener.Stage stage) {
               SeedQueue.LOGGER.info("[seedqueue] spike: load finish {}", stage);
            }

            @Override
            public void updateFocus(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key, net.minecraft.world.level.ChunkPos pos) {
            }
         };
         long started = System.currentTimeMillis();
         net.minecraft.client.server.IntegratedServer server = net.minecraft.server.MinecraftServer.spin(thread -> {
            thread.setName("SeedQueue spike server");
            return new net.minecraft.client.server.IntegratedServer(
               thread, minecraft, access, packs, stem, java.util.Optional.empty(), minecraft.services(), listener
            );
         });
         long deadline = System.currentTimeMillis() + 30000L;

         while (!server.isReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
         }

         SeedQueue.LOGGER.info(
            "[seedqueue] spike: server ready={} after {} ms", server.isReady(), System.currentTimeMillis() - started
         );
         server.halt(false);
      } catch (Throwable t) {
         SeedQueue.LOGGER.error("[seedqueue] spike failed", t);
      }
   }
}