package me.contaria.seedqueue;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a first-person view of a queued world spawn using the client chunk data.
 *
 * <p>This is a CPU ray marcher: it walks each pixel ray through the chunk blocks, colours hits
 * with the block map colour, applies simple face shading, distance fog and a sky gradient, and
 * anti-aliases the result by supersampling. The output is a genuine 3D view from the spawn
 * position, used for the live wall cards.
 */
public final class SpawnViewRenderer {
   private static final int DEFAULT_WIDTH = 320;
   private static final int DEFAULT_HEIGHT = 138;
   private static final int SUPERSAMPLE =
      Math.max(1, Integer.getInteger("seedqueue.previewSupersample", 1));
   private static final double FOV = Math.toRadians(70.0);
   private static final double MAX_DISTANCE = 128.0;
   private static final double STEP = 0.5;

   private SpawnViewRenderer() {
   }

   public static com.mojang.blaze3d.platform.NativeImage render(
      ClientLevel level,
      BlockPos spawn,
      double yawDegrees,
      double pitchDegrees,
      boolean thirdPerson
   ) {
      return render(level, spawn, yawDegrees, pitchDegrees, thirdPerson, DEFAULT_WIDTH, DEFAULT_HEIGHT);
   }

   public static com.mojang.blaze3d.platform.NativeImage render(
      ClientLevel level,
      BlockPos spawn,
      double yawDegrees,
      double pitchDegrees,
      boolean thirdPerson,
      int width,
      int height
   ) {
      com.mojang.blaze3d.platform.NativeImage image =
         new com.mojang.blaze3d.platform.NativeImage(width, height, false);

      double yaw = Math.toRadians(yawDegrees);
      double pitch = Math.toRadians(pitchDegrees);

      double eyeX = spawn.getX() + 0.5;
      double eyeZ = spawn.getZ() + 0.5;
      double eyeY = safeEyeHeight(level, spawn) + 1.62;
      // Give the camera a bit of head room so a spawn next to a tree or hill still shows terrain.
      eyeY = clearEyeHeight(level, eyeX, eyeY, eyeZ);
      // Look slightly down so the card shows the spawn area instead of mostly sky.
      pitch -= Math.toRadians(12.0);

      double forwardX = -Math.sin(yaw) * Math.cos(pitch);
      double forwardY = Math.sin(pitch);
      double forwardZ = Math.cos(yaw) * Math.cos(pitch);

      if (thirdPerson) {
         eyeX -= forwardX * 3.5;
         eyeY -= forwardY * 3.5;
         eyeZ -= forwardZ * 3.5;
         // Look slightly downwards from behind the player, like the vanilla third person camera.
         pitch -= Math.toRadians(10.0);
         forwardX = -Math.sin(yaw) * Math.cos(pitch);
         forwardY = Math.sin(pitch);
         forwardZ = Math.cos(yaw) * Math.cos(pitch);
      }

      double rightX = forwardZ;
      double rightZ = -forwardX;
      double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);

      if (rightLength < 1.0E-4) {
         rightX = 1.0;
         rightZ = 0.0;
         rightLength = 1.0;
      }

      rightX /= rightLength;
      rightZ /= rightLength;

      double upX = forwardY * rightZ;
      double upY = forwardZ * rightX - forwardX * rightZ;
      double upZ = -forwardY * rightX;

      int internalWidth = width * SUPERSAMPLE;
      int internalHeight = height * SUPERSAMPLE;
      int[] pixels = new int[internalWidth * internalHeight];
      double aspect = (double)internalWidth / (double)internalHeight;
      double tanHalfFov = Math.tan(FOV * 0.5);
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      BlockPos.MutableBlockPos last = new BlockPos.MutableBlockPos();

      for (int y = 0; y < internalHeight; y++) {
         double ndcY = (1.0 - 2.0 * ((double)y + 0.5) / (double)internalHeight) * tanHalfFov;
         int sky = skyColor(ndcY, forwardY);

         for (int x = 0; x < internalWidth; x++) {
            double ndcX = (2.0 * ((double)x + 0.5) / (double)internalWidth - 1.0) * aspect * tanHalfFov;

            double dirX = forwardX + rightX * ndcX + upX * ndcY;
            double dirY = forwardY + upY * ndcY;
            double dirZ = forwardZ + rightZ * ndcX + upZ * ndcY;
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            dirX /= length;
            dirY /= length;
            dirZ /= length;

            int color = sky;
            last.set(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

            for (double travelled = 0.0; travelled < MAX_DISTANCE; travelled += STEP) {
               int blockX = (int)Math.floor(eyeX + dirX * travelled);
               int blockY = (int)Math.floor(eyeY + dirY * travelled);
               int blockZ = (int)Math.floor(eyeZ + dirZ * travelled);

               if (blockX == last.getX() && blockY == last.getY() && blockZ == last.getZ()) {
                  continue;
               }

               last.set(blockX, blockY, blockZ);
               cursor.set(blockX, blockY, blockZ);

               BlockState state;

               try {
                  state = level.getBlockState(cursor);
               } catch (Throwable ignored) {
                  state = null;
               }

               if (state == null || state.isAir()) {
                  continue;
               }

               // Sample the block's texture at the hit position so surfaces keep their pattern.
               double hitX = eyeX + dirX * travelled;
               double hitY = eyeY + dirY * travelled;
               double hitZ = eyeZ + dirZ * travelled;
               double localX = hitX - (blockX + 0.5);
               double localY = hitY - (blockY + 0.5);
               double localZ = hitZ - (blockZ + 0.5);
               double u;
               double v;

               if (Math.abs(localY) >= Math.abs(localX) && Math.abs(localY) >= Math.abs(localZ)) {
                  u = hitX - Math.floor(hitX);
                  v = hitZ - Math.floor(hitZ);
               } else if (Math.abs(localX) >= Math.abs(localZ)) {
                  u = hitZ - Math.floor(hitZ);
                  v = 1.0 - (hitY - Math.floor(hitY));
               } else {
                  u = hitX - Math.floor(hitX);
                  v = 1.0 - (hitY - Math.floor(hitY));
               }

               int sampled = BlockTextureColors.sample(state, u, v);
               int alpha = sampled >>> 24;

               if (alpha < 128) {
                  continue;
               }

               int base = sampled & 0xFFFFFF;
               boolean fluid = !state.getFluidState().isEmpty();

               if (fluid) {
                  String blockName = state.getBlock().getClass().getSimpleName();

                  if (!blockName.contains("Lava")) {
                     int sr = (sky >> 16) & 0xFF;
                     int sg = (sky >> 8) & 0xFF;
                     int sb = sky & 0xFF;
                     int br = (base >> 16) & 0xFF;
                     int bg = (base >> 8) & 0xFF;
                     int bb = base & 0xFF;
                     base = (int)(br * 0.55 + sr * 0.45) << 16
                        | (int)(bg * 0.55 + sg * 0.45) << 8
                        | (int)(bb * 0.55 + sb * 0.45);
                  }
               }

               double shade = faceShade(dirX, dirY, dirZ);
               int red = (int)(((base >> 16) & 0xFF) * shade);
               int green = (int)(((base >> 8) & 0xFF) * shade);
               int blue = (int)((base & 0xFF) * shade);

               double fog = Math.min(1.0, travelled / MAX_DISTANCE);
               fog = fog * fog * fog;

               int skyRed = (sky >> 16) & 0xFF;
               int skyGreen = (sky >> 8) & 0xFF;
               int skyBlue = sky & 0xFF;
               red = (int)(red * (1.0 - fog) + skyRed * fog);
               green = (int)(green * (1.0 - fog) + skyGreen * fog);
               blue = (int)(blue * (1.0 - fog) + skyBlue * fog);

               color = 0xFF000000 | (red << 16) | (green << 8) | blue;
               break;
            }

            pixels[y * internalWidth + x] = color;
         }
      }

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int red = 0;
            int green = 0;
            int blue = 0;

            for (int sy = 0; sy < SUPERSAMPLE; sy++) {
               int row = (y * SUPERSAMPLE + sy) * internalWidth + x * SUPERSAMPLE;

               for (int sx = 0; sx < SUPERSAMPLE; sx++) {
                  int color = pixels[row + sx];
                  red += (color >> 16) & 0xFF;
                  green += (color >> 8) & 0xFF;
                  blue += color & 0xFF;
               }
            }

            int samples = SUPERSAMPLE * SUPERSAMPLE;
            image.setPixel(
               x, y, 0xFF000000 | (red / samples) << 16 | (green / samples) << 8 | blue / samples
            );
         }
      }

      return image;
   }


   /** Raises the eye until the block at eye level and two blocks ahead are air. */
   private static double clearEyeHeight(
      ClientLevel level, double x, double y, double z
   ) {
      for (int i = 0; i < 10; i++) {
         if (isAirAt(level, x, y, z) && isAirAt(level, x, y, z + 2.0) && isAirAt(level, x, y + 1.0, z + 2.0)) {
            return y;
         }

         y += 1.0;
      }

      return y;
   }

   private static boolean isAirAt(ClientLevel level, double x, double y, double z) {
      try {
         return level.getBlockState(
            new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z))
         ).isAir();
      } catch (Throwable ignored) {
         return true;
      }
   }

   /** Finds a feet height above the spawn that has two air blocks of head room. */
   private static int safeEyeHeight(ClientLevel level, BlockPos spawn) {
      BlockPos.MutableBlockPos cursor = spawn.mutable();

      for (int i = 0; i < 32; i++) {
         if (isAir(level, cursor) && isAir(level, cursor.above())) {
            return cursor.getY();
         }

         cursor.move(0, 1, 0);
      }

      return spawn.getY();
   }

   private static boolean isAir(ClientLevel level, BlockPos pos) {
      try {
         return level.getBlockState(pos).isAir();
      } catch (Throwable ignored) {
         return true;
      }
   }

   private static double faceShade(double dirX, double dirY, double dirZ) {
      if (dirY < -0.35) {
         // Looking down at a top face.
         return 1.0;
      }

      if (dirY > 0.35) {
         // Underside.
         return 0.55;
      }

      // Side faces are slightly darker and vary a little with the horizontal direction.
      return 0.78 + 0.06 * Math.min(1.0, Math.abs(dirX) + Math.abs(dirZ));
   }

   private static int skyColor(double ndcY, double forwardY) {
      double t = Math.max(0.0, Math.min(1.0, (ndcY + 0.6) / 1.2));
      int horizon = 0xD7E3F4;
      int zenith = 0x6E96E8;
      int hr = (horizon >> 16) & 0xFF;
      int hg = (horizon >> 8) & 0xFF;
      int hb = horizon & 0xFF;
      int zr = (zenith >> 16) & 0xFF;
      int zg = (zenith >> 8) & 0xFF;
      int zb = zenith & 0xFF;
      int red = (int)(hr * (1.0 - t) + zr * t);
      int green = (int)(hg * (1.0 - t) + zg * t);
      int blue = (int)(hb * (1.0 - t) + zb * t);
      return 0xFF000000 | (red << 16) | (green << 8) | blue;
   }
}
