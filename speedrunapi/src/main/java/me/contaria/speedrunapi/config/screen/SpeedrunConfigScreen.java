package me.contaria.speedrunapi.config.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import me.contaria.speedrunapi.SpeedrunAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public class SpeedrunConfigScreen extends Screen {
   private static final int ROW_HEIGHT = 24;
   private static final int WIDGET_WIDTH = 150;
   static final List<String> CATEGORY_ORDER = List.of(
      "queue", "wall", "performance", "misc", "troubleshooting", "advanced", "threading", "experimental", "debug"
   );
   private final String modID;
   private final Screen parent;
   private final List<SpeedrunOption<?>> options = new ArrayList<>();
   private SpeedrunOption<?> keybindingsOption;
   private int page;
   private int scroll;
   private boolean draggingScrollbar;
   private final List<SpeedrunOption<?>> shownOptions = new ArrayList<>();
   private Object keyBindingConfig;
   private Predicate<Key> keyListener;
   private boolean keyCaptureResolved;
   private Object focusedKeyBinding;
   private static final Set<String> drawnLogged = ConcurrentHashMap.newKeySet();
   private static boolean groupLogged;
   static final Map<String, String> BUCKET_TITLES = Map.ofEntries(
      Map.entry("zz.controls", "speedrunapi.group.controls"),
      Map.entry("zz.sound", "speedrunapi.group.sound"),
      Map.entry("zz.video", "speedrunapi.group.video"),
      Map.entry("zz.chat", "speedrunapi.group.chat"),
      Map.entry("zz.mouse", "speedrunapi.group.mouse"),
      Map.entry("zz.skin", "speedrunapi.group.skin"),
      Map.entry("zz.accessibility", "speedrunapi.group.accessibility"),
      Map.entry("zz.debug", "speedrunapi.group.debug"),
      Map.entry("queue", "speedrunapi.group.queue"),
      Map.entry("wall", "speedrunapi.group.wall"),
      Map.entry("performance", "speedrunapi.group.performance"),
      Map.entry("advanced", "speedrunapi.group.advanced"),
      Map.entry("threading", "speedrunapi.group.threading"),
      Map.entry("misc", "speedrunapi.group.misc"),
      Map.entry("benchmark", "speedrunapi.group.benchmark"),
      Map.entry("debug", "speedrunapi.group.debug"),
      Map.entry("f3", "speedrunapi.group.f3"),
      Map.entry("more", "speedrunapi.group.more"),
      Map.entry("onWorldJoin", "speedrunapi.group.onWorldJoin"),
      Map.entry("menu.options", "speedrunapi.group.vanillaOptions"),
      Map.entry("options.lang", "speedrunapi.group.vanillaLanguage"),
      Map.entry("options.online", "speedrunapi.group.vanillaOnline"),
      Map.entry("options.telemetry", "speedrunapi.group.vanillaTelemetry"),
      Map.entry("options.chat", "speedrunapi.group.chat"),
      Map.entry("options.mouse", "speedrunapi.group.mouse"),
      Map.entry("options.sound", "speedrunapi.group.sound"),
      Map.entry("options.video", "speedrunapi.group.video"),
      Map.entry("options.accessibility", "speedrunapi.group.accessibility")
   );
   static final List<String> GROUP_ORDER = List.of(
      "speedrunapi.group.global",
      "speedrunapi.group.queue",
      "speedrunapi.group.wall",
      "speedrunapi.group.performance",
      "speedrunapi.group.advanced",
      "speedrunapi.group.threading",
      "speedrunapi.group.experimental",
      "speedrunapi.group.controls",
      "speedrunapi.group.video",
      "speedrunapi.group.chat",
      "speedrunapi.group.sound",
      "speedrunapi.group.mouse",
      "speedrunapi.group.skin",
      "speedrunapi.group.accessibility",
      "speedrunapi.group.f3",
      "speedrunapi.group.onWorldJoin",
      "speedrunapi.group.more",
      "speedrunapi.group.misc",
      "speedrunapi.group.debug"
   );
   private static Class<?> keyClass;
   private static Method bindingGet;
   private static Method bindingSetVanilla;
   private static Method bindingSet;
   private static boolean capabilityProbed;
   private static boolean capabilityAvailable;

   public SpeedrunConfigScreen(String text, Screen screen) {
      super(TextUtil.translatable("speedrunapi.gui.config.settings"));
      this.modID = text;
      this.parent = screen;
      this.reloadOptions();
   }

   private void reloadOptions() {
      this.options.clear();
      this.keybindingsOption = null;

      try {
         SpeedrunConfigContainer container = SpeedrunConfigAPI.getConfigContainer(this.modID);
         SpeedrunConfig config = container.getConfig();
         ArrayList list = new ArrayList<>(container.getOptions().entrySet());
         list.sort(
            Comparator.<Entry>comparingInt(var0 -> optionPriority((SpeedrunOption<?>)var0.getValue()))
               .thenComparingInt(var0 -> groupIndex((SpeedrunOption<?>)var0.getValue()))
               .thenComparing(var0 -> categoryBucket(((SpeedrunOption)var0.getValue()).getCategory()))
               .thenComparing(
                  var0 -> {
                     String text = ((SpeedrunOption)var0.getValue()).getID();
                     return !text.startsWith("key_")
                           && !text.startsWith("key.")
                           && !text.endsWith("debug.overlay")
                           && !"speedrunapi.group.debug".equals(groupKey((SpeedrunOption<?>)var0.getValue()))
                        ? (String)var0.getKey()
                        : "";
                  }
               )
         );
         if (!groupLogged) {
            groupLogged = true;

            for (Object o5 : list) {
               Entry entry = (Entry) o5;
               SpeedrunAPI.LOGGER
                  .info(
                     "[speedrunapi] {} -> {} (category={})",
                     ((SpeedrunOption)entry.getValue()).getID(),
                     groupKey((SpeedrunOption<?>)entry.getValue()),
                     ((SpeedrunOption)entry.getValue()).getCategory()
                  );
            }
         }

         for (Object o8 : list) {
            Entry entry2 = (Entry) o8;
            if (((SpeedrunOption)entry2.getValue()).hasWidget()
               && (config == null || config.shouldShowOption((String)entry2.getKey()))
               && (config == null || config.shouldShowCategory(((SpeedrunOption)entry2.getValue()).getCategory()))) {
               if ("keyBindings".equals(((SpeedrunOption)entry2.getValue()).getID())) {
                  this.keybindingsOption = (SpeedrunOption<?>)entry2.getValue();
               } else {
                  this.options.add((SpeedrunOption<?>)entry2.getValue());
               }
            }
         }
      } catch (Exception exception) {
         SpeedrunConfigAPI.handleLoadException(this.modID, exception);
      }
   }

   private static  String categoryBucket(String text) {
      return OptionGrouping.categoryBucket(text);
   }

   private static  String groupLabel(String text, String text2) {
      return OptionGrouping.groupLabel(text, text2);
   }

   private static  int optionPriority(SpeedrunOption<?> option) {
      return OptionGrouping.optionPriority(option);
   }

   private static  String groupKey(SpeedrunOption<?> option) {
      return OptionGrouping.groupKey(option);
   }

   private static  String groupTitle(SpeedrunOption<?> option) {
      return OptionGrouping.groupTitle(option);
   }

   private static  int groupIndex(SpeedrunOption<?> option) {
      return OptionGrouping.groupIndex(option);
   }

   private static  String bucketTitle(String text) {
      return OptionGrouping.bucketTitle(text);
   }

   private static  int categoryIndex(String text) {
      return OptionGrouping.categoryIndex(text);
   }

   private int maxScroll(int count) {
      return Math.max(0, count * 24 - (this.height - 96) + 24);
   }

   private int rowY(int count) {
      return 48 + count * 24 - this.scroll;
   }

   private boolean rowVisible(int count) {
      int count2 = this.rowY(count);
      return count2 >= 40 && count2 + 20 <= this.height - 42;
   }

   private static boolean probeCapability(Object obj) {
      if (!capabilityProbed) {
         capabilityProbed = true;

         try {
            keyClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
            bindingGet = obj.getClass().getMethod("get");
            bindingSetVanilla = obj.getClass().getMethod("setVanilla", keyClass);
            bindingSet = obj.getClass().getMethod("set", keyClass);
            capabilityAvailable = true;
         } catch (Throwable error) {
            capabilityAvailable = false;
            SpeedrunAPI.LOGGER.warn("[speedrunapi] key rebinding is unavailable; StandardSettings API not found", error);
         }
      }

      return capabilityAvailable;
   }

   private void pushToVanilla() {
      if (probeCapability(this.focusedKeyBinding)) {
         try {
            Object obj = bindingGet.invoke(this.focusedKeyBinding);
            bindingSetVanilla.invoke(this.focusedKeyBinding, obj);
         } catch (Throwable error) {
            SpeedrunAPI.LOGGER.warn("[speedrunapi] could not apply the rebound key", error);
         }
      }
   }

   private boolean beginKeyCapture(Object obj) {
      try {
         if (!this.keyCaptureResolved) {
            this.keyCaptureResolved = true;
            Class ClassValue = Class.forName("me.contaria.standardsettings.StandardSettings");
            this.keyBindingConfig = ClassValue.getField("config").get(null);
            this.keyListener = (Predicate<Key>)this.keyBindingConfig.getClass().getMethod("createInputListener").invoke(this.keyBindingConfig);
         }

         if (this.keyBindingConfig != null && this.keyListener != null) {
            this.keyBindingConfig.getClass().getMethod("setFocusedKeyBinding", obj.getClass()).invoke(this.keyBindingConfig, obj);
            this.focusedKeyBinding = obj;
            return true;
         } else {
            return false;
         }
      } catch (Throwable error) {
         SpeedrunAPI.LOGGER.warn("[speedrunapi] key capture is unavailable (StandardSettings not installed?)", error);
         return false;
      }
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      if (this.focusedKeyBinding != null && this.keyListener != null) {
         if (keyEvent.isEscape()) {
            try {
               Object obj = Class.forName("com.mojang.blaze3d.platform.InputConstants").getField("UNKNOWN").get(null);
               Class ClassValue = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
               bindingSet.invoke(this.focusedKeyBinding, obj);
            } catch (Throwable error) {
               SpeedrunAPI.LOGGER.warn("[speedrunapi] could not unbind the key binding", error);
            }

            this.pushToVanilla();
            this.focusedKeyBinding = null;
            return true;
         }

         Key key = InputConstants.getKey(keyEvent);
         if (this.keyListener.test(key)) {
            this.pushToVanilla();
            this.focusedKeyBinding = null;
            return true;
         }
      }

      return super.keyPressed(keyEvent);
   }

   private boolean overScrollbar(double ratio, double ratio2) {
      return this.maxScroll(this.shownOptions.size()) > 0 && ratio >= (double)(this.width - 14) && ratio2 >= 34.0 && ratio2 <= (double)(this.height - 40);
   }

   private void scrollToThumb(double ratio) {
      int count = this.maxScroll(this.shownOptions.size());
      byte byteValue = 34;
      int count2 = this.height - 40;
      int count3 = count2 - byteValue;
      int count4 = Math.max(16, count3 * count3 / (count3 + count));
      int count5 = (int)(ratio - (double)count4 / 2.0);
      int count6 = Math.max(1, count3 - count4);
      int count7 = Math.max(0, Math.min(count, (count5 - byteValue) * count / count6));
      this.scroll = count7 / 24 * 24;
      this.rebuildWidgets();
   }

   public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double ratio, double ratio2) {
      if (this.draggingScrollbar) {
         this.scrollToThumb(mouseButtonEvent.y());
         return true;
      } else {
         return super.mouseDragged(mouseButtonEvent, ratio, ratio2);
      }
   }

   public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
      this.draggingScrollbar = false;
      return super.mouseReleased(mouseButtonEvent);
   }

   public boolean mouseScrolled(double ratio, double ratio2, double ratio3, double ratio4) {
      int count = this.maxScroll(this.shownOptions.size());
      if (count <= 0) {
         return super.mouseScrolled(ratio, ratio2, ratio3, ratio4);
      } else {
         int count2 = Math.max(0, Math.min(count, this.scroll - (int)(ratio4 * 24.0)));
         count2 = count2 / 24 * 24;
         if (count2 != this.scroll) {
            this.scroll = count2;
            this.rebuildWidgets();
         }

         return true;
      }
   }

   public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean flag) {
      if (this.overScrollbar(mouseButtonEvent.x(), mouseButtonEvent.y())) {
         this.draggingScrollbar = true;
         this.scrollToThumb(mouseButtonEvent.y());
         return true;
      } else {
         if (this.focusedKeyBinding != null && this.keyListener != null) {
            int count = mouseButtonEvent.button();
            if (count >= 0 && count <= 7) {
               Key key = Type.MOUSE.getOrCreate(count);
               if (this.keyListener.test(key)) {
                  this.pushToVanilla();
                  this.focusedKeyBinding = null;
                  return true;
               }
            }
         }

         int count2 = (int)((mouseButtonEvent.y() + (double)this.scroll - 48.0) / 24.0);
         boolean flag2 = mouseButtonEvent.x() >= (double)(this.width / 2 - 160)
            && mouseButtonEvent.x() <= (double)(this.width / 2 + 160)
            && mouseButtonEvent.y() >= 40.0
            && mouseButtonEvent.y() <= (double)(this.height - 42);
         if (flag2 && count2 >= 0 && count2 < this.shownOptions.size()) {
            SpeedrunOption option = this.shownOptions.get(count2);
            if (option.getClass().getSimpleName().contains("KeyBindingStandardSetting") && this.beginKeyCapture(option)) {
               return true;
            }
         }

         return super.mouseClicked(mouseButtonEvent, flag);
      }
   }

   protected void init() {
      int count = this.options.size();
      int count2 = this.width / 2 + 10;
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll(count)));
      this.shownOptions.clear();

      for (int i = 0; i < count; i++) {
         SpeedrunOption option = this.options.get(i);
         AbstractWidget widget = option.createWidget();
         Component component = option.getDescription();
         if (component != null) {
            widget.setTooltip(Tooltip.create(component));
         }

         widget.setX(count2);
         widget.setY(this.rowY(i));
         widget.setWidth(150);
         widget.visible = this.rowVisible(i);
         this.addRenderableWidget(widget);
         this.shownOptions.add(option);
         if (Boolean.getBoolean("speedrunapi.logMissingNames")) {
            SpeedrunAPI.LOGGER.info("[speedrunapi] option {}:{} = '{}'", this.modID, option.getID(), option.getName() == null ? "?" : option.getName().getString());
         }
      }

      int count3 = this.height - 34;
      if (this.keybindingsOption != null) {
         AbstractWidget widget2 = this.keybindingsOption.createWidget();
         widget2.setX(this.width / 2 - 140);
         widget2.setY(count3);
         widget2.setWidth(200);
         this.addRenderableWidget(widget2);
      }

      this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), var1x -> this.onClose()).bounds(this.width / 2 + 70, count3, 120, 20).build());
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int count, int count2, float ratio) {
      super.extractRenderState(graphics, count, count2, ratio);
      graphics.fill(0, 0, this.width, 34, -1879048192);
      graphics.centeredText(this.font, this.title, this.width / 2, 14, -1);
      if (this.modID != null && this.modID.startsWith("seedqueue")) {
         graphics.centeredText(this.font, Component.translatable("speedrunapi.seedqueue.warning"), this.width / 2, 40, -43691);
      }

      int count3 = this.maxScroll(this.shownOptions.size());
      if (count3 > 0) {
         byte byteValue = 34;
         int count4 = this.height - 40;
         int count5 = count4 - byteValue;
         int count6 = Math.max(16, count5 * count5 / (count5 + count3));
         int count7 = byteValue + (count5 - count6) * this.scroll / count3;
         int count8 = this.width - 10;
         graphics.fill(count8, byteValue, count8 + 6, count4, 1090519039);
         graphics.fill(count8, count7, count8 + 6, count7 + count6, -6250336);
      }

      graphics.enableScissor(0, 36, this.width, this.height - 40);

      try {
         this.drawOptionLabels(graphics);
      } finally {
         graphics.disableScissor();
      }
   }

   private List<String> wrapText(String text, int count) {
      ArrayList list = new ArrayList();
      StringBuilder stringBuilder = new StringBuilder();

      for (String text2 : text.split(" ")) {
         String text3 = stringBuilder.length() == 0 ? text2 : stringBuilder + " " + text2;
         if (this.font.width(text3) > count && stringBuilder.length() > 0) {
            list.add(stringBuilder.toString());
            stringBuilder = new StringBuilder(text2);
         } else {
            stringBuilder = new StringBuilder(text3);
         }
      }

      if (stringBuilder.length() > 0) {
         list.add(stringBuilder.toString());
      }

      return list;
   }

   private void drawOptionLabels(GuiGraphicsExtractor graphics) {
      int count = this.width / 2 - 10;
      int count2 = Math.max(0, this.scroll / 24);
      String text = null;

      for (int i = 0; i < this.shownOptions.size(); i++) {
         SpeedrunOption option = this.shownOptions.get(i);
         String text2 = option.getCategory();
         String text3 = groupTitle(option);
         if (!text3.equals(text)) {
            text = text3;
         }

         String text4 = option.getID();
         String text5 = "speedrunapi.config." + this.modID + ".option." + text4;
         String text6 = Language.getInstance().getOrDefault(text5);
         if (text6.equals(text5)) {
            String text7 = Language.getInstance().getOrDefault(text4);
            if (!text7.equals(text4)) {
               text6 = text7;
            } else {
               String text8 = "options." + text4;
               String text9 = Language.getInstance().getOrDefault(text8);
               if (!text9.equals(text8)) {
                  text6 = text9;
               } else if (!text4.startsWith("key_") && !text4.startsWith("key.")) {
                  String text10 = text4;
                  if (text4.startsWith("debug.")) {
                     text10 = text4.substring("debug.".length());
                  }

                  int count3 = text10.indexOf(58);
                  if (count3 >= 0) {
                     text10 = text10.substring(count3 + 1);
                  }

                  text6 = pretty(text10);
               } else {
                  String text11 = text4;

                  for (int j = 0; j < 4; j++) {
                     if (text11.startsWith("key_")) {
                        text11 = text11.substring("key_".length());
                     } else {
                        if (!text11.startsWith("key.")) {
                           break;
                        }

                        text11 = text11.substring("key.".length());
                     }
                  }

                  String text12 = Language.getInstance().getOrDefault(text11);
                  if (text12.equals(text11)) {
                     String text13 = text11.substring(text11.lastIndexOf(46) + 1);
                     String text14 = Language.getInstance().getOrDefault("key.debug." + text13);
                     text6 = text14.equals("key.debug." + text13) ? pretty(text13) : text14;
                  } else {
                     text6 = text12;
                  }
               }
            }
         }

         if (this.rowVisible(i)) {
            int count4 = this.rowY(i) + 6;
            if (Boolean.getBoolean("speedrunapi.logMissingNames") && drawnLogged.add(this.modID + ":" + option.getID())) {
               SpeedrunAPI.LOGGER.info("[speedrunapi] drawn {}:{} = '{}'", this.modID, option.getID(), text6);
            }

            if (i == 0 || !groupTitle(this.shownOptions.get(i - 1)).trim().equals(text3.trim())) {
               graphics.text(this.font, text3, this.width / 2 - 210, this.rowY(i) + 6, -171);
            }

            boolean flag = option.getID().equals("toggleStandardSettings");
            graphics.text(this.font, text6, count - this.font.width(text6), count4, flag ? -171 : -1);
         }
      }
   }

   private static  String pretty(String text) {
      return OptionGrouping.pretty(text);
   }

   public void onClose() {
      this.saveQuietly();
      this.minecraft.gui.setScreen(this.parent);
   }

   private void saveQuietly() {
      try {
         SpeedrunConfigAPI.getConfigContainer(this.modID).save();
      } catch (Exception exception) {
         SpeedrunConfigAPI.handleSaveException(this.modID, exception);
      }
   }
}
