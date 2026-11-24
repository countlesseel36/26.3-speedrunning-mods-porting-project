package me.contaria.seedqueue.config;
import me.contaria.seedqueue.SeedQueue;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Map;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI.CustomOption.Builder;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.config.api.annotations.Config.Category;
import me.contaria.speedrunapi.config.api.annotations.Config.Hide;
import me.contaria.speedrunapi.config.api.annotations.Config.Ignored;
import me.contaria.speedrunapi.config.api.annotations.Config.InitPoint;
import me.contaria.speedrunapi.config.api.annotations.Config.Text;
import me.contaria.speedrunapi.config.api.annotations.Config.Numbers.Whole.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

@Config(
   init = InitPoint.ONINITIALIZE
)
public final class SeedQueueSpeedrunConfig implements SpeedrunConfig {
   @Category("queue")
   @Bounds(
      min = 0L,
      max = 10L
   )
   public int maxCapacity = 3;
   @Category("queue")
   @Bounds(
      min = 0L,
      max = 30L
   )
   public int maxConcurrently = 0;
   @Category("queue")
   @Bounds(
      min = 1L,
      max = 30L
   )
   public int maxConcurrently_onWall = 4;
   @Category("queue")
   @Bounds(
      min = 0L,
      max = 100L
   )
   public int maxWorldGenerationPercentage = 100;
   @Category("queue")
   public boolean resumeOnFilledQueue = false;
   @Category("wall")
   public boolean useWall = true;
   @Category("wall")
   @Bounds(
      min = 1L,
      max = 10L
   )
   public int rows = 1;
   @Category("wall")
   @Bounds(
      min = 1L,
      max = 10L
   )
   public int columns = 3;
   @Category("wall")
   @Bounds(
      min = 0L,
      max = 1000L
   )
   public int resetCooldown = 400;
   @Category("queue")
   @Bounds(
      min = 2L,
      max = 6L
   )
   public int chunkRadius = 4;
   @Category("debug")
   @Text("speedrunapi.config.seedqueue.option.showChunkProgress.description")
   public boolean showChunkProgress = false;
   @Category("wall")
   public boolean waitForPreviewSetup = false;
   @Category("wall")
   public boolean bypassWall = false;
   @Category("wall")
   public boolean smartSwitch = false;
   @Category("wall")
   public boolean joinTransition = true;
   @Category("wall")
   @Bounds(
      min = 0L,
      max = 100L
   )
   public int soundVolume = 100;
   @Category("wall")
   @Bounds(
      min = 0L,
      max = 16384L
   )
   public int simulatedWidth = 0;
   @Category("wall")
   @Bounds(
      min = 0L,
      max = 16384L
   )
   public int simulatedHeight = 0;
   @Category("performance")
   @Bounds(
      min = 1L,
      max = 255L
   )
   public int wallFPS = 60;
   @Category("performance")
   @Bounds(
      min = 1L,
      max = 255L
   )
   public int previewFPS = 20;
   @Category("performance")
   @Bounds(
      min = -1L,
      max = 30L
   )
   public int preparingPreviews = -1;
   @Category("performance")
   @Bounds(
      min = 1L,
      max = 30L
   )
   public int startConcurrently = 2;
   @Category("performance")
   @Bounds(
      min = 0L,
      max = 30L
   )
   public int prewarmWorlds = 9;
   @Category("performance")
   @Bounds(
      min = 2L,
      max = 16L
   )
   public int wallChunkDistance = 12;
   @Category("performance")
   @Bounds(
      min = 1L,
      max = 100L
   )
   public int wallDataLimit = 100;
   @Category("performance")
   @Bounds(
      min = 0L,
      max = 60L
   )
   public int idlePreviewFPS = 15;
   @Category("performance")
   public boolean freezeLockedPreviews = false;
   @Category("performance")
   public boolean reduceLevelList = true;
   @Category("benchmark")
   @Bounds(
      min = 1L,
      max = 1000L
   )
   public int benchmarkResets = 1000;
   @Category("benchmark")
   @Bounds(
      min = 0L,
      max = 16L
   )
   public int benchmarkConcurrency = 0;
   @Category("benchmark")
   @Bounds(
      min = 0L,
      max = 400L
   )
   public int benchmarkSampleTicks = 100;
   @Category("benchmark")
   @Bounds(
      min = 0L,
      max = 16L
   )
   public int maxGeneratingCount = 0;
   @Category("benchmark")
   @Bounds(
      min = 1L,
      max = 10L
   )
   public int benchmarkChunkRadius = 2;
   @Category("performance")
   @Bounds(
      min = -1L,
      max = 10000L
   )
   public long chunkMapFreezing = -1L;
   @Category("performance")
   public boolean checkMinMemory = true;
   @Hide
   @Category("advanced")
   public boolean showAdvancedSettings = true;
   @Category("threading")
   @Bounds(
      min = 1L,
      max = 5L
   )
   public int seedQueueThreadPriority = 5;
   @Category("threading")
   @Bounds(
      min = 1L,
      max = 5L
   )
   public int serverThreadPriority = 5;
   @Category("threading")
   @Bounds(
      min = 0L,
      max = 256L
   )
   public int backgroundExecutorThreads = 0;
   @Category("threading")
   @Bounds(
      min = 1L,
      max = 5L
   )
   public int backgroundExecutorThreadPriority = 5;
   @Category("threading")
   @Bounds(
      min = 0L,
      max = 256L
   )
   public int wallExecutorThreads = 0;
   @Category("threading")
   @Bounds(
      min = 1L,
      max = 5L
   )
   public int wallExecutorThreadPriority = 4;
   @Category("threading")
   @Bounds(
      min = 0L,
      max = 64L
   )
   public int chunkUpdateThreads = 0;
   @Category("threading")
   @Bounds(
      min = 1L,
      max = 5L
   )
   public int chunkUpdateThreadPriority = 3;
   @Category("debug")
   public boolean showDebugMenu = false;
   @Category("debug")
   public boolean useWatchdog = false;
   @Category("debug")
   @Hide
   public boolean showChunkMaps = false;
   @Ignored
   public String keyBindings = "";

   public SeedQueueSpeedrunConfig() {
      this.syncFromQueueConfig();
   }

   public String modID() {
      return "seedqueue";
   }

   public Map<String, SpeedrunOption<?>> init() throws ReflectiveOperationException {
      Map map = this.init(this, new String[0]);

      try {
         Field field = SeedQueueSpeedrunConfig.class.getField("keyBindings");
         SpeedrunOption speedrunOption = new Builder(this, this, field, new String[0])
            .getter((arg, arg2, arg3, arg4) -> "")
            .setter((arg, arg2, arg3, arg4, arg5) -> {
            })
            .fromJson((arg, arg2, arg3, arg4, arg5) -> {
            })
            .toJson((arg, arg2, arg3, arg4) -> new JsonObject())
            .createWidget((arg, arg2, arg3, arg4) -> Button.builder(Component.translatable("seedqueue.menu.keys"), ignored -> {
                  Screen var1xx = Minecraft.getInstance().gui.screen();
                  Minecraft.getInstance().gui.setScreen(new WallKeybindingsScreen(var1xx));
               }).bounds(0, 0, 150, 20).build())
            .build();
         map.put(speedrunOption.getID(), speedrunOption);
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to add the keybindings option", error);
      }

      return map;
   }

   public boolean shouldShowOption(String text) {
      return true;
   }

   public boolean shouldShowCategory(String text) {
      return true;
   }

   public void finishLoading() {
      this.applyToQueueConfig();
   }

   public void finishSaving() {
      this.applyToQueueConfig();
   }

   public void onConfigScreenClose(Screen screen, Screen screen2) {
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
      this.benchmarkConcurrency = QueueConfig.benchmarkConcurrency;
      this.benchmarkSampleTicks = QueueConfig.benchmarkSampleTicks;
      this.maxGeneratingCount = QueueConfig.maxGeneratingCount;
      this.benchmarkChunkRadius = QueueConfig.benchmarkChunkRadius;
      this.serverThreadPriority = QueueConfig.serverThreadPriority;
      this.backgroundExecutorThreadPriority = QueueConfig.backgroundExecutorThreadPriority;
   }

   private void applyToQueueConfig() {
      QueueConfig.queueSize = Math.max(1, Math.min(10, this.maxCapacity));
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
      QueueConfig.benchmarkResets = Math.max(1, Math.min(1000, this.benchmarkResets));
      QueueConfig.benchmarkConcurrency = Math.max(0, Math.min(16, this.benchmarkConcurrency));
      QueueConfig.benchmarkSampleTicks = Math.max(0, Math.min(400, this.benchmarkSampleTicks));
      QueueConfig.maxGeneratingCount = Math.max(0, Math.min(16, this.maxGeneratingCount));
      QueueConfig.benchmarkChunkRadius = Math.max(1, Math.min(10, this.benchmarkChunkRadius));
      QueueConfig.serverThreadPriority = Math.max(1, Math.min(10, this.serverThreadPriority));
      QueueConfig.backgroundExecutorThreadPriority = Math.max(1, Math.min(10, this.backgroundExecutorThreadPriority));
      QueueConfig.save();
   }
}
