package me.contaria.seedqueue.wall;
import me.contaria.seedqueue.SeedQueue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

public final class WallLayout {
   public final WallLayout.Group main;
   public final List<WallLayout.Group> preparing;
   public final WallLayout.Group locked;
   public final boolean replaceLockedInstances;

   private WallLayout(WallLayout.Group wallLayoutGroup, List<WallLayout.Group> list, WallLayout.Group wallLayoutGroup2, boolean flag) {
      this.main = wallLayoutGroup;
      this.preparing = list;
      this.locked = wallLayoutGroup2;
      this.replaceLockedInstances = flag;
   }

   private static int toPixels(float ratio, int count) {
      return Math.round(ratio >= 0.0F && ratio <= 1.0F ? ratio * (float)count : ratio);
   }

   public static WallLayout defaults() {
      WallLayout.Group wallLayoutGroup = new WallLayout.Group(
         Math.max(1, QueueConfig.rows), Math.max(1, QueueConfig.columns), 0.02F, 0.05F, 0.96F, -1.0F, 8.0F, null, false, false
      );
      return new WallLayout(wallLayoutGroup, List.of(), null, false);
   }

   public static WallLayout load(Minecraft minecraft) {
      try {
         Identifier identifier = Identifier.fromNamespaceAndPath("seedqueue", "wall/custom_layout.json");
         Optional optional = minecraft.getResourceManager().getResource(identifier);
         if (optional.isEmpty()) {
            SeedQueue.LOGGER.info("[seedqueue] using default wall layout");
            return defaults();
         } else {
            WallLayout wallLayout;
            try (InputStream inputStream = ((Resource)optional.get()).open()) {
               JsonObject jsonObject = JsonParser.parseString(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
               WallLayout.Group wallLayoutGroup = jsonObject.has("main") ? parseGroup(jsonObject.getAsJsonObject("main")) : defaults().main;
               if (wallLayoutGroup != null && wallLayoutGroup.positions == null) {
                  wallLayoutGroup = wallLayoutGroup.withGrid(Math.max(1, Math.min(4, QueueConfig.rows)), Math.max(1, Math.min(4, QueueConfig.columns)));
               }

               ArrayList list = new ArrayList();
               if (jsonObject.has("preparing")) {
                  for (JsonElement jsonElement : jsonObject.getAsJsonArray("preparing")) {
                     list.add(parseGroup(jsonElement.getAsJsonObject()));
                  }
               }

               WallLayout.Group wallLayoutGroup2 = jsonObject.has("locked") ? parseGroup(jsonObject.getAsJsonObject("locked")) : null;
               boolean flag = jsonObject.has("replaceLockedInstances") && jsonObject.get("replaceLockedInstances").getAsBoolean();
               SeedQueue.LOGGER
                  .info(
                     "[seedqueue] loaded custom wall layout (preparing groups={}, locked group={}, replaceLockedInstances={})",
                     list.size(),
                     wallLayoutGroup2 != null,
                     flag
                  );
               wallLayout = new WallLayout(wallLayoutGroup, list, wallLayoutGroup2, flag);
            }

            return wallLayout;
         }
      } catch (Throwable error) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load wall layout, using defaults", error);
         return defaults();
      }
   }

   private static WallLayout.Group parseGroup(JsonObject jsonObject) {
      boolean flag = jsonObject.has("instance_background") && jsonObject.get("instance_background").getAsBoolean();
      boolean flag2 = jsonObject.has("cosmetic") && jsonObject.get("cosmetic").getAsBoolean();
      if (!jsonObject.has("positions")) {
         return new WallLayout.Group(
            Math.max(1, integer(jsonObject, "rows", 1)),
            Math.max(1, integer(jsonObject, "columns", 1)),
            number(jsonObject, "x", 0.0F),
            number(jsonObject, "y", 0.0F),
            number(jsonObject, "width", 1.0F),
            number(jsonObject, "height", 1.0F),
            number(jsonObject, "padding", 0.0F),
            null,
            flag,
            flag2
         );
      } else {
         ArrayList list = new ArrayList();

         for (JsonElement jsonElement : jsonObject.getAsJsonArray("positions")) {
            JsonObject jsonObject2 = jsonElement.getAsJsonObject();
            list.add(
               new WallLayout.Slot(number(jsonObject2, "x", 0.0F), number(jsonObject2, "y", 0.0F), number(jsonObject2, "width", 1.0F), number(jsonObject2, "height", 1.0F), flag, flag2)
            );
         }

         return new WallLayout.Group(1, 1, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, list, flag, flag2);
      }
   }

   private static float number(JsonObject jsonObject, String text, float ratio) {
      return jsonObject.has(text) ? jsonObject.get(text).getAsFloat() : ratio;
   }

   private static int integer(JsonObject jsonObject, String text, int count) {
      return jsonObject.has(text) ? jsonObject.get(text).getAsInt() : count;
   }

   public static final class Group {
      private final int rows;
      private final int columns;
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private final float padding;
      private final List<WallLayout.Slot> positions;
      final boolean instanceBackground;
      final boolean cosmetic;

      private Group(int rows, int columns, float x, float y, float width, float height, float padding, List<WallLayout.Slot> positions, boolean instanceBackground, boolean cosmetic) {
         this.rows = rows;
         this.columns = columns;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.padding = padding;
         this.positions = positions;
         this.instanceBackground = instanceBackground;
         this.cosmetic = cosmetic;
      }

      private WallLayout.Group withGrid(int rows, int columns) {
         return new WallLayout.Group(rows, columns, this.x, this.y, this.width, this.height, this.padding, null, this.instanceBackground, this.cosmetic);
      }

      List<WallLayout.Slot> resolve(int viewportWidth, int viewportHeight) {
         if (this.positions != null) {
            return this.positions;
         } else {
            ArrayList slots = new ArrayList(Math.max(1, this.rows * this.columns));
            int originX = WallLayout.toPixels(this.x, viewportWidth);
            int originY = WallLayout.toPixels(this.y, viewportHeight);
            int totalWidth = WallLayout.toPixels(this.width, viewportWidth);
            byte gap = 0;
            int slotWidth = Math.max(1, (totalWidth - gap * (this.columns - 1)) / Math.max(1, this.columns));
            int slotHeight = this.height < 0.0F
               ? Math.max(1, Math.round((float)slotWidth * 9.0F / 16.0F) + 18)
               : Math.max(1, (WallLayout.toPixels(this.height, viewportHeight) - gap * (this.rows - 1)) / Math.max(1, this.rows));

            for (int i = 0; i < this.rows; i++) {
               for (int j = 0; j < this.columns; j++) {
                  float xPos = (float)(originX + j * (slotWidth + gap));
                  float yPos = (float)(originY + i * (slotHeight + gap));
                  slots.add(new WallLayout.Slot(xPos, yPos, (float)slotWidth, (float)slotHeight, this.instanceBackground, this.cosmetic));
               }
            }

            return slots;
         }
      }
   }

   public static final class Slot {
      final float x;
      final float y;
      final float width;
      final float height;
      final boolean instanceBackground;
      final boolean cosmetic;

      Slot(float x, float y, float width, float height, boolean instanceBackground, boolean cosmetic) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.instanceBackground = instanceBackground;
         this.cosmetic = cosmetic;
      }

      int pixelX(int count) {
         return WallLayout.toPixels(this.x, count);
      }

      int pixelY(int count) {
         return WallLayout.toPixels(this.y, count);
      }

      int pixelWidth(int count) {
         return WallLayout.toPixels(this.width, count);
      }

      int pixelHeight(int count) {
         return WallLayout.toPixels(this.height, count);
      }
   }
}
