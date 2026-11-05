package me.contaria.seedqueue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Wall layout, loaded from {@code assets/seedqueue/wall/custom_layout.json} in a resource pack.
 *
 * <p>The format follows the 1.16.1/1.21.4 wiki: a {@code main} group, an optional array of
 * {@code preparing} groups, an optional {@code locked} group and {@code replaceLockedInstances}.
 * Groups are either grids ({@code rows}/{@code columns}) or explicit {@code positions}. Coordinates
 * and sizes are pixels when they are whole numbers and screen fractions when they are 0..1.</p>
 */
public final class WallLayout {
   public static final class Slot {
      public final float x;
      public final float y;
      public final float width;
      public final float height;
      public final boolean instanceBackground;
      public final boolean cosmetic;

      Slot(float x, float y, float width, float height, boolean instanceBackground, boolean cosmetic) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.instanceBackground = instanceBackground;
         this.cosmetic = cosmetic;
      }

      public int pixelX(int screenWidth) {
         return toPixels(this.x, screenWidth);
      }

      public int pixelY(int screenHeight) {
         return toPixels(this.y, screenHeight);
      }

      public int pixelWidth(int screenWidth) {
         return toPixels(this.width, screenWidth);
      }

      public int pixelHeight(int screenHeight) {
         return toPixels(this.height, screenHeight);
      }
   }

   public static final class Group {
      private final int rows;
      private final int columns;
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private final float padding;
      private final List<Slot> positions;
      public final boolean instanceBackground;
      public final boolean cosmetic;

      private Group(
         int rows, int columns, float x, float y, float width, float height, float padding, List<Slot> positions,
         boolean instanceBackground, boolean cosmetic
      ) {
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

      /** Same placement, different grid size (used so the pack cannot override rows/columns). */
      private Group withGrid(int newRows, int newColumns) {
         return new Group(
            newRows, newColumns, this.x, this.y, this.width, this.height, this.padding, null,
            this.instanceBackground, this.cosmetic
         );
      }

      public List<Slot> resolve(int screenWidth, int screenHeight) {
         if (this.positions != null) {
            return this.positions;
         }

         List<Slot> slots = new ArrayList<>(Math.max(1, this.rows * this.columns));
         int originX = toPixels(this.x, screenWidth);
         int originY = toPixels(this.y, screenHeight);
         int totalWidth = toPixels(this.width, screenWidth);
         int gap = Math.round(this.padding);
         int cellWidth = Math.max(1, (totalWidth - gap * (this.columns - 1)) / Math.max(1, this.columns));
         // height < 0 means "auto": 16:9 preview + a label line, used by the built-in default.
         int cellHeight = this.height < 0.0F
            ? Math.max(1, Math.round(cellWidth * 9.0F / 16.0F) + 18)
            : Math.max(1, (toPixels(this.height, screenHeight) - gap * (this.rows - 1)) / Math.max(1, this.rows));

         for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < this.columns; column++) {
               float slotX = originX + column * (cellWidth + gap);
               float slotY = originY + row * (cellHeight + gap);
               slots.add(new Slot(slotX, slotY, cellWidth, cellHeight, this.instanceBackground, this.cosmetic));
            }
         }

         return slots;
      }
   }

   public final Group main;
   public final List<Group> preparing;
   public final Group locked;
   public final boolean replaceLockedInstances;

   private WallLayout(Group main, List<Group> preparing, Group locked, boolean replaceLockedInstances) {
      this.main = main;
      this.preparing = preparing;
      this.locked = locked;
      this.replaceLockedInstances = replaceLockedInstances;
   }

   private static int toPixels(float value, int screenSize) {
      return Math.round(value >= 0.0F && value <= 1.0F ? value * screenSize : value);
   }

   public static WallLayout defaults() {
      Group main = new Group(
         Math.max(1, QueueConfig.rows),
         Math.max(1, QueueConfig.columns),
         0.02F,
         0.05F,
         0.96F,
         -1.0F,
         8.0F,
         null,
         false,
         false
      );
      return new WallLayout(main, List.of(), null, false);
   }

   /** Loads the resource-pack layout, falling back to the built-in grid when absent or invalid. */
   public static WallLayout load(Minecraft minecraft) {
      try {
         Identifier id = Identifier.fromNamespaceAndPath("seedqueue", "wall/custom_layout.json");
         java.util.Optional<Resource> resource = minecraft.getResourceManager().getResource(id);

         if (resource.isEmpty()) {
            SeedQueue.LOGGER.info("[seedqueue] using default wall layout");
            return defaults();
         }

         try (InputStream in = resource.get().open()) {
            JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            Group main = json.has("main") ? parseGroup(json.getAsJsonObject("main")) : defaults().main;

            // The layout only positions the main grid; how many cards fit must still follow the
            // rows/columns settings, otherwise enabling the resource pack silently pinned the wall to
            // whatever grid the pack shipped (looked like "the setting does nothing").
            if (main != null && main.positions == null) {
               main = main.withGrid(
                  Math.max(1, Math.min(4, QueueConfig.rows)),
                  Math.max(1, Math.min(4, QueueConfig.columns))
               );
            }
            List<Group> preparing = new ArrayList<>();

            if (json.has("preparing")) {
               for (JsonElement element : json.getAsJsonArray("preparing")) {
                  preparing.add(parseGroup(element.getAsJsonObject()));
               }
            }

            Group locked = json.has("locked") ? parseGroup(json.getAsJsonObject("locked")) : null;
            boolean replaceLockedInstances = json.has("replaceLockedInstances") && json.get("replaceLockedInstances").getAsBoolean();
            SeedQueue.LOGGER.info(
               "[seedqueue] loaded custom wall layout (preparing groups={}, locked group={}, replaceLockedInstances={})",
               preparing.size(),
               locked != null,
               replaceLockedInstances
            );
            return new WallLayout(main, preparing, locked, replaceLockedInstances);
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load wall layout, using defaults", t);
         return defaults();
      }
   }

   private static Group parseGroup(JsonObject json) {
      boolean instanceBackground = json.has("instance_background") && json.get("instance_background").getAsBoolean();
      boolean cosmetic = json.has("cosmetic") && json.get("cosmetic").getAsBoolean();

      if (json.has("positions")) {
         List<Slot> positions = new ArrayList<>();

         for (JsonElement element : json.getAsJsonArray("positions")) {
            JsonObject position = element.getAsJsonObject();
            positions.add(
               new Slot(
                  number(position, "x", 0.0F),
                  number(position, "y", 0.0F),
                  number(position, "width", 1.0F),
                  number(position, "height", 1.0F),
                  instanceBackground,
                  cosmetic
               )
            );
         }

         return new Group(1, 1, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, positions, instanceBackground, cosmetic);
      }

      return new Group(
         Math.max(1, integer(json, "rows", 1)),
         Math.max(1, integer(json, "columns", 1)),
         number(json, "x", 0.0F),
         number(json, "y", 0.0F),
         number(json, "width", 1.0F),
         number(json, "height", 1.0F),
         number(json, "padding", 0.0F),
         null,
         instanceBackground,
         cosmetic
      );
   }

   private static float number(JsonObject json, String key, float fallback) {
      return json.has(key) ? json.get(key).getAsFloat() : fallback;
   }

   private static int integer(JsonObject json, String key, int fallback) {
      return json.has(key) ? json.get(key).getAsInt() : fallback;
   }
}
