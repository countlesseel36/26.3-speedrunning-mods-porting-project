package me.contaria.speedrunapi.mixin;

import me.contaria.speedrunapi.SpeedrunAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.screen.SpeedrunConfigScreen;
import me.contaria.speedrunapi.config.screen.SpeedrunModConfigsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the POSTLAUNCH init point once the client instance is fully constructed. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
   @Unique
   private static final String SPEEDRUNAPI_SMOKE = System.getProperty("speedrunapi.smoke", "");
   @Unique
   private static int speedrunapi$smokeTicks;
   @Unique
   private static boolean speedrunapi$smokeStarted;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void speedrunapi$onPostLaunch(CallbackInfo ci) {
      SpeedrunConfigAPI.onPostLaunch();
   }

   /** Test-only hook: opens the generated config screens and takes screenshots. */
   @Inject(method = "tick", at = @At("TAIL"))
   private void speedrunapi$smoke(CallbackInfo ci) {
      if (SPEEDRUNAPI_SMOKE.isEmpty()) {
         return;
      }

      Minecraft minecraft = (Minecraft) (Object) this;
      if (minecraft.level != null || minecraft.gui.overlay() != null) {
         return;
      }

      if (!speedrunapi$smokeStarted) {
         if (!(minecraft.gui.screen() instanceof TitleScreen)) {
            return;
         }

         speedrunapi$smokeStarted = true;
      }

      speedrunapi$smokeTicks++;
      if (SPEEDRUNAPI_SMOKE.equals("options")) {
         speedrunapi$optionsSmoke(minecraft);
      } else if (SPEEDRUNAPI_SMOKE.equals("toggle")) {
         speedrunapi$toggleSmoke(minecraft);
      } else {
         speedrunapi$configSmoke(minecraft);
      }
   }

   @Unique
   private static void speedrunapi$configSmoke(Minecraft minecraft) {
      if (speedrunapi$smokeTicks == 40) {
         minecraft.gui.setScreen(new SpeedrunModConfigsScreen(minecraft.gui.screen()));
      } else if (speedrunapi$smokeTicks == 70) {
         Screenshot.grab(minecraft, false);
         SpeedrunAPI.LOGGER.info("[smoke] captured mod config list");
      } else if (speedrunapi$smokeTicks == 80) {
         minecraft.gui.setScreen(new SpeedrunConfigScreen("standardsettings", minecraft.gui.screen()));
      } else if (speedrunapi$smokeTicks == 120) {
         Screenshot.grab(minecraft, false);
         SpeedrunAPI.LOGGER.info("[smoke] captured standardsettings config screen");
      } else if (speedrunapi$smokeTicks == 130) {
         minecraft.gui.setScreen(new TitleScreen());
         SpeedrunAPI.LOGGER.info("[smoke] closed config screens");
      }
   }

   /** Opens the vanilla options screen, dumps its buttons and clicks the Mod Configs button. */
   @Unique
   private static void speedrunapi$optionsSmoke(Minecraft minecraft) {
      if (speedrunapi$smokeTicks == 40) {
         minecraft.gui.setScreen(new OptionsScreen(minecraft.gui.screen(), minecraft.options));
      } else if (speedrunapi$smokeTicks == 70) {
         Screen screen = minecraft.gui.screen();
         SpeedrunAPI.LOGGER.info("[smoke] options screen: {}", screen == null ? "null" : screen.getClass().getName());
         if (screen != null) {
            for (GuiEventListener listener : screen.children()) {
               if (listener instanceof AbstractWidget widget) {
                  SpeedrunAPI.LOGGER.info(
                     "[smoke] widget {} '{}' at {},{},{}x{}, active={}",
                     widget.getClass().getSimpleName(),
                     widget.getMessage() == null ? "" : widget.getMessage().getString(),
                     widget.getX(),
                     widget.getY(),
                     widget.getWidth(),
                     widget.getHeight(),
                     widget.isActive()
                  );
               }
            }
         }
      } else if (speedrunapi$smokeTicks == 85) {
         speedrunapi$clickConfigButton(minecraft);
      } else if (speedrunapi$smokeTicks == 110) {
         Screenshot.grab(minecraft, false);
         SpeedrunAPI.LOGGER.info("[smoke] after click screen: {}", minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName());
      } else if (speedrunapi$smokeTicks == 130) {
         minecraft.gui.setScreen(new TitleScreen());
         SpeedrunAPI.LOGGER.info("[smoke] closed options smoke");
      }
   }

   @Unique
   private static void speedrunapi$clickConfigButton(Minecraft minecraft) {
      Screen screen = minecraft.gui.screen();
      if (screen == null) {
         return;
      }

      AbstractWidget target = null;
      for (GuiEventListener listener : screen.children()) {
         if (listener instanceof AbstractWidget widget && widget.getMessage() != null && widget.getMessage().getString().contains("Mod Config")) {
            target = widget;
            break;
         }
      }

      if (target == null) {
         SpeedrunAPI.LOGGER.warn("[smoke] config button not found");
         return;
      }

      double x = target.getX() + target.getWidth() / 2.0;
      double y = target.getY() + target.getHeight() / 2.0;

      for (GuiEventListener listener : screen.children()) {
         if (listener.isMouseOver(x, y)) {
            SpeedrunAPI.LOGGER.info("[smoke] child under point: {} at {}", listener.getClass().getName(), listener.getRectangle());
         }
      }

      for (GuiEventListener listener : screen.children()) {
         if (listener instanceof AbstractWidget widget && widget.isMouseOver(x, y)) {
            SpeedrunAPI.LOGGER.info(
               "[smoke] under point: {} '{}' at {},{},{}x{}",
               widget.getClass().getSimpleName(),
               widget.getMessage() == null ? "" : widget.getMessage().getString(),
               widget.getX(),
               widget.getY(),
               widget.getWidth(),
               widget.getHeight()
            );
         }
      }
      SpeedrunAPI.LOGGER.info("[smoke] clicking '{}' ({}) at {},{}", target.getMessage().getString(), target.getClass().getSimpleName(), x, y);

      try {
         boolean direct = screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)), false);
         SpeedrunAPI.LOGGER.info("[smoke] screen click={} -> {}", direct, minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName());
      } catch (Throwable t) {
         SpeedrunAPI.LOGGER.error("[smoke] widget click threw", t);
      }
   }

   /** Opens the StandardSettings config screen and toggles the first boolean option. */
   @Unique
   private static void speedrunapi$toggleSmoke(Minecraft minecraft) {
      if (speedrunapi$smokeTicks == 40) {
         minecraft.gui.setScreen(new SpeedrunConfigScreen("standardsettings", minecraft.gui.screen()));
      } else if (speedrunapi$smokeTicks == 70) {
         Screen screen = minecraft.gui.screen();
         SpeedrunAPI.LOGGER.info("[smoke] config screen: {}", screen == null ? "null" : screen.getClass().getName());
         if (screen != null) {
            for (GuiEventListener listener : screen.children()) {
               if (listener instanceof AbstractWidget widget) {
                  SpeedrunAPI.LOGGER.info(
                     "[smoke] widget {} '{}' at {},{},{}x{}, active={}",
                     widget.getClass().getSimpleName(),
                     widget.getMessage() == null ? "" : widget.getMessage().getString(),
                     widget.getX(),
                     widget.getY(),
                     widget.getWidth(),
                     widget.getHeight(),
                     widget.isActive()
                  );
               }
            }
         }

         speedrunapi$dumpOptions("before");
      } else if (speedrunapi$smokeTicks == 85) {
         speedrunapi$toggleFirstOption(minecraft);
      } else if (speedrunapi$smokeTicks == 100) {
         speedrunapi$dumpOptions("after");
         SpeedrunAPI.LOGGER.info("[smoke] screen after toggle: {}", minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName());
      } else if (speedrunapi$smokeTicks == 115) {
         Screenshot.grab(minecraft, false);
         SpeedrunAPI.LOGGER.info("[smoke] captured toggled config screen");
      } else if (speedrunapi$smokeTicks == 130) {
         SpeedrunAPI.LOGGER.info("[smoke] closing screen {}", minecraft.gui.screen() == null ? "null" : minecraft.gui.screen().getClass().getName());
         if (minecraft.gui.screen() != null) {
            minecraft.gui.screen().onClose();
         }

         try {
            SpeedrunAPI.LOGGER.info("[smoke] ao after save = {}", SpeedrunConfigAPI.getConfigContainer("standardsettings").getOption("ao").getString());
         } catch (Throwable t) {
            SpeedrunAPI.LOGGER.error("[smoke] ao check failed", t);
         }

         minecraft.gui.setScreen(new TitleScreen());
         SpeedrunAPI.LOGGER.info("[smoke] closed toggle smoke");
      }
   }

   @Unique
   private static void speedrunapi$dumpOptions(String phase) {
      try {
         var container = SpeedrunConfigAPI.getConfigContainer("standardsettings");
         int i = 0;
         for (var entry : container.getOptions().entrySet()) {
            SpeedrunAPI.LOGGER.info("[smoke] {} option {} = {}", phase, entry.getKey(), entry.getValue().getString());
            if (++i >= 5) {
               break;
            }
         }
      } catch (Throwable t) {
         SpeedrunAPI.LOGGER.error("[smoke] failed to dump options", t);
      }
   }

   @Unique
   private static void speedrunapi$toggleFirstOption(Minecraft minecraft) {
      Screen screen = minecraft.gui.screen();
      if (screen == null) {
         return;
      }

      AbstractWidget target = null;
      for (GuiEventListener listener : screen.children()) {
         if (listener instanceof AbstractWidget widget && widget.getMessage() != null) {
            String message = widget.getMessage().getString();
            if (widget.getMessage().getString().startsWith("Smooth Lighting")) {
               target = widget;
               break;
            }
         }
      }

      if (target == null) {
         SpeedrunAPI.LOGGER.warn("[smoke] no ON/OFF widget found");
         return;
      }

      double x = target.getX() + target.getWidth() / 2.0;
      double y = target.getY() + target.getHeight() / 2.0;
      SpeedrunAPI.LOGGER.info("[smoke] clicking '{}' ({}) at {},{}", target.getMessage().getString(), target.getClass().getSimpleName(), x, y);

      if (target instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container && !container.children().isEmpty()) {
         GuiEventListener first = container.children().get(0);
         if (first instanceof AbstractWidget widget && widget.getMessage() != null) {
            SpeedrunAPI.LOGGER.info("[smoke] inner value before: '{}'", widget.getMessage().getString());
         }
      }

      try {
         boolean handled = screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)), false);
         SpeedrunAPI.LOGGER.info("[smoke] toggle click={} message now '{}'", handled, target.getMessage().getString());

         try {
            var cfg = SpeedrunConfigAPI.getConfigContainer("standardsettings");
            SpeedrunAPI.LOGGER.info("[smoke] ao after click = {}", cfg.getOption("ao").getString());
            for (var entry : cfg.getOptions().entrySet()) {
               if (entry.getKey().toLowerCase().contains("smooth") || entry.getKey().toLowerCase().contains("light")) {
                  SpeedrunAPI.LOGGER.info("[smoke] candidate option {} = {}", entry.getKey(), entry.getValue().getString());
               }
            }
         } catch (Throwable t) {
            SpeedrunAPI.LOGGER.error("[smoke] candidate dump failed", t);
         }

         if (target instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container && !container.children().isEmpty()) {
            GuiEventListener firstChild = container.children().get(0);
            if (firstChild instanceof AbstractWidget widget && widget.getMessage() != null) {
               SpeedrunAPI.LOGGER.info("[smoke] inner value after: '{}'", widget.getMessage().getString());
            }

            for (GuiEventListener child : container.children()) {
               if (child instanceof AbstractWidget widget) {
                  SpeedrunAPI.LOGGER.info(
                     "[smoke] child {} '{}' at {},{},{}x{}, active={}, hovered={}",
                     widget.getClass().getSimpleName(),
                     widget.getMessage() == null ? "" : widget.getMessage().getString(),
                     widget.getX(),
                     widget.getY(),
                     widget.getWidth(),
                     widget.getHeight(),
                     widget.isActive(),
                     widget.isMouseOver(x, y)
                  );
               }
            }
         }
      } catch (Throwable t) {
         SpeedrunAPI.LOGGER.error("[smoke] toggle click threw", t);
      }
   }
}
