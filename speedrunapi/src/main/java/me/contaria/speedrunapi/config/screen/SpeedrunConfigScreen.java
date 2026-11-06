package me.contaria.speedrunapi.config.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpeedrunConfigScreen extends Screen {
   private static final int ROW_HEIGHT = 24;
   private static final int WIDGET_WIDTH = 150;
   private static final List<String> CATEGORY_ORDER = List.of(
      "queue", "wall", "performance", "misc", "troubleshooting", "advanced", "threading", "experimental", "debug"
   );

   private final String modID;
   private final Screen parent;
   private final List<SpeedrunOption<?>> options = new ArrayList<>();
   private SpeedrunOption<?> keybindingsOption;
   private int page;
   private final List<SpeedrunOption<?>> shownOptions = new ArrayList<>();
   /** StandardSettings support for rebinding keys: resolved lazily so SpeedrunAPI has no hard dependency. */
   private Object keyBindingConfig;
   private java.util.function.Predicate<com.mojang.blaze3d.platform.InputConstants.Key> keyListener;
   private boolean keyCaptureResolved;
   private Object focusedKeyBinding;
   private static final java.util.Set<String> drawnLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

   public SpeedrunConfigScreen(String modID, Screen parent) {
      super(TextUtil.translatable("speedrunapi.gui.config.settings"));
      this.modID = modID;
      this.parent = parent;
      this.reloadOptions();
   }

   private void reloadOptions() {
      this.options.clear();
      this.keybindingsOption = null;

      try {
         SpeedrunConfigContainer<?> container = SpeedrunConfigAPI.getConfigContainer(this.modID);
         me.contaria.speedrunapi.config.api.SpeedrunConfig config = container.getConfig();
         List<Map.Entry<String, SpeedrunOption<?>>> entries = new ArrayList<>(container.getOptions().entrySet());
         entries.sort(
            Comparator.<Map.Entry<String, SpeedrunOption<?>>>comparingInt(entry -> categoryIndex(entry.getValue().getCategory()))
               // "Toggle overlay" is the entry point of the debug block: an empty sort key puts it first
               // within its category.
               .thenComparing(entry -> entry.getValue().getID().endsWith("debug.overlay") ? "" : entry.getKey())
         );

         for (Map.Entry<String, SpeedrunOption<?>> entry : entries) {
            if (!entry.getValue().hasWidget()) {
               continue;
            }

            if (config != null && !config.shouldShowOption(entry.getKey())) {
               continue;
            }

            if (config != null && !config.shouldShowCategory(entry.getValue().getCategory())) {
               continue;
            }

            if ("keyBindings".equals(entry.getValue().getID())) {
               this.keybindingsOption = entry.getValue();
            } else {
               this.options.add(entry.getValue());
            }
         }
      } catch (Exception e) {
         SpeedrunConfigAPI.handleLoadException(this.modID, e);
      }
   }

   /**
    * Canonical bucket for an option's category.
    *
    * <p>Options coming from vanilla carry the vanilla group key (e.g. {@code options.sounds},
    * {@code key.category.movement}), so every one of them used to land after the known categories and
    * the list interleaved (a music row between two chat rows). Everything is folded into a handful of
    * buckets so equal kinds stay together.</p>
    */
   private static String categoryBucket(String category) {
      String value = category == null ? "" : category.toLowerCase(java.util.Locale.ROOT);

      if (value.contains("key.category") || value.contains("controls") || value.contains("key")) {
         return "zz.controls";
      }

      if (value.contains("sound") || value.contains("music")) {
         return "zz.sound";
      }

      if (value.contains("video") || value.contains("graphic") || value.contains("render")) {
         return "zz.video";
      }

      if (value.contains("chat")) {
         return "zz.chat";
      }

      if (value.contains("mouse") || value.contains("touch")) {
         return "zz.mouse";
      }

      if (value.contains("skin") || value.contains("model")) {
         return "zz.skin";
      }

      if (value.contains("debug") || value.contains("f3")) {
         return "zz.debug";
      }

      if (value.contains("accessib") || value.contains("narrat")) {
         return "zz.accessibility";
      }

      return "zz." + value;
   }

   private static int categoryIndex(String category) {
      if (category == null) {
         return CATEGORY_ORDER.size();
      }

      int index = CATEGORY_ORDER.indexOf(category);

      if (index >= 0) {
         return index;
      }

      // Unknown (vanilla) categories are grouped into stable buckets after the known ones.
      return CATEGORY_ORDER.size() + Math.abs(categoryBucket(category).hashCode() % 1000);
   }

   private int pageSize() {
      return Math.max(1, (this.height - 110) / ROW_HEIGHT);
   }

   private int pages() {
      return Math.max(1, (this.options.size() + this.pageSize() - 1) / this.pageSize());
   }

   /** Starts capturing the next key press for a StandardSettings key binding row. */
   private boolean beginKeyCapture(Object setting) {
      try {
         if (!this.keyCaptureResolved) {
            this.keyCaptureResolved = true;
            Class<?> main = Class.forName("me.contaria.standardsettings.StandardSettings");
            this.keyBindingConfig = main.getField("config").get(null);
            this.keyListener = (java.util.function.Predicate<com.mojang.blaze3d.platform.InputConstants.Key>)this
               .keyBindingConfig.getClass().getMethod("createInputListener").invoke(this.keyBindingConfig);
         }

         if (this.keyBindingConfig == null || this.keyListener == null) {
            return false;
         }

         this.keyBindingConfig.getClass()
            .getMethod("setFocusedKeyBinding", setting.getClass())
            .invoke(this.keyBindingConfig, setting);
         this.focusedKeyBinding = setting;
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
      if (this.focusedKeyBinding != null && this.keyListener != null) {
         com.mojang.blaze3d.platform.InputConstants.Key key =
            com.mojang.blaze3d.platform.InputConstants.getKey(event);

         if (this.keyListener.test(key)) {
            this.focusedKeyBinding = null;
            return true;
         }
      }

      return super.keyPressed(event);
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
      // Clicking a key binding row starts a capture; the next key press writes it back.
      int row = (int)((event.y() - 52) / ROW_HEIGHT);

      if (row >= 0 && row < this.shownOptions.size()) {
         SpeedrunOption<?> option = this.shownOptions.get(row);

         if (option.getClass().getSimpleName().contains("KeyBindingStandardSetting") && this.beginKeyCapture(option)) {
            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   @Override
   protected void init() {
      int size = this.pageSize();
      int pages = this.pages();
      this.page = Math.max(0, Math.min(this.page, pages - 1));
      int start = this.page * size;
      int x = this.width / 2 + 10;

      this.shownOptions.clear();

      for (int i = 0; i < size && start + i < this.options.size(); i++) {
         SpeedrunOption<?> option = this.options.get(start + i);
         AbstractWidget widget = option.createWidget();

         // Hover help: options describe themselves through "<id>.description" lang keys.
         Component description = option.getDescription();

         if (description != null) {
            widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(description));
         }

         widget.setX(x);
         widget.setY(52 + i * ROW_HEIGHT);
         widget.setWidth(WIDGET_WIDTH);
         this.addRenderableWidget(widget);
         this.shownOptions.add(option);

         if (Boolean.getBoolean("speedrunapi.logMissingNames")) {
            // Diagnostics: print every option that is actually rendered with its resolved label, so a
            // language file can be completed from the log instead of guessing key names.
            me.contaria.speedrunapi.SpeedrunAPI.LOGGER.info(
               "[speedrunapi] option {}:{} = '{}'",
               this.modID,
               option.getID(),
               option.getName() == null ? "?" : option.getName().getString()
            );
         }
      }

      int bottom = this.height - 27;

      if (pages > 1) {
         this.addRenderableWidget(
            Button.builder(Component.literal("<"), button -> {
               this.page--;
               this.rebuildWidgets();
            }).bounds(this.width / 2 - 195, bottom, 20, 20).build()
         ).active = this.page > 0;
         this.addRenderableWidget(
            Button.builder(Component.literal(">"), button -> {
               this.page++;
               this.rebuildWidgets();
            }).bounds(this.width / 2 - 170, bottom, 20, 20).build()
         ).active = this.page < pages - 1;
      }

      if (this.keybindingsOption != null) {
         AbstractWidget widget = this.keybindingsOption.createWidget();
         widget.setX(this.width / 2 - 140);
         widget.setY(bottom);
         widget.setWidth(200);
         this.addRenderableWidget(widget);
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), button -> this.onClose())
            .bounds(this.width / 2 + 70, bottom, 120, 20)
            .build()
      );
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      graphics.centeredText(this.font, this.title, this.width / 2, 14, -1);
      graphics.centeredText(
         this.font, Component.literal((this.page + 1) + " / " + this.pages()), this.width / 2, 28, -6250336
      );

      int labelRight = this.width / 2 - 10;
      String lastCategory = null;

      for (int i = 0; i < this.shownOptions.size(); i++) {
         SpeedrunOption<?> option = this.shownOptions.get(i);
         String category = option.getCategory();

         if (category != null && !category.equals(lastCategory)) {
            lastCategory = category;
            String key = "speedrunapi.config." + this.modID + ".category." + category;
            String header = net.minecraft.locale.Language.getInstance().getOrDefault(key);

            if (header.equals(key)) {
               // Categories may themselves be translation keys (e.g. StandardSettings uses
               // "options.mouse_settings" / "options.video" as categories).
               header = net.minecraft.locale.Language.getInstance().getOrDefault(category);
            }

            if (header.equals(category)) {
               header = net.minecraft.locale.Language.getInstance().getOrDefault("options." + category);
            }

            if (header.equals(category)) {
               header = pretty(category);
            }

            graphics.text(this.font, header, this.width / 2 - 150, 52 + i * ROW_HEIGHT - 10, -171);
         }

         String id = option.getID();
         String key = "speedrunapi.config." + this.modID + ".option." + id;
         String name = net.minecraft.locale.Language.getInstance().getOrDefault(key);

         if (name.equals(key)) {
            String byId = net.minecraft.locale.Language.getInstance().getOrDefault(id);

            if (!byId.equals(id)) {
               name = byId;
            } else {
               String optionsKey = "options." + id;
               String byOptions = net.minecraft.locale.Language.getInstance().getOrDefault(optionsKey);

               if (!byOptions.equals(optionsKey)) {
                  name = byOptions;
               } else if (id.startsWith("key_") || id.startsWith("key.")) {
                  // Ids come in several shapes ("key_key.playerlist", "key.key.debug.copyLocation"},
                  // because the port prefixes the mapping name again. Strip the "key"/"key_" prefixes
                  // until only the mapping name is left, then translate that.
                  String mapping = id;

                  for (int guard = 0; guard < 4; guard++) {
                     if (mapping.startsWith("key_")) {
                        mapping = mapping.substring("key_".length());
                     } else if (mapping.startsWith("key.")) {
                        mapping = mapping.substring("key.".length());
                     } else {
                        break;
                     }
                  }

                  String translated = net.minecraft.locale.Language.getInstance().getOrDefault(mapping);

                  if (translated.equals(mapping)) {
                     // Debug mappings are named "debug.<name>"; the trailing part is the readable one.
                     String trailing = mapping.substring(mapping.lastIndexOf('.') + 1);
                     String byTrailing = net.minecraft.locale.Language.getInstance()
                        .getOrDefault("key.debug." + trailing);
                     name = byTrailing.equals("key.debug." + trailing) ? pretty(trailing) : byTrailing;
                  } else {
                     name = translated;
                  }
               } else {
                  // Debug toggles carry ids like "debug.minecraft:looking_at_entity"; the vanilla list
                  // shows just "looking_at_entity", so drop the namespace before prettifying.
                  String bare = id;

                  if (bare.startsWith("debug.")) {
                     bare = bare.substring("debug.".length());
                  }

                  int colon = bare.indexOf(':');

                  if (colon >= 0) {
                     bare = bare.substring(colon + 1);
                  }

                  name = pretty(bare);
               }
            }
         }

         int y = 52 + i * ROW_HEIGHT + 6;
         if (Boolean.getBoolean("speedrunapi.logMissingNames") && drawnLogged.add(this.modID + ":" + option.getID())) {
            me.contaria.speedrunapi.SpeedrunAPI.LOGGER.info(
               "[speedrunapi] drawn {}:{} = '{}'", this.modID, option.getID(), name
            );
         }

         graphics.text(this.font, name, labelRight - this.font.width(name), y, -1);
      }
   }

   /** Turns an option/category id into a readable label when no translation is available. */
   private static String pretty(String id) {
      StringBuilder text = new StringBuilder();

      for (int i = 0; i < id.length(); i++) {
         char character = id.charAt(i);

         if (character == '_') {
            text.append(' ');
            continue;
         }

         if (i > 0 && Character.isUpperCase(character) && !Character.isUpperCase(id.charAt(i - 1))) {
            text.append(' ');
         }

         text.append(character);
      }

      String result = text.toString();
      return result.isEmpty() ? id : Character.toUpperCase(result.charAt(0)) + result.substring(1);
   }

   @Override
   public void onClose() {
      this.saveQuietly();
      this.minecraft.gui.setScreen(this.parent);
   }

   private void saveQuietly() {
      try {
         SpeedrunConfigAPI.getConfigContainer(this.modID).save();
      } catch (Exception e) {
         SpeedrunConfigAPI.handleSaveException(this.modID, e);
      }
   }
}
