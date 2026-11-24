package me.contaria.seedqueue.wall;
import me.contaria.seedqueue.SeedQueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.apache.logging.log4j.Logger;
import org.joml.Vector4f;
import me.contaria.seedqueue.wall.*;

public final class WallPreview {
   private static final Logger LOGGER = SeedQueue.LOGGER;
   private static final ThreadLocal<Boolean> REDIRECTING = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static volatile RenderTarget activeTarget;
   private static long lastImportLog;
   private static RenderTarget scratch;
   private static int snapshots;

   private WallPreview() {
   }

   public static void end() {
      activeTarget = null;
   }

   public static RenderTarget activeTarget() {
      return activeTarget;
   }

   public static boolean redirecting() {
      return REDIRECTING.get();
   }

   public static void setRedirecting(boolean flag) {
      REDIRECTING.set(flag);
   }

   public static void logImport(String text, Object obj) {
      if (Boolean.getBoolean("seedqueue.graphLog")) {
         long value = System.currentTimeMillis();
         if (value - lastImportLog >= 1000L) {
            lastImportLog = value;
            LOGGER.info("[seedqueue] graph import {} -> {} (redirect={})", text, obj, activeTarget);
         }
      }
   }

   public static Runnable wrapPass(Runnable runnable, String text) {
      boolean flag = Boolean.getBoolean("seedqueue.graphLog");
      boolean flag2 = Boolean.getBoolean("seedqueue.graphDump");
      return !flag && !flag2 ? runnable : () -> {
         if (flag) {
            LOGGER.info("[seedqueue] graph pass '{}' active={} gameTarget={}", text, activeTarget, Minecraft.getInstance().gameRenderer.mainRenderTarget());
         }

         runnable.run();
         if (flag2) {
            writeNow("pass_" + text, Minecraft.getInstance().gameRenderer.mainRenderTarget());
         }
      };
   }

   public static void logPasses(Iterable<?> iterable) {
      if (Boolean.getBoolean("seedqueue.graphLog") && activeTarget != null) {
         StringBuilder builder = new StringBuilder();

         for (Object obj : iterable) {
            if (builder.length() > 0) {
               builder.append(", ");
            }

            builder.append(obj);
         }

         LOGGER.info("[seedqueue] graph passes: [{}]", builder);
      }
   }

   public static boolean publish(RenderTarget target, RenderTarget target2) {
      if (target != null && target2 != null && target != target2) {
         try {
            target2.copyColorFrom(target);
            return true;
         } catch (Throwable error) {
            LOGGER.warn("[seedqueue] failed to publish instance frame", error);
            return false;
         }
      } else {
         return false;
      }
   }

   public static void resizeScratch(int count, int count2) {
      try {
         if (scratch != null && (scratch.width != count || scratch.height != count2)) {
            scratch.resize(count, count2);
         }
      } catch (Throwable error) {
         LOGGER.warn("[seedqueue] failed to resize scratch target", error);
      }
   }

   public static void paint(RenderTarget target, float ratio, float ratio2, float ratio3, float ratio4) {
      try {
         RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(ratio, ratio2, ratio3, ratio4), target.getDepthTexture(), 0.0);
      } catch (Throwable error) {
         LOGGER.warn("[seedqueue] paint failed", error);
      }
   }


   private static void writeNow(String text, RenderTarget target) {
      Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("seedqueue_" + text + "_" + snapshots++ + ".png");
      Screenshot.takeScreenshot(target, arg -> {
         try {
            write(arg, path);
            LOGGER.info("[seedqueue] wrote {}", path.getFileName());
         } catch (Throwable error) {
            LOGGER.warn("[seedqueue] failed to write {}", path, error);
         }
      });
   }

   private static void write(NativeImage image, Path path) throws Exception {
      Files.createDirectories(path.getParent());
      image.writeToFile(path.toFile());
   }

}
