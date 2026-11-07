package me.contaria.seedqueue;

import net.minecraft.client.Minecraft;

/**
 * Screenshot based card capture pipeline.
 *
 * <p>Moved out of {@link SeedQueue}: this is the "render an instance off-screen, grab a screenshot,
 * load it back as the card image" flow. It is currently only used by the diagnostic capture path,
 * but keeping it in its own class makes the pipeline separable from the queue logic.</p>
 */
public final class PreviewPipeline {
   private PreviewPipeline() {
   }

   /** Builds and captures a real rendered frame for each ready instance, one at a time. */
   public static void tick(Minecraft minecraft) {
      if (PreviewCapture.entry != null) {
         PreviewCapture.ticks++;

         if (!PreviewCapture.requested && PreviewCapture.ticks > 30) {
            PreviewCapture.requested = true;
            SeedQueue.LOGGER.info("[seedqueue] hiding UI before capturing instance #{}", PreviewCapture.entry.number);
            PreviewCapture.active = true;
            PreviewCapture.savedScreen = minecraft.gui.screen();

            if (PreviewCapture.savedScreen != null) {
               minecraft.setScreenAndShow(new BlankScreen());
            }

            if (!minecraft.gui.hud.isHidden()) {
               minecraft.gui.hud.toggle();
               PreviewCapture.hudHidden = true;
            }
         }

         if (Boolean.getBoolean("seedqueue.swapTest") && !PreviewCapture.swapTestDone && PreviewCapture.requested && PreviewCapture.ticks > 40) {
            PreviewCapture.swapTestDone = true;
            boolean swapped = me.voidxwalker.worldpreview.WorldPreview.swapMainTarget(minecraft);
            SeedQueue.LOGGER.info("[seedqueue] swap test: swapped={}", swapped);
            net.minecraft.client.Screenshot.grab(minecraft, false);
         }

         if (PreviewCapture.requested && !PreviewCapture.shot && PreviewCapture.ticks > 50) {
            PreviewCapture.shot = true;
            SeedQueue.LOGGER.info("[seedqueue] capturing spawn view for instance #{}", PreviewCapture.entry.number);
            PreviewCapture.startMillis = System.currentTimeMillis();
            requestDirectCapture(minecraft);
         }

         if (PreviewCapture.requested && PreviewCapture.entry.nativePreview == null
            && !(minecraft.gui.screen() instanceof BlankScreen)) {
            minecraft.setScreenAndShow(new BlankScreen());
            SeedQueue.LOGGER.info("[seedqueue] re-asserted blank screen for capture");
         }

         if (PreviewCapture.entry.nativePreview != null) {
            PreviewCapture.entry.uploadNativePreview();
            SeedQueue.restoreAfterCapture(minecraft);
            me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
            PreviewCapture.entry = null;
            PreviewCapture.preview = null;
            PreviewCapture.ticks = 0;
            PreviewCapture.requested = false;
         }

         return;
      }

      for (QueueEntry entry : SeedQueue.entries()) {
         if (entry.state == QueueEntry.State.READY && entry.mapColors != null
            && entry.previewTexture == null && entry.nativePreview == null && entry.server != null) {
            PreviewCapture.preview = me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, SeedQueue.previewRadius(entry));

            if (PreviewCapture.preview != null) {
               me.voidxwalker.worldpreview.WorldPreview.activateExternal(
                  new me.voidxwalker.worldpreview.WorldPreviewProperties(
                     PreviewCapture.preview.level, PreviewCapture.preview.listener, PreviewCapture.preview.gameMode, PreviewCapture.preview.player, PreviewCapture.preview.spawn
                  )
               );
               PreviewCapture.entry = entry;
               PreviewCapture.ticks = 0;
               PreviewCapture.requested = false;
               SeedQueue.LOGGER.info("[seedqueue] capturing 3D preview for entry #{}", entry.number);
            }

            break;
         }
      }
   }

   private static long previewFrameCounter;

   public static void frameSwapTick(Minecraft minecraft) {
      // Rendering a preview every frame costs a full world render for a card that is only ~20x12
      // pixels on screen. Skipping every other frame halves that cost and is invisible in motion.
      int every = Math.max(1, Integer.getInteger("seedqueue.previewEveryNthFrame", 2));

      if (every > 1 && (previewFrameCounter++ % every) != 0) {
         return;
      }

      previewFramesRendered++;

      if (SeedQueue.joinInProgress || PreviewState.handoffPreviewEntry != null) {
         // A join hand-off owns the client level right now (the preview world usually *is* the
         // joined world). Swapping another preview in here would replace the world the player just
         // entered with a card's preview level.
         if (PreviewState.frameSwapEntry != null) {
            me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
            PreviewState.frameSwapEntry = null;
      PreviewState.renderDistance = -1;
         }

         return;
      }

      if (!SeedQueue.wallPreviewEnabled()) {
         if (me.voidxwalker.worldpreview.WorldPreview.keepSodiumBound) {
            me.voidxwalker.worldpreview.WorldPreview.keepSodiumBound = false;
         }

         return;
      }

      boolean wall = minecraft.gui.screen() instanceof WallScreen;
      me.voidxwalker.worldpreview.WorldPreview.keepSodiumBound = wall || PreviewState.frameSwapEntry != null;

      long now = System.currentTimeMillis();

      if (PreviewState.frameSwapEntry != null) {
         if (!wall) {
            me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
            PreviewState.frameSwapEntry = null;
      PreviewState.renderDistance = -1;
            return;
         }

         if (now < PreviewState.frameSwapDwellUntil) {
            return;
         }

         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         PreviewState.frameSwapEntry.lastPreviewAt = now;
         PreviewState.frameSwapEntry = null;
      PreviewState.renderDistance = -1;
         SeedQueue.nextFrameSwapAt = now + SeedQueue.PREVIEW_GAP_MS;
         // Fall through: the next card can be armed in the same frame (Preview FPS throttle below).
      }

      if (!wall || now < SeedQueue.nextFrameSwapAt) {
         return;
      }

      java.util.List<QueueEntry> ready = SeedQueue.FRAME_SWAP_READY;
      ready.clear();

      for (QueueEntry entry : SeedQueue.entries()) {
         if (entry.state == QueueEntry.State.READY
            && entry.server != null
            && !(QueueConfig.freezeLockedPreviews && entry.locked)) {
            ready.add(entry);
         }
      }

      if (ready.isEmpty()) {
         // Everything is locked and frozen; keep the wall usable by previewing them anyway.
         for (QueueEntry entry : SeedQueue.entries()) {
            if (entry.state == QueueEntry.State.READY && entry.server != null) {
               ready.add(entry);
            }
         }
      }

      if (ready.isEmpty()) {
         return;
      }

      // Preview FPS: each card is rendered at most previewFps times per second. The dwell below is
      // shared between all cards, so the whole rotation runs at previewFps * cardCount frames/second.
      int previewFps = Math.max(1, QueueConfig.previewFps);
      long interval = Math.max(1L, 1000L / previewFps);
      QueueEntry entry = null;
      QueueEntry hovered = WallScreen.hoveredEntry();

      for (int i = 0; i < ready.size(); i++) {
         QueueEntry candidate = ready.get((SeedQueue.frameSwapIndex + i) % ready.size());
         // Idle Preview FPS: ready previews the player is not hovering update slower, keeping the wall
         // smooth when many worlds are ready (0 = use the normal Preview FPS for everything).
         int fps = QueueConfig.idlePreviewFPS > 0 && candidate != hovered
            ? QueueConfig.idlePreviewFPS
            : previewFps;
         long candidateInterval = Math.max(1L, 1000L / Math.max(1, fps));

         if (candidate.lastPreviewAt == 0 || now - candidate.lastPreviewAt >= candidateInterval) {
            entry = candidate;
            SeedQueue.frameSwapIndex = (SeedQueue.frameSwapIndex + i + 1) % ready.size();
            break;
         }
      }

      if (entry == null) {
         return;
      }

      entry.lastPreviewAt = now;
      applyPerspective();

      try {
         if (entry.externalPreview == null) {
            // READY SeedQueue.entries() are parked (server thread frozen), so build from the chunk snapshot that
            // generation already captured instead of asking the server for chunks.
            java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> snapshot;

            synchronized (entry.chunkPackets) {
               snapshot = new java.util.ArrayList<>(entry.chunkPackets);
            }

            entry.externalPreview = snapshot.size() >= 9
               ? me.voidxwalker.worldpreview.ExternalPreview.fromPackets(minecraft, entry.server, snapshot, SeedQueue.previewRadius(entry))
               : me.voidxwalker.worldpreview.ExternalPreview.build(minecraft, entry.server, SeedQueue.previewRadius(entry));
         }

         if (entry.externalPreview == null) {
            return;
         }

         SodiumLevelCache.markPreview(entry.externalPreview.level, entry.externalPreview.spawn);

         if (entry.renderProperties == null) {
            entry.renderProperties = new me.voidxwalker.worldpreview.WorldPreviewProperties(
               entry.externalPreview.level,
               entry.externalPreview.listener,
               entry.externalPreview.gameMode,
               entry.externalPreview.player,
               entry.externalPreview.spawn
            );
         }

         PreviewPipeline.ensureDisplayTarget(minecraft, entry);

         if (entry.displayTarget == null) {
            return;
         }

         me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = true;
         me.voidxwalker.worldpreview.WorldPreview.suppressDebugOverlay = true;
         me.voidxwalker.worldpreview.WorldPreview.activateExternal(entry.renderProperties);
         // Feed the chunk snapshot now: Sodium is already bound to this level (keepSodiumBound) and
         // its tracker only accepts chunks for the level it is rendering.
         entry.externalPreview.activateSections();

         if (!entry.pendingPackets.isEmpty()) {
            java.util.List<net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket> incoming;

            synchronized (entry.pendingPackets) {
               incoming = new java.util.ArrayList<>(entry.pendingPackets);
               entry.pendingPackets.clear();
            }

            if (!incoming.isEmpty()) {
               entry.externalPreview.applyNewPackets(incoming);
            }
         }

         if (!entry.sectionsPrepared) {
            // prepareExternal() calls allChanged(), which throws away every compiled section. Only do
            // that once per preview level instead of on every rotation (it dominated the wall frame).
            entry.sectionsPrepared = true;
            me.voidxwalker.worldpreview.WorldPreview.prepareExternal(minecraft);
         }

         me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = true;
         if (SeedQueue.CAMERA_LOG) {
            SeedQueue.LOGGER.info(
               "[seedqueue] card #{} camera={} spawnY={}",
               entry.number,
               entry.renderProperties.player.position(),
               entry.externalPreview.spawnPosY
            );
         }

         PreviewState.frameSwapEntry = entry;
         PreviewState.renderDistance = SeedQueue.previewRadius(entry) + 2;
         long dwell = Math.max(1L, Math.round(1000.0 / (previewFps * (double) Math.max(1, ready.size()))));
         PreviewState.frameSwapDwellUntil = now + dwell;
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to arm wall frame for #{}", entry.number, t);
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
         PreviewState.frameSwapEntry = null;
      PreviewState.renderDistance = -1;
      }
   }

   /** Applies the configured card perspective to WorldPreview's preview worlds. */
   public static void applyPerspective() {
      boolean thirdPerson = "third_person".equals(QueueConfig.perspective);
      String current = System.getProperty("worldpreview.thirdPerson");

      if (current == null || !current.equals(String.valueOf(thirdPerson))) {
         System.setProperty("worldpreview.thirdPerson", String.valueOf(thirdPerson));
      }
   }

   /** Creates the per-card render target lazily, at the configured (simulated) preview size. */
   /**
    * Downscale factor for the card texture.
    *
    * <p>Disabled (always 1): the downscaled readback path produced dark/empty cards in testing. 26.3's
    * {@code Screenshot.takeScreenshot(target, downscale, callback)} hands over a GPU-buffer backed image
    * that {@code uploadCardImage} cannot interpret, so the wall drew the clear colour. The window-sized
    * {@code copyColorFrom} path stays the only working option until a sprite-level blit is available.</p>
    */
   public static int cardDownscale() {
      return 1;
   }

   public static void ensureDisplayTarget(Minecraft minecraft, QueueEntry entry) {
      if (entry.displayTarget != null) {
         return;
      }

      if (cardDownscale() > 1) {
         // Scheme 1: no window-sized target at all. The frame is read back once per preview frame and
         // downscaled by the screenshot helper, so each card only owns a small GUI texture.
         return;
      }

      int width = Integer.getInteger("worldpreview.simulatedWidth", 0);
      int height = Integer.getInteger("worldpreview.simulatedHeight", 0);

      // copyColorFrom does not scale, so this target must stay at window size: a smaller target only
      // cropped the frame (cards looked zoomed).
      entry.displayTarget = me.voidxwalker.worldpreview.WorldPreview.createTarget(
         minecraft,
         "seedqueue card " + entry.number,
         width > 0 ? width : minecraft.getWindow().getWidth(),
         height > 0 ? height : minecraft.getWindow().getHeight()
      );
   }

   /**
    * Called at the GUI boundary: the main target holds the world-only frame, copy it into the card.
    *
    * <p>Skipped while the preview level is still compiling sections or while the frame has not settled,
    * so the wall never shows a half-built chunk. Moved here together with the rest of the preview
    * lifecycle; it only reads the bound entry from {@link PreviewState}.</p>
    */
   /** Incremented every time a preview frame is rendered; the copy below only runs on new frames. */
   public static long previewFramesRendered;

   private static long lastCopiedFrame = -1L;

   public static void captureWallFrame(Minecraft minecraft) {
      QueueEntry entry = PreviewState.frameSwapEntry;

      if (entry == null || entry.displayTarget == null) {
         return;
      }

      // The preview renders every Nth frame (seedqueue.previewEveryNthFrame); copying the full
      // render target on the frames in between is pure bandwidth for an identical image.
      if (lastCopiedFrame == previewFramesRendered && previewFramesRendered > 0) {
         return;
      }

      lastCopiedFrame = previewFramesRendered;

      long now = System.currentTimeMillis();

      // While the card sections are being re-sent / recompiled the frame is incomplete (terrain pops in
      // white). Keep the previous frame instead of copying it.
      if (entry.externalPreview != null && !entry.externalPreview.settled()) {
         return;
      }

      try {
         int downscale = cardDownscale();

         if (downscale > 1) {
            // Read the finished frame back and let Screenshot downscale it while copying.
            net.minecraft.client.Screenshot.takeScreenshot(
               minecraft.gameRenderer.mainRenderTarget(),
               downscale,
               image -> {
                  try {
                     entry.uploadCardImage(image);
                  } catch (Throwable t) {
                     SeedQueue.LOGGER.warn("[seedqueue] failed to upload downscaled card for #{}", entry.number, t);
                  }
               }
            );
            entry.lastRenderedAt = now;
            SeedQueue.renderedFrames++;
            return;
         }

         entry.displayTarget.copyColorFrom(minecraft.gameRenderer.mainRenderTarget());

         if (SeedQueue.CARD_READBACK) {
            net.minecraft.client.Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
               try {
                  entry.uploadCardImage(image);
                  entry.lastPreviewAt = System.currentTimeMillis();
               } catch (Throwable t) {
                  SeedQueue.LOGGER.warn("[seedqueue] failed to upload card for #{}", entry.number, t);
               }
            });
         }

         entry.lastRenderedAt = now;
         SeedQueue.renderedFrames++;
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] failed to capture wall frame for #{}", entry.number, t);
      }
   }

   /**
    * Drops every preview binding: stops the frame grab, restores the main render target and swaps the
    * client back to the real level. Kept next to the rest of the preview lifecycle.
    */
   public static void stop(Minecraft minecraft) {
      SeedQueue.pendingGrab = false;
      SeedQueue.grabStartedAt = 0;
      me.voidxwalker.worldpreview.WorldPreview.instanceRendering = false;
      me.voidxwalker.worldpreview.WorldPreview.forceLevelRender = false;
      me.voidxwalker.worldpreview.WorldPreview.restoreMainTarget(minecraft);
      me.voidxwalker.worldpreview.WorldPreview.hideDebugOverlay = false;
      me.voidxwalker.worldpreview.WorldPreview.suppressDebugOverlay = false;
      PreviewCapture.stop();

      if (PreviewState.activePreviewEntry != null) {
         try {
            me.voidxwalker.worldpreview.WorldPreview.swapOut(minecraft);
         } catch (Throwable t) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to swap out preview", t);
         }

         PreviewState.activePreviewEntry = null;
      }

      if (me.voidxwalker.worldpreview.WorldPreview.isActive()) {
         me.voidxwalker.worldpreview.WorldPreview.deactivateExternal();
      }

      SeedQueue.previewCaptured = false;
      SeedQueue.previewFramesLeft = 0;
      me.voidxwalker.worldpreview.WorldPreview.setTargetOverride(null);
   }

   /** Newest screenshot written after the capture started, or {@code null}. */
   /**
    * Grabs the frame straight from the render target into a {@link com.mojang.blaze3d.platform.NativeImage}
    * instead of writing a PNG to {@code screenshots/} and reading it back.
    *
    * <p>26.3 exposes {@code Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)}, which maps the
    * colour texture into a mapped buffer on the GPU and hands the pixels to the callback. The callback is
    * marshalled back to the client thread before the texture is uploaded.</p>
    */
   public static void requestDirectCapture(Minecraft minecraft) {
      if (!Boolean.parseBoolean(System.getProperty("seedqueue.directCapture", "true"))) {
         SeedQueue.LOGGER.info("[seedqueue] direct capture disabled, skipping the spawn view capture");
         PreviewCapture.startMillis = 0;
         return;
      }

      QueueEntry entry = PreviewCapture.entry;

      if (entry == null) {
         return;
      }

      try {
         net.minecraft.client.Screenshot.takeScreenshot(
            minecraft.gameRenderer.mainRenderTarget(),
            image -> minecraft.execute(() -> {
               if (PreviewCapture.entry != entry || entry.nativePreview != null) {
                  image.close();
                  return;
               }

               entry.nativePreview = image;
               PreviewCapture.startMillis = 0;
            })
         );
         SeedQueue.LOGGER.info("[seedqueue] direct framebuffer capture requested for instance #{}", entry.number);
      } catch (Throwable t) {
         SeedQueue.LOGGER.warn("[seedqueue] direct capture failed, skipping the spawn view capture", t);
         PreviewCapture.startMillis = 0;
      }
   }

   public static java.nio.file.Path findNewScreenshot() {
      try {
         java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");

         if (!java.nio.file.Files.isDirectory(dir)) {
            return null;
         }

         try (var stream = java.nio.file.Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".png"))
               .filter(p -> {
                  try {
                     return java.nio.file.Files.getLastModifiedTime(p).toMillis() >= PreviewCapture.startMillis;
                  } catch (Throwable t) {
                     return false;
                  }
               })
               .max(java.util.Comparator.comparingLong(p -> {
                  try {
                     return java.nio.file.Files.getLastModifiedTime(p).toMillis();
                  } catch (Throwable t) {
                     return 0L;
                  }
               }))
               .orElse(null);
         }
      } catch (Throwable t) {
         return null;
      }
   }

}
