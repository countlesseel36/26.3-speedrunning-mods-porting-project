package me.contaria.seedqueue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Map;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.api.annotations.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * SpeedrunAPI config for SeedQueue (the Mod Configs entry and the settings screen with categories,
 * sliders and the wall keybinding screen).
 *
 * <p>Options that the 26.3 port actually uses are mirrored into {@link QueueConfig} on load/save;
 * the remaining ones match the 1.16.1/1.21.4 option set and are persisted by SpeedrunAPI itself.</p>
 */
@Config(init = Config.InitPoint.ONINITIALIZE)
public final class SeedQueueSpeedrunConfig implements SpeedrunConfig {
   @Config.Category("queue")
   @Config.Numbers.Whole.Bounds(min = 0, max = 30)
   public int maxCapacity = 3;

   @Config.Category("queue")
   @Config.Numbers.Whole.Bounds(min = 0, max = 30)
   public int maxConcurrently = 0;

   @Config.Category("queue")
   @Config.Numbers.Whole.Bounds(min = 1, max = 30)
   public int maxConcurrently_onWall = 4;

   @Config.Category("queue")
   @Config.Numbers.Whole.Bounds(min = 0, max = 100)
   public int maxWorldGenerationPercentage = 100;

   @Config.Category("queue")
   public boolean resumeOnFilledQueue = false;

   @Config.Category("wall")
   public boolean useWall = true;

   @Config.Category("wall")
   @Config.Numbers.Whole.Bounds(min = 1, max = 10)
   public int rows = 1;

   @Config.Category("wall")
   @Config.Numbers.Whole.Bounds(min = 1, max = 10)
   public int columns = 3;

   @Config.Category("wall")
   @Config.Numbers.Whole.Bounds(min = 0, max = 1000)
   public int resetCooldown = 400;

   @Config.Category("queue")
   @Config.Numbers.Whole.Bounds(min = 2, max = 6)
   public int chunkRadius = 4;

   @Config.Category("debug")
   @Config.Text("speedrunapi.config.seedqueue.option.showChunkProgress.description")
   public boolean showChunkProgress = false;

   @Config.Category("wall")
   public boolean waitForPreviewSetup = false;

   @Config.Category("wall")
   public boolean bypassWall = false;

   @Config.Category("wall")
   public boolean smartSwitch = false;

   @Config.Category("wall")
   public boolean joinTransition = true;

   @Config.Category("wall")
   @Config.Numbers.Whole.Bounds(min = 0, max = 100)
   public int soundVolume = 100;

   @Config.Category("wall")
   @Config.Numbers.Whole.Bounds(min = 0, max = 16384)
   @Config.Hide
   public int simulatedWidth = 0;

   @Config.Category("wall")
   @Config.Numbers.Whole.Bounds(min = 0, max = 16384)
   @Config.Hide
   public int simulatedHeight = 0;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 1, max = 255)
   public int wallFPS = 60;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 1, max = 255)
   public int previewFPS = 20;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = -1, max = 30)
   public int preparingPreviews = -1;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 1, max = 30)
   public int startConcurrently = 2;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 0, max = 30)
   public int prewarmWorlds = 9;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 2, max = 16)
   public int wallChunkDistance = 12;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 1, max = 100)
   public int wallDataLimit = 100;

   @Config.Category("performance")
   @Config.Numbers.Whole.Bounds(min = 0, max = 60)
   public int idlePreviewFPS = 15;

   @Config.Category("performance")
   public boolean freezeLockedPreviews = false;

   @Config.Category("performance")
   public boolean reduceLevelList = true;

   @Config.Category("misc")
   @Config.Numbers.Whole.Bounds(min = 1, max = 2000)
   public int benchmarkResets = 1000;

   @Config.Category("misc")
   @Config.Numbers.Whole.Bounds(min = -1, max = 500000)
   public long chunkMapFreezing = -1L;

   @Config.Category("misc")
   public boolean checkMinMemory = true;

   @Config.Hide
   @Config.Category("advanced")
   public boolean showAdvancedSettings = true;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 1, max = 5)
   public int seedQueueThreadPriority = 5;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 1, max = 5)
   public int serverThreadPriority = 5;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 0, max = 256)
   public int backgroundExecutorThreads = 0;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 1, max = 5)
   public int backgroundExecutorThreadPriority = 5;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 0, max = 256)
   public int wallExecutorThreads = 0;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 1, max = 5)
   public int wallExecutorThreadPriority = 4;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 0, max = 64)
   public int chunkUpdateThreads = 0;

   @Config.Category("threading")
   @Config.Numbers.Whole.Bounds(min = 1, max = 5)
   public int chunkUpdateThreadPriority = 3;

   @Config.Category("debug")
   public boolean showDebugMenu = false;

   @Config.Category("debug")
   public boolean useWatchdog = false;

   @Config.Category("debug")
   @Config.Hide
   public boolean showChunkMaps = false;

   @Config.Ignored
   public String keyBindings = "";

   public SeedQueueSpeedrunConfig() {
      this.syncFromQueueConfig();
   }

   @Override
   public String modID() {
      return "seedqueue";
   }

   @Override
   public Map<String, SpeedrunOption<?>> init() throws ReflectiveOperationException {
      Map<String, SpeedrunOption<?>> options = this.init(this);

      try {
         Field field = SeedQueueSpeedrunConfig.class.getField("keyBindings");
         SpeedrunOption<String> keyOption = new SpeedrunConfigAPI.CustomOption.Builder<String>(this, this, field)
            .getter((option, config, storage, optionField) -> "")
            .setter((option, config, storage, optionField, value) -> {})
            .fromJson((option, config, storage, optionField, json) -> {})
            .toJson((option, config, storage, optionField) -> new JsonObject())
            .createWidget((option, config, storage, optionField) -> Button.builder(
                  Component.translatable("seedqueue.menu.keys"),
                  button -> {
                     Screen current = Minecraft.getInstance().gui.screen();
                     Minecraft.getInstance().gui.setScreen(new WallKeybindingsScreen(current));
                  }
               )
               .bounds(0, 0, 150, 20)
               .build())
            .build();
         options.put(keyOption.getID(), keyOption);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to add the keybindings option", t);
      }

      return options;
   }

   @Override
   public boolean shouldShowOption(String option) {
      // The "show advanced settings" toggle was removed: every option is always listed.
      if (true) {
         return true;
      }

      return switch (option) {
         case "seedQueueThreadPriority",
            "serverThreadPriority",
            "backgroundExecutorThreads",
            "backgroundExecutorThreadPriority",
            "wallExecutorThreads",
            "wallExecutorThreadPriority",
            "chunkUpdateThreads",
            "chunkUpdateThreadPriority",
            "showDebugMenu",
            "useWatchdog",
            "showChunkMaps",
            "chunkMapFreezing",
            "checkMinMemory" -> false;
         default -> true;
      };
   }

   @Override
   public boolean shouldShowCategory(String category) {
      return true;
   }

   @Override
   public void finishLoading() {
      this.applyToQueueConfig();
   }

   @Override
   public void finishSaving() {
      this.applyToQueueConfig();
   }

   @Override
   public void onConfigScreenClose(Screen current, Screen parent) {
      this.applyToQueueConfig();
   }

   private void syncFromQueueConfig() {
      this.maxCapacity = QueueConfig.queueSize;
      this.maxConcurrently = QueueConfig.maxConcurrently;
      this.maxConcurrently_onWall = QueueConfig.maxConcurrentlyOnWall;
      this.maxWorldGenerationPercentage = QueueConfig.maxWorldGenerationPercentage;
      this.resumeOnFilledQueue = QueueConfig.resumeOnFilledQueue;
      this.preparingPreviews = QueueConfig.preparingPreviews;
      this.backgroundExecutorThreads = QueueConfig.backgroundExecutorThreads;
      this.backgroundExecutorThreadPriority = QueueConfig.backgroundExecutorThreadPriority;
      this.wallExecutorThreads = QueueConfig.wallExecutorThreads;
      this.wallExecutorThreadPriority = QueueConfig.wallExecutorThreadPriority;
      this.chunkUpdateThreads = QueueConfig.chunkUpdateThreads;
      this.chunkUpdateThreadPriority = QueueConfig.chunkUpdateThreadPriority;
      this.seedQueueThreadPriority = QueueConfig.seedQueueThreadPriority;
      this.showDebugMenu = QueueConfig.showDebugMenu;
      this.useWatchdog = QueueConfig.useWatchdog;
      this.showChunkMaps = QueueConfig.showChunkMaps;
      this.startConcurrently = QueueConfig.startConcurrently;
      this.prewarmWorlds = QueueConfig.prewarmWorlds;
      this.chunkRadius = QueueConfig.chunkRadius;
      this.showChunkProgress = QueueConfig.showChunkProgress;
      this.simulatedWidth = QueueConfig.simulatedWidth;
      this.simulatedHeight = QueueConfig.simulatedHeight;
      this.wallChunkDistance = QueueConfig.wallChunkDistance;
      this.wallDataLimit = QueueConfig.wallDataLimit;
      this.idlePreviewFPS = QueueConfig.idlePreviewFPS;
      this.joinTransition = QueueConfig.joinTransition;
      this.chunkMapFreezing = QueueConfig.chunkMapFreezing;
      this.checkMinMemory = QueueConfig.checkMinMemory;
      this.useWall = QueueConfig.useWall;
      this.rows = QueueConfig.rows;
      this.columns = QueueConfig.columns;
      this.resetCooldown = QueueConfig.resetCooldownMs;
      this.waitForPreviewSetup = QueueConfig.waitForPreviewSetup;
      this.bypassWall = QueueConfig.bypassWall;
      this.smartSwitch = QueueConfig.smartSwitch;
      this.soundVolume = QueueConfig.soundVolume;
      this.wallFPS = QueueConfig.wallFps;
      this.previewFPS = QueueConfig.previewFps;
      this.freezeLockedPreviews = QueueConfig.freezeLockedPreviews;
      this.reduceLevelList = QueueConfig.reduceLevelList;
      this.benchmarkResets = QueueConfig.benchmarkResets;
      this.serverThreadPriority = QueueConfig.serverThreadPriority;
      this.backgroundExecutorThreadPriority = QueueConfig.backgroundExecutorThreadPriority;
   }

   private void applyToQueueConfig() {
      QueueConfig.queueSize = Math.max(1, Math.min(6, this.maxCapacity));
      QueueConfig.maxConcurrently = Math.max(0, Math.min(30, this.maxConcurrently));
      QueueConfig.maxConcurrentlyOnWall = Math.max(1, Math.min(30, this.maxConcurrently_onWall));
      QueueConfig.maxWorldGenerationPercentage = Math.max(0, Math.min(100, this.maxWorldGenerationPercentage));
      QueueConfig.resumeOnFilledQueue = this.resumeOnFilledQueue;
      QueueConfig.preparingPreviews = Math.max(-1, Math.min(30, this.preparingPreviews));
      QueueConfig.backgroundExecutorThreads = Math.max(0, Math.min(256, this.backgroundExecutorThreads));
      QueueConfig.backgroundExecutorThreadPriority = Math.max(1, Math.min(10, this.backgroundExecutorThreadPriority));
      QueueConfig.wallExecutorThreads = Math.max(0, Math.min(256, this.wallExecutorThreads));
      QueueConfig.wallExecutorThreadPriority = Math.max(1, Math.min(10, this.wallExecutorThreadPriority));
      QueueConfig.chunkUpdateThreads = Math.max(0, Math.min(64, this.chunkUpdateThreads));
      QueueConfig.chunkUpdateThreadPriority = Math.max(1, Math.min(10, this.chunkUpdateThreadPriority));
      QueueConfig.seedQueueThreadPriority = Math.max(1, Math.min(10, this.seedQueueThreadPriority));
      QueueConfig.showDebugMenu = this.showDebugMenu;
      QueueConfig.useWatchdog = this.useWatchdog;
      QueueConfig.showChunkMaps = this.showChunkMaps;
      QueueConfig.startConcurrently = Math.max(1, Math.min(30, this.startConcurrently));
      QueueConfig.prewarmWorlds = Math.max(0, Math.min(30, this.prewarmWorlds));
      QueueConfig.chunkRadius = Math.max(2, Math.min(6, this.chunkRadius));
      QueueConfig.showChunkProgress = this.showChunkProgress;
      QueueConfig.simulatedWidth = Math.max(0, Math.min(16384, this.simulatedWidth));
      QueueConfig.simulatedHeight = Math.max(0, Math.min(16384, this.simulatedHeight));
      QueueConfig.wallChunkDistance = Math.max(2, Math.min(16, this.wallChunkDistance));
      QueueConfig.wallDataLimit = Math.max(1, Math.min(100, this.wallDataLimit));
      System.setProperty("seedqueue.wallDataLimit", String.valueOf(QueueConfig.wallDataLimit));
      QueueConfig.idlePreviewFPS = Math.max(0, Math.min(60, this.idlePreviewFPS));
      QueueConfig.joinTransition = this.joinTransition;
      QueueConfig.chunkMapFreezing = this.chunkMapFreezing;
      QueueConfig.checkMinMemory = this.checkMinMemory;
      QueueConfig.useWall = this.useWall;
      QueueConfig.rows = Math.max(1, Math.min(4, this.rows));
      QueueConfig.columns = Math.max(1, Math.min(4, this.columns));
      QueueConfig.resetCooldownMs = Math.max(0, this.resetCooldown);
      QueueConfig.waitForPreviewSetup = this.waitForPreviewSetup;
      QueueConfig.bypassWall = this.bypassWall;
      QueueConfig.smartSwitch = this.smartSwitch;
      QueueConfig.soundVolume = Math.max(0, Math.min(100, this.soundVolume));
      QueueConfig.wallFps = Math.max(1, this.wallFPS);
      QueueConfig.previewFps = Math.max(1, this.previewFPS);
      QueueConfig.freezeLockedPreviews = this.freezeLockedPreviews;
      QueueConfig.reduceLevelList = this.reduceLevelList;
      QueueConfig.benchmarkResets = Math.max(1, Math.min(2000, this.benchmarkResets));
      QueueConfig.serverThreadPriority = Math.max(1, Math.min(10, this.serverThreadPriority));
      QueueConfig.backgroundExecutorThreadPriority = Math.max(1, Math.min(10, this.backgroundExecutorThreadPriority));
      QueueConfig.save();
   }
}
