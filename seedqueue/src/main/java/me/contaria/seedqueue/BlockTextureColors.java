package me.contaria.seedqueue;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves a block's particle texture so the spawn view renderer can use real game textures
 * instead of flat map colours.
 */
public final class BlockTextureColors {
   private static final Map<BlockState, SpriteData> SPRITES = new HashMap<>();
   private static Field imageField;
   private static boolean reflectionFailed;

   private BlockTextureColors() {
   }

   public static int color(BlockState state) {
      SpriteData data = data(state);
      return data == null ? fallback(state) : data.average;
   }

   public static int sample(BlockState state, double u, double v) {
      SpriteData data = data(state);

      if (data == null) {
         return fallback(state);
      }

      int x = (int)(u * data.width);
      int y = (int)(v * data.height);

      if (x < 0) {
         x = 0;
      } else if (x >= data.width) {
         x = data.width - 1;
      }

      if (y < 0) {
         y = 0;
      } else if (y >= data.height) {
         y = data.height - 1;
      }

      int pixel = data.image.getPixel(x, y);

      if (((pixel >> 24) & 0xFF) < 128) {
         return data.average;
      }

      return 0xFF000000 | pixel & 0xFFFFFF;
   }

   private static SpriteData data(BlockState state) {
      SpriteData cached = SPRITES.get(state);

      if (cached != null) {
         return cached == MISSING ? null : cached;
      }

      SpriteData data = compute(state);
      SPRITES.put(state, data == null ? MISSING : data);
      return data;
   }

   private static final SpriteData MISSING = new SpriteData(null, 0, 0, 0);

   private static SpriteData compute(BlockState state) {
      if (reflectionFailed) {
         return null;
      }

      try {
         Object modelManager = Minecraft.getInstance().getModelManager();
         Object modelSet = getField(modelManager, "blockStateModelSet");

         if (modelSet == null) {
            reflectionFailed = true;
            return null;
         }

         Object material = modelSet.getClass().getMethod("getParticleMaterial", BlockState.class).invoke(modelSet, state);

         if (material == null) {
            return null;
         }

         Object sprite = material.getClass().getMethod("sprite").invoke(material);
         Object contents = sprite.getClass().getMethod("contents").invoke(sprite);
         int width = (int)contents.getClass().getMethod("width").invoke(contents);
         int height = (int)contents.getClass().getMethod("height").invoke(contents);
         NativeImage image = (NativeImage)getImage(contents);

         if (image == null || width <= 0 || height <= 0) {
            return null;
         }

         int stepX = Math.max(1, width / 8);
         int stepY = Math.max(1, height / 8);
         long red = 0;
         long green = 0;
         long blue = 0;
         int count = 0;

         for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
               int pixel = image.getPixel(x, y);
               int alpha = (pixel >> 24) & 0xFF;

               if (alpha < 128) {
                  continue;
               }

               red += (pixel >> 16) & 0xFF;
               green += (pixel >> 8) & 0xFF;
               blue += pixel & 0xFF;
               count++;
            }
         }

         if (count == 0) {
            return null;
         }

         int r = (int)(red / count);
         int g = (int)(green / count);
         int b = (int)(blue / count);
         double[] tint = tintFor(state);
         r = Math.min(255, (int)(r * tint[0]));
         g = Math.min(255, (int)(g * tint[1]));
         b = Math.min(255, (int)(b * tint[2]));
         return new SpriteData(image, width, height, 0xFF000000 | r << 16 | g << 8 | b);
      } catch (Throwable t) {
         reflectionFailed = true;
         return null;
      }
   }

   private static int fallback(BlockState state) {
      return 0xFF000000 | state.getBlock().defaultMapColor().col;
   }

   private static Object getField(Object owner, String name) {
      try {
         Class<?> type = owner.getClass();

         while (type != null) {
            try {
               Field field = type.getDeclaredField(name);
               field.setAccessible(true);
               return field.get(owner);
            } catch (NoSuchFieldException ignored) {
               type = type.getSuperclass();
            }
         }

         return null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static Object getImage(Object contents) {
      try {
         if (imageField == null) {
            Field field = contents.getClass().getDeclaredField("originalImage");
            field.setAccessible(true);
            imageField = field;
         }

         return imageField.get(contents);
      } catch (Throwable t) {
         return null;
      }
   }

   private static double[] tintFor(BlockState state) {
      String name = state.getBlock().getClass().getSimpleName();

      if (name.equals("GrassBlock") || name.equals("ShortGrassBlock") || name.contains("Fern")) {
         return new double[] { 0.62, 0.95, 0.42 };
      }

      if (name.contains("Leaves") || name.contains("Vine")) {
         return new double[] { 0.48, 0.82, 0.34 };
      }

      return new double[] { 1.0, 1.0, 1.0 };
   }

   public static void clearCache() {
      SPRITES.clear();
   }

   private static final class SpriteData {
      private final NativeImage image;
      private final int width;
      private final int height;
      private final int average;

      private SpriteData(NativeImage image, int width, int height, int average) {
         this.image = image;
         this.width = width;
         this.height = height;
         this.average = average;
      }
   }
}
