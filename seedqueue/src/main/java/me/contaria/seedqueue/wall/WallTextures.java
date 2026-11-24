package me.contaria.seedqueue.wall;
import me.contaria.seedqueue.SeedQueue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Util;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.wall.*;

public final class WallTextures {
   private static final String[] LOCK_NAMES = new String[]{
      "lock", "lock-1", "lock-2", "lock-3", "lock-4", "lock-5", "lock-6", "lock-7", "lock-8", "lock-9", "lock-10", "lock-11", "lock-12"
   };
   public final WallTextures.Animated background;
   public final WallTextures.Animated overlay;
   public final WallTextures.Animated instanceBackground;
   public final List<WallTextures.Animated> locks;

   private WallTextures(WallTextures.Animated wallTexturesAnimated, WallTextures.Animated wallTexturesAnimated2, WallTextures.Animated wallTexturesAnimated3, List<WallTextures.Animated> list) {
      this.background = wallTexturesAnimated;
      this.overlay = wallTexturesAnimated2;
      this.instanceBackground = wallTexturesAnimated3;
      this.locks = list;
   }

   public static WallTextures load(Minecraft minecraft) {
      WallTextures.Animated wallTexturesAnimated = loadTexture(minecraft, "background");
      WallTextures.Animated wallTexturesAnimated2 = loadTexture(minecraft, "overlay");
      WallTextures.Animated wallTexturesAnimated3 = loadTexture(minecraft, "instance_background");
      ArrayList list = new ArrayList();
      ArrayList list2 = new ArrayList();
      int count = defaultWeight(minecraft);

      for (String text : LOCK_NAMES) {
         WallTextures.Animated wallTexturesAnimated4 = loadTexture(minecraft, text);
         if (wallTexturesAnimated4 != null) {
            list.add(wallTexturesAnimated4);
            int count2 = weight(minecraft, text, count);

            for (int i = 0; i < Math.max(1, count2); i++) {
               list2.add(wallTexturesAnimated4);
            }
         }
      }

      return new WallTextures(wallTexturesAnimated, wallTexturesAnimated2, wallTexturesAnimated3, list2);
   }

   public WallTextures.Animated lockFor(QueueEntry entry) {
      return this.locks.isEmpty() ? null : this.locks.get(Math.floorMod((long)(entry.number * 31) + entry.seed, this.locks.size()));
   }

   private static int defaultWeight(Minecraft minecraft) {
      return weight(minecraft, "lock", 1, true);
   }

   private static int weight(Minecraft minecraft, String text, int count) {
      return weight(minecraft, text, count, false);
   }

   private static int weight(Minecraft minecraft, String text, int count, boolean flag) {
      try {
         Identifier identifier = Identifier.fromNamespaceAndPath("seedqueue", "textures/gui/wall/" + text + ".png.mcmeta");
         Optional optional = minecraft.getResourceManager().getResource(identifier);
         if (optional.isEmpty()) {
            return count;
         } else {
            int count2;
            try (InputStream inputStream = ((Resource)optional.get()).open()) {
               JsonObject jsonObject = JsonParser.parseString(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
               JsonObject jsonObject2 = jsonObject.has("seedqueue") ? jsonObject.getAsJsonObject("seedqueue") : jsonObject;
               String text2 = flag ? "defaultWeight" : "weight";
               count2 = jsonObject2.has(text2) ? Math.max(1, jsonObject2.get(text2).getAsInt()) : count;
            }

            return count2;
         }
      } catch (Throwable error) {
         return count;
      }
   }

   private static WallTextures.Animated loadTexture(Minecraft minecraft, String text) {
      try {
         Identifier identifier = Identifier.fromNamespaceAndPath("seedqueue", "textures/gui/wall/" + text + ".png");
         Optional optional = minecraft.getResourceManager().getResource(identifier);
         if (optional.isEmpty()) {
            return null;
         } else {
            NativeImage image = NativeImage.read(((Resource)optional.get()).open());

            try {
               DynamicTexture texture = new DynamicTexture(() -> "seedqueue wall " + text, image);
               Identifier identifier2 = Identifier.fromNamespaceAndPath("seedqueue", "wall/" + text);
               minecraft.getTextureManager().register(identifier2, texture);
               int count = image.getWidth();
               int count2 = image.getHeight();
               int count3 = 1;
               int count4 = 1;
               Identifier identifier3 = Identifier.fromNamespaceAndPath("seedqueue", "textures/gui/wall/" + text + ".png.mcmeta");
               Optional optional2 = minecraft.getResourceManager().getResource(identifier3);
               if (optional2.isPresent()) {
                  try (InputStream inputStream = ((Resource)optional2.get()).open()) {
                     JsonObject jsonObject = JsonParser.parseString(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                     if (jsonObject.has("animation")) {
                        JsonObject jsonObject2 = jsonObject.getAsJsonObject("animation");
                        count4 = jsonObject2.has("frametime") ? Math.max(1, jsonObject2.get("frametime").getAsInt()) : 1;
                        if (count > 0 && count2 % count == 0) {
                           count3 = Math.max(1, count2 / count);
                           count2 = count;
                        }
                     }
                  }
               }

               return new WallTextures.Animated(identifier2, count, count2, count3, count4);
            } catch (Throwable error) {
               image.close();
               throw error;
            }
         }
      } catch (Throwable error2) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to load wall texture {}", text, error2);
         return null;
      }
   }

   public static final class Animated {
      final Identifier id;
      final int textureWidth;
      final int textureHeight;
      final int frames;
      final int frameTicks;

      Animated(Identifier identifier, int count, int count2, int count3, int count4) {
         this.id = identifier;
         this.textureWidth = count;
         this.textureHeight = count2;
         this.frames = Math.max(1, count3);
         this.frameTicks = Math.max(1, count4);
      }

      void blit(GuiGraphicsExtractor guiGraphicsExtractor, int count, int count2, int count3, int count4) {
         int count5 = (int)(Util.getMillis() / 50L / (long)this.frameTicks % (long)this.frames);
         float ratio = (float)count5 / (float)this.frames;
         float ratio2 = (float)(count5 + 1) / (float)this.frames;
         guiGraphicsExtractor.blit(this.id, count, count2, count3, count4, 0.0F, 1.0F, ratio, ratio2);
      }
   }
}
