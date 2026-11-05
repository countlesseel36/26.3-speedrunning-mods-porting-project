package me.contaria.seedqueue;

import net.fabricmc.loader.api.FabricLoader;

/**
 * SpeedRunIGT integration for the wall hand-off.
 *
 * <p>Without this, joining a second queued world continues the timer of the previous one: SpeedRunIGT
 * only starts a fresh timer for world loads it observes through its own join hook, and the hand-off
 * adopts an already running server. Starting the timer here (like the 1.21.4 SeedQueue did) resets
 * IGT and RTA to zero for every queued world.</p>
 *
 * <p>Reflection is used so SeedQueue still builds and runs without SpeedRunIGT installed.</p>
 */
public final class TimerCompat {
   private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("speedrunigt");
   private static boolean warned;

   private TimerCompat() {
   }

   public static void start(QueueEntry entry) {
      if (!PRESENT) {
         return;
      }

      try {
         Class<?> timerClass = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
         Class<?> runTypeClass = Class.forName("com.redlimerl.speedrunigt.timer.running.RunType");
         Class<?> utilsClass = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimerUtils");
         boolean autoStart = autoStart();

         if (autoStart) {
            Object randomSeed = runTypeClass.getField("RANDOM_SEED").get(null);
            timerClass.getMethod("start", String.class, runTypeClass).invoke(null, entry.folder, randomSeed);

            Object timer = timerClass.getMethod("getInstance").invoke(null);
            timerClass
               .getMethod("setDefaultGameMode", int.class)
               .invoke(timer, utilsClass.getMethod("getCurrentWorldDefaultGameMode").invoke(null));
            timerClass
               .getMethod("setCheatAvailable", boolean.class)
               .invoke(timer, utilsClass.getMethod("isCurrentWorldCheatAvailable").invoke(null));
            timerClass
               .getMethod("checkDifficulty", net.minecraft.world.Difficulty.class)
               .invoke(timer, utilsClass.getMethod("getCurrentDifficulty").invoke(null));

            SeedQueue.LOGGER.info(
               "[seedqueue] SpeedRunIGT timer started for #{}: IGT={} RTA={}",
               entry.number,
               timerClass.getMethod("getInGameTime").invoke(timer),
               timerClass.getMethod("getRealTimeAttack").invoke(timer)
            );
         }

         utilsClass.getField("IS_CHANGING_DIMENSION").setBoolean(null, true);
         utilsClass.getField("CAN_DISCONNECT").setBoolean(null, false);
         utilsClass.getField("IS_SET_SEED").setBoolean(null, false);
      } catch (Throwable t) {
         if (!warned) {
            warned = true;
            SeedQueue.LOGGER.warn("[seedqueue] SpeedRunIGT timer integration failed", t);
         }
      }
   }

   /**
    * Restores the SpeedRunIGT flags that shield the hand-off.
    *
    * <p>{@link #start(QueueEntry)} turns {@code CAN_DISCONNECT} off so IGT does not end the fresh
    * timer while the client swaps levels. If that is never undone, IGT keeps its timer running after
    * the world is left, and the RTA/IGT overlay stays on the title screen and on the wall. This runs a
    * few ticks after the join, once the level swap is over.</p>
    */
   public static void finish() {
      if (!PRESENT) {
         return;
      }

      try {
         Class<?> utilsClass = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimerUtils");
         utilsClass.getField("IS_CHANGING_DIMENSION").setBoolean(null, false);
         utilsClass.getField("CAN_DISCONNECT").setBoolean(null, true);
         utilsClass.getField("IS_SET_SEED").setBoolean(null, false);
      } catch (Throwable t) {
         warnOnce("[seedqueue] SpeedRunIGT flag restore failed", t);
      }
   }

   /** Ends the current run timer: used when a queued world is abandoned by going back to the wall. */
   public static void stop() {
      if (!PRESENT) {
         return;
      }

      finish();

      try {
         Class<?> timerClass = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
         Object timer = timerClass.getMethod("getInstance").invoke(null);

         if (timer != null && Boolean.TRUE.equals(timerClass.getMethod("isStarted").invoke(timer))) {
            timerClass.getMethod("end").invoke(null);
            SeedQueue.LOGGER.info("[seedqueue] SpeedRunIGT timer ended");
         }
      } catch (Throwable t) {
         warnOnce("[seedqueue] SpeedRunIGT timer end failed", t);
      }
   }

   private static void warnOnce(String message, Throwable t) {
      if (!warned) {
         warned = true;
         SeedQueue.LOGGER.warn(message, t);
      }
   }

   /** Logs the current IGT/RTA values (debug for the hand-off timing). */
   public static void report(String where) {
      if (!PRESENT) {
         return;
      }

      try {
         Class<?> timerClass = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
         Object timer = timerClass.getMethod("getInstance").invoke(null);

         if (timer == null) {
            SeedQueue.LOGGER.info("[seedqueue] SpeedRunIGT timer {}: none", where);
            return;
         }

         SeedQueue.LOGGER.info(
            "[seedqueue] SpeedRunIGT timer {}: world={} IGT={} RTA={}",
            where,
            timerClass.getMethod("getWorldName").invoke(timer),
            timerClass.getMethod("getInGameTime").invoke(timer),
            timerClass.getMethod("getRealTimeAttack").invoke(timer)
         );
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] SpeedRunIGT timer report failed", t);
      }
   }

   private static boolean autoStart() throws Exception {
      Class<?> optionClass = Class.forName("com.redlimerl.speedrunigt.option.SpeedRunOption");
      Class<?> optionsClass = Class.forName("com.redlimerl.speedrunigt.option.SpeedRunOptions");
      Class<?> argumentClass = Class.forName("com.redlimerl.speedrunigt.option.OptionArgument");
      Object argument = optionsClass.getField("TIMER_CATEGORY").get(null);
      Object category = optionClass.getMethod("getOption", argumentClass).invoke(null, argument);

      if (category == null) {
         return false;
      }

      return Boolean.TRUE.equals(category.getClass().getMethod("isAutoStart").invoke(category));
   }
}
