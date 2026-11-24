package me.contaria.seedqueue.render;
import me.contaria.seedqueue.SeedQueue;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.Difficulty;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.render.*;

public final class TimerCompat {
   private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("speedrunigt");
   private static boolean warned;

   private TimerCompat() {
   }

   public static void start(QueueEntry entry) {
      if (PRESENT) {
         try {
            Class clazz = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
            Class class2 = Class.forName("com.redlimerl.speedrunigt.timer.running.RunType");
            Class class3 = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimerUtils");
            boolean flag = autoStart();
            if (flag) {
               Object obj = class2.getField("RANDOM_SEED").get(null);
               clazz.getMethod("start", String.class, class2).invoke(null, entry.folder, obj);
               Object obj2 = clazz.getMethod("getInstance").invoke(null);
               clazz.getMethod("setDefaultGameMode", int.class).invoke(obj2, class3.getMethod("getCurrentWorldDefaultGameMode").invoke(null));
               clazz.getMethod("setCheatAvailable", boolean.class).invoke(obj2, class3.getMethod("isCurrentWorldCheatAvailable").invoke(null));
               clazz.getMethod("checkDifficulty", Difficulty.class).invoke(obj2, class3.getMethod("getCurrentDifficulty").invoke(null));
               SeedQueue.LOGGER
                  .info(
                     "[seedqueue] SpeedRunIGT timer started for #{}: IGT={} RTA={}",
                     entry.number,
                     clazz.getMethod("getInGameTime").invoke(obj2),
                     clazz.getMethod("getRealTimeAttack").invoke(obj2)
                  );
            }

            class3.getField("IS_CHANGING_DIMENSION").setBoolean(null, true);
            class3.getField("CAN_DISCONNECT").setBoolean(null, false);
            class3.getField("IS_SET_SEED").setBoolean(null, false);
         } catch (Throwable error) {
            if (!warned) {
               warned = true;
               SeedQueue.LOGGER.warn("[seedqueue] SpeedRunIGT timer integration failed", error);
            }
         }
      }
   }

   public static void finish() {
      if (PRESENT) {
         try {
            Class clazz = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimerUtils");
            clazz.getField("IS_CHANGING_DIMENSION").setBoolean(null, false);
            clazz.getField("CAN_DISCONNECT").setBoolean(null, true);
            clazz.getField("IS_SET_SEED").setBoolean(null, false);
         } catch (Throwable error) {
            warnOnce("[seedqueue] SpeedRunIGT flag restore failed", error);
         }
      }
   }

   public static void stop() {
      if (PRESENT) {
         finish();

         try {
            Class clazz = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
            Object obj = clazz.getMethod("getInstance").invoke(null);
            if (obj != null && Boolean.TRUE.equals(clazz.getMethod("isStarted").invoke(obj))) {
               clazz.getMethod("end").invoke(null);
               SeedQueue.LOGGER.info("[seedqueue] SpeedRunIGT timer ended");
            }
         } catch (Throwable error) {
            warnOnce("[seedqueue] SpeedRunIGT timer end failed", error);
         }
      }
   }

   private static void warnOnce(String text, Throwable error) {
      if (!warned) {
         warned = true;
         SeedQueue.LOGGER.warn(text, error);
      }
   }

   public static void report(String text) {
      if (PRESENT) {
         try {
            Class clazz = Class.forName("com.redlimerl.speedrunigt.timer.InGameTimer");
            Object obj = clazz.getMethod("getInstance").invoke(null);
            if (obj == null) {
               SeedQueue.LOGGER.info("[seedqueue] SpeedRunIGT timer {}: none", text);
               return;
            }

            SeedQueue.LOGGER
               .info(
                  "[seedqueue] SpeedRunIGT timer {}: world={} IGT={} RTA={}",
                  text,
                  clazz.getMethod("getWorldName").invoke(obj),
                  clazz.getMethod("getInGameTime").invoke(obj),
                  clazz.getMethod("getRealTimeAttack").invoke(obj)
               );
         } catch (Throwable error) {
            SeedQueue.LOGGER.warn("[seedqueue] SpeedRunIGT timer report failed", error);
         }
      }
   }

   private static boolean autoStart() throws Exception {
      Class clazz = Class.forName("com.redlimerl.speedrunigt.option.SpeedRunOption");
      Class class2 = Class.forName("com.redlimerl.speedrunigt.option.SpeedRunOptions");
      Class class3 = Class.forName("com.redlimerl.speedrunigt.option.OptionArgument");
      Object obj = class2.getField("TIMER_CATEGORY").get(null);
      Object obj2 = clazz.getMethod("getOption", class3).invoke(null, obj);
      return obj2 == null ? false : Boolean.TRUE.equals(obj2.getClass().getMethod("isAutoStart").invoke(obj2));
   }
}
