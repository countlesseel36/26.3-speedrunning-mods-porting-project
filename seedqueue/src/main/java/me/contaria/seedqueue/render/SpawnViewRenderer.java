package me.contaria.seedqueue.render;

import me.contaria.seedqueue.SeedQueue;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import me.contaria.seedqueue.render.*;

public final class SpawnViewRenderer {
   private static final int SUPERSAMPLE = Math.max(1, Integer.getInteger("seedqueue.previewSupersample", 1));
   private static final double FOV = Math.toRadians(70.0);

   private SpawnViewRenderer() {
   }

   public static NativeImage render(ClientLevel level, BlockPos pos, double ratio, double ratio2, boolean flag) {
      return render(level, pos, ratio, ratio2, flag, 320, 138);
   }

   public static NativeImage render(ClientLevel level, BlockPos pos, double ratio, double ratio2, boolean flag, int count, int count2) {
      NativeImage image = new NativeImage(count, count2, false);

      try {
         RespawnData respawnData = level.getRespawnData();
         if (respawnData != null) {
            ratio = (double)respawnData.yaw();
            ratio2 = (double)respawnData.pitch();
         }
      } catch (Throwable error) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error);
      }

      double ratio3 = Math.toRadians(ratio);
      double ratio4 = Math.toRadians(ratio2);
      double ratio5 = (double)pos.getX() + 0.5;
      double ratio6 = (double)pos.getZ() + 0.5;
      double ratio7 = (double)safeEyeHeight(level, pos) + 1.62;
      ratio7 = clearEyeHeight(level, ratio5, ratio7, ratio6);
      double ratio8 = -Math.sin(ratio3) * Math.cos(ratio4);
      double ratio9 = Math.sin(ratio4);
      double ratio10 = Math.cos(ratio3) * Math.cos(ratio4);
      if (flag) {
         ratio5 -= ratio8 * 3.5;
         ratio7 -= ratio9 * 3.5;
         ratio6 -= ratio10 * 3.5;
         ratio4 -= Math.toRadians(10.0);
         ratio8 = -Math.sin(ratio3) * Math.cos(ratio4);
         ratio9 = Math.sin(ratio4);
         ratio10 = Math.cos(ratio3) * Math.cos(ratio4);
      }

      double ratio11 = ratio10;
      double ratio12 = -ratio8;
      double ratio13 = Math.sqrt(ratio10 * ratio10 + ratio12 * ratio12);
      if (ratio13 < 1.0E-4) {
         ratio11 = 1.0;
         ratio12 = 0.0;
         ratio13 = 1.0;
      }

      ratio11 /= ratio13;
      ratio12 /= ratio13;
      double ratio14 = ratio9 * ratio12;
      double ratio15 = ratio10 * ratio11 - ratio8 * ratio12;
      double ratio16 = -ratio9 * ratio11;
      int count3 = count * SUPERSAMPLE;
      int count4 = count2 * SUPERSAMPLE;
      int[] counts = new int[count3 * count4];
      double ratio17 = (double)count3 / (double)count4;
      double ratio18 = Math.tan(FOV * 0.5);
      MutableBlockPos mutableBlockPos = new MutableBlockPos();
      MutableBlockPos mutableBlockPos2 = new MutableBlockPos();

      for (int i = 0; i < count4; i++) {
         double ratio19 = (1.0 - 2.0 * ((double)i + 0.5) / (double)count4) * ratio18;
         int count5 = skyColor(ratio19, ratio9);

         for (int j = 0; j < count3; j++) {
            double ratio20 = (2.0 * ((double)j + 0.5) / (double)count3 - 1.0) * ratio17 * ratio18;
            double ratio21 = ratio8 + ratio11 * ratio20 + ratio14 * ratio19;
            double ratio22 = ratio9 + ratio15 * ratio19;
            double ratio23 = ratio10 + ratio12 * ratio20 + ratio16 * ratio19;
            double ratio24 = Math.sqrt(ratio21 * ratio21 + ratio22 * ratio22 + ratio23 * ratio23);
            ratio21 /= ratio24;
            ratio22 /= ratio24;
            ratio23 /= ratio24;
            int count6 = count5;
            mutableBlockPos2.set(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

            for (double ratio25 = 0.0; ratio25 < 256.0; ratio25 += 0.5) {
               int count7 = (int)Math.floor(ratio5 + ratio21 * ratio25);
               int count8 = (int)Math.floor(ratio7 + ratio22 * ratio25);
               int count9 = (int)Math.floor(ratio6 + ratio23 * ratio25);
               if (count7 != mutableBlockPos2.getX() || count8 != mutableBlockPos2.getY() || count9 != mutableBlockPos2.getZ()) {
                  mutableBlockPos2.set(count7, count8, count9);
                  mutableBlockPos.set(count7, count8, count9);

                  BlockState blockState;
                  try {
                     blockState = level.getBlockState(mutableBlockPos);
                  } catch (Throwable error2) {
                     blockState = null;
                  }

                  if (blockState != null && !blockState.isAir()) {
                     try {
                        if (blockState.getCollisionShape(level, mutableBlockPos).isEmpty() && blockState.getFluidState().isEmpty()) {
                           continue;
                        }
                     } catch (Throwable error3) {
         SeedQueue.LOGGER.debug("[seedqueue] ignored exception", error3);
      }

                     double ratio26 = ratio5 + ratio21 * ratio25;
                     double ratio27 = ratio7 + ratio22 * ratio25;
                     double ratio28 = ratio6 + ratio23 * ratio25;
                     double ratio29 = ratio26 - ((double)count7 + 0.5);
                     double ratio30 = ratio27 - ((double)count8 + 0.5);
                     double ratio31 = ratio28 - ((double)count9 + 0.5);
                     double ratio32;
                     double ratio33;
                     if (Math.abs(ratio30) >= Math.abs(ratio29) && Math.abs(ratio30) >= Math.abs(ratio31)) {
                        ratio32 = ratio26 - Math.floor(ratio26);
                        ratio33 = ratio28 - Math.floor(ratio28);
                     } else if (Math.abs(ratio29) >= Math.abs(ratio31)) {
                        ratio32 = ratio28 - Math.floor(ratio28);
                        ratio33 = 1.0 - (ratio27 - Math.floor(ratio27));
                     } else {
                        ratio32 = ratio26 - Math.floor(ratio26);
                        ratio33 = 1.0 - (ratio27 - Math.floor(ratio27));
                     }

                     int count10 = BlockTextureColors.sample(blockState, ratio32, ratio33);
                     int count11 = count10 >>> 24;
                     if (count11 >= 128) {
                        int count12 = count10 & 16777215;
                        boolean flag2 = !blockState.getFluidState().isEmpty();
                        if (flag2) {
                           String text = blockState.getBlock().getClass().getSimpleName();
                           if (!text.contains("Lava")) {
                              int count13 = count5 >> 16 & 0xFF;
                              int count14 = count5 >> 8 & 0xFF;
                              int count15 = count5 & 0xFF;
                              int count16 = count12 >> 16 & 0xFF;
                              int count17 = count12 >> 8 & 0xFF;
                              int count18 = count12 & 0xFF;
                              count12 = (int)((double)count16 * 0.55 + (double)count13 * 0.45) << 16
                                 | (int)((double)count17 * 0.55 + (double)count14 * 0.45) << 8
                                 | (int)((double)count18 * 0.55 + (double)count15 * 0.45);
                           }
                        }

                        double ratio34 = faceShade(ratio21, ratio22, ratio23);
                        int count19 = (int)((double)(count12 >> 16 & 0xFF) * ratio34);
                        int count20 = (int)((double)(count12 >> 8 & 0xFF) * ratio34);
                        int count21 = (int)((double)(count12 & 0xFF) * ratio34);
                        double ratio35 = Math.min(1.0, ratio25 / 153.6);
                        ratio35 = 1.0 - Math.pow(1.0 - ratio35, 3.0);
                        int count22 = count5 >> 16 & 0xFF;
                        int count23 = count5 >> 8 & 0xFF;
                        int count24 = count5 & 0xFF;
                        count19 = (int)((double)count19 * (1.0 - ratio35) + (double)count22 * ratio35);
                        count20 = (int)((double)count20 * (1.0 - ratio35) + (double)count23 * ratio35);
                        count21 = (int)((double)count21 * (1.0 - ratio35) + (double)count24 * ratio35);
                        count6 = 0xFF000000 | count19 << 16 | count20 << 8 | count21;
                        break;
                     }
                  }
               }
            }

            counts[i * count3 + j] = count6;
         }
      }

      for (int k = 0; k < count2; k++) {
         for (int l = 0; l < count; l++) {
            int count25 = 0;
            int count26 = 0;
            int count27 = 0;

            for (int index = 0; index < SUPERSAMPLE; index++) {
               int count28 = (k * SUPERSAMPLE + index) * count3 + l * SUPERSAMPLE;

               for (int index2 = 0; index2 < SUPERSAMPLE; index2++) {
                  int count29 = counts[count28 + index2];
                  count25 += count29 >> 16 & 0xFF;
                  count26 += count29 >> 8 & 0xFF;
                  count27 += count29 & 0xFF;
               }
            }

            int count30 = SUPERSAMPLE * SUPERSAMPLE;
            image.setPixel(l, k, 0xFF000000 | count25 / count30 << 16 | count26 / count30 << 8 | count27 / count30);
         }
      }

      return image;
   }

   private static double clearEyeHeight(ClientLevel level, double ratio, double ratio2, double ratio3) {
      for (int i = 0; i < 10; i++) {
         if (isAirAt(level, ratio, ratio2, ratio3) && isAirAt(level, ratio, ratio2 + 1.0, ratio3)) {
            return ratio2;
         }

         ratio2++;
      }

      return ratio2;
   }

   private static boolean isAirAt(ClientLevel level, double ratio, double ratio2, double ratio3) {
      try {
         return level.getBlockState(new BlockPos((int)Math.floor(ratio), (int)Math.floor(ratio2), (int)Math.floor(ratio3))).isAir();
      } catch (Throwable error) {
         return true;
      }
   }

   private static int safeEyeHeight(ClientLevel level, BlockPos pos) {
      MutableBlockPos mutableBlockPos = pos.mutable();

      for (int i = 0; i < 32; i++) {
         if (isAir(level, mutableBlockPos) && isAir(level, mutableBlockPos.above())) {
            return mutableBlockPos.getY();
         }

         mutableBlockPos.move(0, 1, 0);
      }

      return pos.getY();
   }

   private static boolean isAir(ClientLevel level, BlockPos pos) {
      try {
         return level.getBlockState(pos).isAir();
      } catch (Throwable error) {
         return true;
      }
   }

   private static double faceShade(double ratio, double ratio2, double ratio3) {
      if (ratio2 < -0.35) {
         return 1.0;
      } else {
         return ratio2 > 0.35 ? 0.55 : 0.78 + 0.06 * Math.min(1.0, Math.abs(ratio) + Math.abs(ratio3));
      }
   }

   private static int skyColor(double ratio, double ratio2) {
      double ratio3 = Math.max(0.0, Math.min(1.0, (ratio + 0.6) / 1.2));
      int count = 14148596;
      int count2 = 7247592;
      int count3 = count >> 16 & 0xFF;
      int count4 = count >> 8 & 0xFF;
      int count5 = count & 0xFF;
      int count6 = count2 >> 16 & 0xFF;
      int count7 = count2 >> 8 & 0xFF;
      int count8 = count2 & 0xFF;
      int count9 = (int)((double)count3 * (1.0 - ratio3) + (double)count6 * ratio3);
      int count10 = (int)((double)count4 * (1.0 - ratio3) + (double)count7 * ratio3);
      int count11 = (int)((double)count5 * (1.0 - ratio3) + (double)count8 * ratio3);
      return 0xFF000000 | count9 << 16 | count10 << 8 | count11;
   }
}
