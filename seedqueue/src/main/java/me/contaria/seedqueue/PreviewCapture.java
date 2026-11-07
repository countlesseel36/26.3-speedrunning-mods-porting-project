package me.contaria.seedqueue;

import net.minecraft.client.Minecraft;

/**
 * State of the "render a frame off-screen and turn it into a card" capture cycle.
 *
 * <p>Extracted from {@link SeedQueue}: the flag soup around hiding the HUD / swapping the screen used
 * to be spread over a dozen fields in the main class. Keeping it here makes the capture lifecycle
 * (begin -> hold a few frames -> restore) explicit and independent from the queue itself.</p>
 */
public final class PreviewCapture {
   /** Entry whose preview is currently being captured, or {@code null}. */
   static QueueEntry entry;
   /** Preview level built for {@link #entry}. */
   static me.voidxwalker.worldpreview.ExternalPreview preview;
   static int ticks;
   static boolean requested;
   static boolean shot;
   static long startMillis;
   static boolean swapTestDone;

   /** The game renders this frame instead of the wall while it is true. */
   static boolean active;
   static int hold;
   static boolean hudHidden;
   static net.minecraft.client.gui.screens.Screen savedScreen;

   private PreviewCapture() {
   }

   public static boolean isActive() {
      return active;
   }

   /** Starts a capture frame: hides the HUD, remembers the screen and keeps the override for a few frames. */
   public static void beginFrame(Minecraft minecraft) {
      active = true;
      hold = 3;
      savedScreen = minecraft.gui.screen();

      if (!minecraft.gui.hud.isHidden()) {
         minecraft.gui.hud.toggle();
         hudHidden = true;
      }
   }

   /** Called after every rendered frame; the override expires after {@link #hold} frames. */
   public static void afterFrame() {
      if (active && --hold <= 0) {
         active = false;
         me.voidxwalker.worldpreview.WorldPreview.instanceRendering = false;
      }
   }

   /** Restoration used when a capture ends, whether it succeeded or was interrupted. */
   public static void restore(Minecraft minecraft) {
      if (!active) {
         return;
      }

      if (savedScreen != null) {
         minecraft.gui.setScreen(savedScreen);
         savedScreen = null;
      }

      if (hudHidden) {
         minecraft.gui.hud.toggle();
         hudHidden = false;
      }

      active = false;
   }

   public static void stop() {
      active = false;
   }
}
