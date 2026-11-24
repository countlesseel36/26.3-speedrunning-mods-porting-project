package me.contaria.seedqueue.render;

import me.contaria.seedqueue.SeedQueue;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import me.contaria.seedqueue.render.*;

public final class BlockTextureColors {
   private static final Map<BlockState, BlockTextureColors.SpriteData> SPRITES = new HashMap<>();
   private static Field imageField;
   private static boolean reflectionFailed;
   private static final BlockTextureColors.SpriteData MISSING = new BlockTextureColors.SpriteData(null, 0, 0, 0);

   private BlockTextureColors() {
   }

   public static int color(BlockState blockState) {
      BlockTextureColors.SpriteData blockTextureColorsSpriteData = data(blockState);
      return blockTextureColorsSpriteData == null ? fallback(blockState) : blockTextureColorsSpriteData.average;
   }

   public static int sample(BlockState blockState, double ratio, double ratio2) {
      BlockTextureColors.SpriteData blockTextureColorsSpriteData = data(blockState);
      if (blockTextureColorsSpriteData == null) {
         return fallback(blockState);
      } else {
         int count = (int)(ratio * (double)blockTextureColorsSpriteData.width);
         int count2 = (int)(ratio2 * (double)blockTextureColorsSpriteData.height);
         if (count < 0) {
            count = 0;
         } else if (count >= blockTextureColorsSpriteData.width) {
            count = blockTextureColorsSpriteData.width - 1;
         }

         if (count2 < 0) {
            count2 = 0;
         } else if (count2 >= blockTextureColorsSpriteData.height) {
            count2 = blockTextureColorsSpriteData.height - 1;
         }

         int count3 = blockTextureColorsSpriteData.image.getPixel(count, count2);
         return (count3 >> 24 & 0xFF) < 128 ? blockTextureColorsSpriteData.average : 0xFF000000 | count3 & 16777215;
      }
   }

   private static BlockTextureColors.SpriteData data(BlockState blockState) {
      BlockTextureColors.SpriteData blockTextureColorsSpriteData = SPRITES.get(blockState);
      if (blockTextureColorsSpriteData != null) {
         return blockTextureColorsSpriteData == MISSING ? null : blockTextureColorsSpriteData;
      } else {
         BlockTextureColors.SpriteData blockTextureColorsSpriteData2 = compute(blockState);
         SPRITES.put(blockState, blockTextureColorsSpriteData2 == null ? MISSING : blockTextureColorsSpriteData2);
         return blockTextureColorsSpriteData2;
      }
   }

   private static BlockTextureColors.SpriteData compute(BlockState blockState) {
      if (reflectionFailed) {
         return null;
      } else {
         try {
            ModelManager modelManager = Minecraft.getInstance().getModelManager();
            Object obj = getField(modelManager, "blockStateModelSet");
            if (obj == null) {
               reflectionFailed = true;
               return null;
            } else {
               Object obj2 = obj.getClass().getMethod("getParticleMaterial", BlockState.class).invoke(obj, blockState);
               if (obj2 == null) {
                  return null;
               } else {
                  Object obj3 = obj2.getClass().getMethod("sprite").invoke(obj2);
                  Object obj4 = obj3.getClass().getMethod("contents").invoke(obj3);
                  int count = (Integer)obj4.getClass().getMethod("width").invoke(obj4);
                  int count2 = (Integer)obj4.getClass().getMethod("height").invoke(obj4);
                  NativeImage image = (NativeImage)getImage(obj4);
                  if (image != null && count > 0 && count2 > 0) {
                     int count3 = Math.max(1, count / 8);
                     int count4 = Math.max(1, count2 / 8);
                     long value = 0L;
                     long value2 = 0L;
                     long value3 = 0L;
                     int count5 = 0;

                     for (int i = 0; i < count2; i += count4) {
                        for (int j = 0; j < count; j += count3) {
                           int count6 = image.getPixel(j, i);
                           int count7 = count6 >> 24 & 0xFF;
                           if (count7 >= 128) {
                              value += (long)(count6 >> 16 & 0xFF);
                              value2 += (long)(count6 >> 8 & 0xFF);
                              value3 += (long)(count6 & 0xFF);
                              count5++;
                           }
                        }
                     }

                     if (count5 == 0) {
                        return null;
                     } else {
                        int count8 = (int)(value / (long)count5);
                        int count9 = (int)(value2 / (long)count5);
                        int count10 = (int)(value3 / (long)count5);
                        double[] ratios = tintFor(blockState);
                        count8 = Math.min(255, (int)((double)count8 * ratios[0]));
                        count9 = Math.min(255, (int)((double)count9 * ratios[1]));
                        count10 = Math.min(255, (int)((double)count10 * ratios[2]));
                        return new BlockTextureColors.SpriteData(image, count, count2, 0xFF000000 | count8 << 16 | count9 << 8 | count10);
                     }
                  } else {
                     return null;
                  }
               }
            }
         } catch (Throwable error) {
            reflectionFailed = true;
            return null;
         }
      }
   }

   private static int fallback(BlockState blockState) {
      return 0xFF000000 | blockState.getBlock().defaultMapColor().col;
   }

   private static Object getField(Object obj, String text) {
      try {
         for (Class clazz = obj.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
               Field field = clazz.getDeclaredField(text);
               field.setAccessible(true);
               return field.get(obj);
            } catch (NoSuchFieldException noSuchFieldException) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", noSuchFieldException);
      }
         }

         return null;
      } catch (Throwable error) {
         return null;
      }
   }

   private static Object getImage(Object obj) {
      try {
         if (imageField == null) {
            Field field = obj.getClass().getDeclaredField("originalImage");
            field.setAccessible(true);
            imageField = field;
         }

         return imageField.get(obj);
      } catch (Throwable error) {
         return null;
      }
   }

   private static double[] tintFor(BlockState blockState) {
      String text = blockState.getBlock().getClass().getSimpleName();
      if (text.equals("GrassBlock") || text.equals("ShortGrassBlock") || text.contains("Fern")) {
         return new double[]{0.62, 0.95, 0.42};
      } else {
         return !text.contains("Leaves") && !text.contains("Vine") ? new double[]{1.0, 1.0, 1.0} : new double[]{0.48, 0.82, 0.34};
      }
   }

   public static void clearCache() {
      SPRITES.clear();
   }

   private static final class SpriteData {
      private final NativeImage image;
      private final int width;
      private final int height;
      private final int average;

      private SpriteData(NativeImage image, int count, int count2, int count3) {
         this.image = image;
         this.width = count;
         this.height = count2;
         this.average = count3;
      }
   }
}
