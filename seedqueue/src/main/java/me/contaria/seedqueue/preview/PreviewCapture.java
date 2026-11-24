package me.contaria.seedqueue.preview;

import me.voidxwalker.worldpreview.ExternalPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;

public final class PreviewCapture {
   public static QueueEntry entry;
   static ExternalPreview preview;
   static int ticks;
   static boolean requested;
   static boolean shot;
   static long startMillis;
   public static boolean active;
   public static int hold;
   static boolean hudHidden;
   static Screen savedScreen;

   private PreviewCapture() {
   }

   public static boolean isActive() {
      return active;
   }



   public static void restore(Minecraft minecraft) {
      if (active) {
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
   }

   public static void stop() {
      active = false;
   }
}
