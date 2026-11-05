package me.contaria.seedqueue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** SeedQueue settings, persisted to config/seedqueue.json. */
public final class QueueConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static int queueSize = 1;
   public static int chunkRadius = 2;
   public static boolean useWall = true;
   public static boolean autoQueue = true;
   public static boolean showHud = true;
   /** Preview camera perspective: "first_person" or "third_person". */
   public static String perspective = "first_person";

   private QueueConfig() {
   }

   public static Path path() {
      return FabricLoader.getInstance().getConfigDir().resolve("seedqueue.json");
   }

   public static void load() {
      try {
         Path path = path();

         if (!Files.exists(path)) {
            save();
            return;
         }

         JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
         queueSize = clamp(json.has("queueSize") ? json.get("queueSize").getAsInt() : queueSize, 1, 4);
         chunkRadius = clamp(json.has("chunkRadius") ? json.get("chunkRadius").getAsInt() : chunkRadius, 0, 8);
         useWall = !json.has("useWall") || json.get("useWall").getAsBoolean();
         autoQueue = !json.has("autoQueue") || json.get("autoQueue").getAsBoolean();
         showHud = !json.has("showHud") || json.get("showHud").getAsBoolean();
         perspective = json.has("perspective") ? json.get("perspective").getAsString() : perspective;
         if (!"third_person".equals(perspective)) {
            perspective = "first_person";
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load config", t);
      }
   }

   public static void save() {
      try {
         JsonObject json = new JsonObject();
         json.addProperty("queueSize", queueSize);
         json.addProperty("chunkRadius", chunkRadius);
         json.addProperty("useWall", useWall);
         json.addProperty("autoQueue", autoQueue);
         json.addProperty("showHud", showHud);
         json.addProperty("perspective", perspective);
         Path path = path();
         Files.createDirectories(path.getParent());
         Files.writeString(path, GSON.toJson(json));
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to save config", t);
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}