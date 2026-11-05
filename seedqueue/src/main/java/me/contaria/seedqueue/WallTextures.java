package me.contaria.seedqueue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Wall textures from a resource pack ({@code assets/seedqueue/textures/gui/wall/}):
 * background.png, overlay.png, instance_background.png and lock.png / lock-1.png ... with
 * {@code .mcmeta} animation and weight support.
 */
public final class WallTextures {
   public static final class Animated {
      public final Identifier id;
      public final int textureWidth;
      public final int textureHeight;
      public final int frames;
      public final int frameTicks;

      Animated(Identifier id, int textureWidth, int textureHeight, int frames, int frameTicks) {
         this.id = id;
         this.textureWidth = textureWidth;
         this.textureHeight = textureHeight;
         this.frames = Math.max(1, frames);
         this.frameTicks = Math.max(1, frameTicks);
      }

      public void blit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
         int frame = (int) ((net.minecraft.util.Util.getMillis() / 50L / this.frameTicks) % this.frames);
         float v0 = frame / (float) this.frames;
         float v1 = (frame + 1) / (float) this.frames;
         graphics.blit(this.id, x0, y0, x1, y1, 0.0F, 1.0F, v0, v1);
      }
   }

   private static final String[] LOCK_NAMES = new String[] {
      "lock", "lock-1", "lock-2", "lock-3", "lock-4", "lock-5", "lock-6", "lock-7", "lock-8",
      "lock-9", "lock-10", "lock-11", "lock-12"
   };

   public final Animated background;
   public final Animated overlay;
   public final Animated instanceBackground;
   public final List<Animated> locks;

   private WallTextures(Animated background, Animated overlay, Animated instanceBackground, List<Animated> locks) {
      this.background = background;
      this.overlay = overlay;
      this.instanceBackground = instanceBackground;
      this.locks = locks;
   }

   public static WallTextures load(Minecraft minecraft) {
      // No default background/instance texture: without a resource pack the wall stays black.
      Animated background = loadTexture(minecraft, "background");
      Animated overlay = loadTexture(minecraft, "overlay");
      Animated instanceBackground = loadTexture(minecraft, "instance_background");
      List<Animated> locks = new ArrayList<>();
      List<Animated> weightedLocks = new ArrayList<>();

      int defaultWeight = defaultWeight(minecraft);

      for (String name : LOCK_NAMES) {
         Animated lock = loadTexture(minecraft, name);

         if (lock != null) {
            locks.add(lock);
            int weight = weight(minecraft, name, defaultWeight);

            for (int i = 0; i < Math.max(1, weight); i++) {
               weightedLocks.add(lock);
            }
         }
      }

      return new WallTextures(background, overlay, instanceBackground, weightedLocks);
   }

   /** Chooses a lock texture for an entry; deterministic so it does not flicker between frames. */
   public Animated lockFor(QueueEntry entry) {
      if (this.locks.isEmpty()) {
         return null;
      }

      return this.locks.get(Math.floorMod(entry.number * 31 + entry.seed, this.locks.size()));
   }

   private static int defaultWeight(Minecraft minecraft) {
      return weight(minecraft, "lock", 1, true);
   }

   private static int weight(Minecraft minecraft, String name, int fallback) {
      return weight(minecraft, name, fallback, false);
   }

   private static int weight(Minecraft minecraft, String name, int fallback, boolean defaultWeightOnly) {
      try {
         Identifier id = Identifier.fromNamespaceAndPath("seedqueue", "textures/gui/wall/" + name + ".png.mcmeta");
         var resource = minecraft.getResourceManager().getResource(id);

         if (resource.isEmpty()) {
            return fallback;
         }

         try (InputStream in = resource.get().open()) {
            JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject seedqueue = json.has("seedqueue") ? json.getAsJsonObject("seedqueue") : json;
            String key = defaultWeightOnly ? "defaultWeight" : "weight";
            return seedqueue.has(key) ? Math.max(1, seedqueue.get(key).getAsInt()) : fallback;
         }
      } catch (Throwable t) {
         return fallback;
      }
   }

   private static Animated loadTexture(Minecraft minecraft, String name) {
      try {
         Identifier id = Identifier.fromNamespaceAndPath("seedqueue", "textures/gui/wall/" + name + ".png");
         var resource = minecraft.getResourceManager().getResource(id);

         if (resource.isEmpty()) {
            return null;
         }

         var image = com.mojang.blaze3d.platform.NativeImage.read(resource.get().open());

         try {
            var texture = new DynamicTexture(() -> "seedqueue wall " + name, image);
            Identifier textureId = Identifier.fromNamespaceAndPath("seedqueue", "wall/" + name);
            minecraft.getTextureManager().register(textureId, texture);
            int frameWidth = image.getWidth();
            int frameHeight = image.getHeight();
            int frames = 1;
            int frameTicks = 1;
            Identifier metaId = Identifier.fromNamespaceAndPath("seedqueue", "textures/gui/wall/" + name + ".png.mcmeta");
            var meta = minecraft.getResourceManager().getResource(metaId);

            if (meta.isPresent()) {
               try (InputStream in = meta.get().open()) {
                  JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();

                  if (json.has("animation")) {
                     JsonObject animation = json.getAsJsonObject("animation");
                     frameTicks = animation.has("frametime") ? Math.max(1, animation.get("frametime").getAsInt()) : 1;

                     if (frameWidth > 0 && frameHeight % frameWidth == 0) {
                        frames = Math.max(1, frameHeight / frameWidth);
                        frameHeight = frameWidth;
                     }
                  }
               }
            }

            return new Animated(textureId, frameWidth, frameHeight, frames, frameTicks);
         } catch (Throwable t) {
            image.close();
            throw t;
         }
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load wall texture {}", name, t);
         return null;
      }
   }

}
