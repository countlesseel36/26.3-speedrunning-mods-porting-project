package me.contaria.seedqueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Frame-graph integration for the wall cards.
 *
 * <p>26.3 renders the level by building a {@code FrameGraphBuilder} inside
 * {@code LevelRenderer.render}; the graph imports the game's main target as the external resource
 * {@code "main"} and every level pass writes into whatever {@code mainRenderTarget()} returns at
 * that moment. SeedQueue therefore has to make two things line up:</p>
 *
 * <ol>
 *   <li>the graph must import <em>our</em> per-entry {@link RenderTarget} (see
 *       {@code FrameGraphMixin}): the vanilla import happens before any pass runs, so redirecting
 *       the external resource is the only place to swap the attachment;</li>
 *   <li>the texture must never be an attachment and a sampled texture in the same frame. That is a
 *       framebuffer feedback loop (undefined pixels on GL, a validation error on Vulkan), so each
 *       entry renders into {@code renderTarget} and the finished frame is copied into a second,
 *       sample-only {@code displayTarget} that the wall blits.</li>
 * </ol>
 */
public final class WallPreview {
   private static final org.apache.logging.log4j.Logger LOGGER = SeedQueue.LOGGER;
   private static final ThreadLocal<Boolean> REDIRECTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

   /** Target that receives the level render of the instance currently being rendered. */
   private static volatile RenderTarget activeTarget;

   private static long lastImportLog;
   private static int dumps;

   private WallPreview() {
   }

   public static void begin(RenderTarget target) {
      activeTarget = target;
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

   public static void setRedirecting(boolean value) {
      REDIRECTING.set(value);
   }

   /** Logs every import while an instance render is running (rate limited to once a second). */
   public static void logImport(String name, Object resource) {
      if (!Boolean.getBoolean("seedqueue.graphLog")) {
         return;
      }

      long now = System.currentTimeMillis();

      if (now - lastImportLog < 1000L) {
         return;
      }

      lastImportLog = now;
      LOGGER.info("[seedqueue] graph import {} -> {} (redirect={})", name, resource, activeTarget);
   }

   /** Logs every frame-graph pass that actually executes while an instance render is running. */
   public static Runnable wrapPass(Runnable task, String name) {
      boolean log = Boolean.getBoolean("seedqueue.graphLog");
      boolean dump = Boolean.getBoolean("seedqueue.graphDump");

      if (!log && !dump) {
         return task;
      }

      return () -> {
         if (log) {
            LOGGER.info(
               "[seedqueue] graph pass '{}' active={} gameTarget={}",
               name,
               activeTarget,
               Minecraft.getInstance().gameRenderer.mainRenderTarget()
            );
         }

         task.run();

         if (dump) {
            writeNow("pass_" + name, Minecraft.getInstance().gameRenderer.mainRenderTarget());
         }
      };
   }

   public static void logPasses(Iterable<?> passes) {
      if (!Boolean.getBoolean("seedqueue.graphLog") || activeTarget == null) {
         return;
      }

      StringBuilder builder = new StringBuilder();

      for (Object pass : passes) {
         if (builder.length() > 0) {
            builder.append(", ");
         }

         builder.append(pass);
      }

      LOGGER.info("[seedqueue] graph passes: [{}]", builder);
   }

   /**
    * Copies a finished instance frame into the sample-only target of the card. Called right after
    * {@code LevelRenderer.render} so the copy is recorded after the level passes but before the GUI
    * pass samples it.
    */
   public static boolean publish(RenderTarget source, RenderTarget display) {
      if (source == null || display == null || source == display) {
         return false;
      }

      try {
         display.copyColorFrom(source);
         return true;
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to publish instance frame", t);
         return false;
      }
   }

   /** Reads the target back into a PNG for diagnostics (render thread, after the frame). */
   private static RenderTarget scratch;

   /**
    * Writes a target into a uniquely named PNG (no property gate, for smoke verification).
    *
    * <p>The game's main target cannot be read back directly in all cases, so the frame is first
    * copied into a plain off-screen target with {@code copyColorFrom} and that copy is captured.</p>
    */
   /** Resizes the diagnostic scratch target with the window. */
   public static void resizeScratch(int width, int height) {
      try {
         if (scratch != null && (scratch.width != width || scratch.height != height)) {
            scratch.resize(width, height);
         }
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to resize scratch target", t);
      }
   }

   public static void writeFrame(String label, RenderTarget target) {
      if (target == null) {
         return;
      }

      try {
         Minecraft minecraft = Minecraft.getInstance();

         if (scratch == null || scratch.width != target.width || scratch.height != target.height) {
            scratch = me.voidxwalker.worldpreview.WorldPreview.createTarget(
               minecraft, "seedqueue scratch", target.width, target.height
            );
         }

         if (scratch != null) {
            scratch.copyColorFrom(target);
            writeNow("frame_" + label, scratch);
            return;
         }
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] frame copy failed", t);
      }

      writeNow("frame_" + label, target);
   }

   public static void dump(String label, RenderTarget target) {
      if (target == null || !Boolean.getBoolean("seedqueue.dumpTargets")) {
         return;
      }

      int index = dumps++;

      try {
         Path dir = Minecraft.getInstance().gameDirectory.toPath();
         Path file = dir.resolve("seedqueue_card_" + label + "_" + index + ".png");
         Screenshot.takeScreenshot(target, image -> {
            try {
               write(image, file);
               LOGGER.info("[seedqueue] dumped {} ({}x{})", file.getFileName(), image.getWidth(), image.getHeight());
            } catch (Throwable t) {
               LOGGER.warn("[seedqueue] failed to write {}", file, t);
            }
         });
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] failed to dump target {}", label, t);
      }
   }

   /**
    * Diagnostic: verifies that an off-screen {@link RenderTarget} can be cleared and read back at
    * all. Writes {@code seedqueue_selftest_clear.png} (should be red) and
    * {@code seedqueue_selftest_copy.png} (a copy of the current main target).
    */
   public static void selfTest(Minecraft minecraft) {
      try {
         RenderTarget target = me.voidxwalker.worldpreview.WorldPreview.createTarget(
            minecraft, "seedqueue selftest", minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight()
         );

         if (target == null) {
            LOGGER.warn("[seedqueue] selftest: no target");
            return;
         }

         RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorAndDepthTextures(
               target.getColorTexture(), new org.joml.Vector4f(1.0F, 0.0F, 0.0F, 1.0F), target.getDepthTexture(), 0.0
            );
         writeNow("selftest_clear", target);

         target.copyColorFrom(minecraft.gameRenderer.mainRenderTarget());
         writeNow("selftest_copy", target);

         // Draw test: this goes through a RenderPass that writes into our target (not a copy).
         target.blitAndBlendToTexture(
            minecraft.gameRenderer.mainRenderTarget().getColorTextureView(), target.getColorTextureView()
         );
         writeNow("selftest_draw", target);

         LOGGER.info("[seedqueue] selftest targets written");
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] selftest failed", t);
      }
   }

   private static long lastSodiumLog;

   /** Logs how many sections with compiled geometry Sodium actually submits. */
   public static void reportSodiumDraw(Object pass, Object lists) {
      if (!Boolean.getBoolean("seedqueue.sodiumStats")) {
         return;
      }

      long now = System.currentTimeMillis();

      if (now - lastSodiumLog < 1000L) {
         return;
      }

      lastSodiumLog = now;

      try {
         int regions = 0;
         int sections = 0;
         java.util.Iterator<?> iterator = (java.util.Iterator<?>)lists.getClass().getMethod("iterator", boolean.class).invoke(lists, false);

         while (iterator.hasNext()) {
            Object list = iterator.next();
            regions++;
            sections += (Integer)list.getClass().getMethod("getSectionsWithGeometryCount").invoke(list);
         }

         LOGGER.info(
            "[seedqueue] sodium draw: pass={} regions={} sectionsWithGeometry={}", String.valueOf(pass), regions, sections
         );
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] sodium stats failed", t);
      }
   }

   /** Paints the target a solid colour so a later readback shows whether anything wrote to it. */
   public static void paint(RenderTarget target, float r, float g, float b, float a) {
      try {
         RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorAndDepthTextures(
               target.getColorTexture(), new org.joml.Vector4f(r, g, b, a), target.getDepthTexture(), 0.0
            );
      } catch (Throwable t) {
         LOGGER.warn("[seedqueue] paint failed", t);
      }
   }

   public static void snapshotNow(String label, RenderTarget target) {
      writeNow("paint_" + label, target);
   }

   private static int snapshots;

   private static void writeNow(String label, RenderTarget target) {
      Path file = Minecraft.getInstance().gameDirectory.toPath()
         .resolve("seedqueue_" + label + "_" + (snapshots++) + ".png");
      Screenshot.takeScreenshot(target, image -> {
         try {
            write(image, file);
            LOGGER.info("[seedqueue] wrote {}", file.getFileName());
         } catch (Throwable t) {
            LOGGER.warn("[seedqueue] failed to write {}", file, t);
         }
      });
   }

   private static void write(NativeImage image, Path file) throws Exception {
      Files.createDirectories(file.getParent());
      image.writeToFile(file.toFile());
   }

   /** A 1x1 probe that reports whether the graphics device is already recording this frame. */
   public static String device() {
      return String.valueOf(RenderSystem.getDevice());
   }
}
