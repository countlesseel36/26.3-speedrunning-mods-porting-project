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
   /** Pixel offset of the scrollable option list. */
   private int scroll;
   private boolean draggingScrollbar;
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
            Comparator.<Map.Entry<String, SpeedrunOption<?>>>comparingInt(entry -> groupIndex(entry.getValue()))
               // Options that belong to the same vanilla screen (video / accessibility / chat ...) must
               // stay together: sorting by the bucket name before the id keeps the groups contiguous
               // instead of scattering them by hash order.
               .thenComparing(entry -> categoryBucket(entry.getValue().getCategory()))
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

   /** Readable headings for the grouped vanilla buckets. */
   private static final java.util.Map<String, String> BUCKET_TITLES = java.util.Map.ofEntries(
      java.util.Map.entry("zz.controls", "按键控制"),
      java.util.Map.entry("zz.sound", "声音设置"),
      java.util.Map.entry("zz.video", "视频设置"),
      java.util.Map.entry("zz.chat", "聊天设置"),
      java.util.Map.entry("zz.mouse", "鼠标设置"),
      java.util.Map.entry("zz.skin", "皮肤设置"),
      java.util.Map.entry("zz.accessibility", "辅助功能设置"),
      java.util.Map.entry("zz.debug", "调试选项"),
      java.util.Map.entry("queue", "队列选项"),
      java.util.Map.entry("wall", "墙界面选项"),
      java.util.Map.entry("performance", "性能选项"),
      java.util.Map.entry("advanced", "进阶选项"),
      java.util.Map.entry("threading", "线程选项"),
      java.util.Map.entry("misc", "杂项"),
      java.util.Map.entry("debug", "调试选项"),
      java.util.Map.entry("f3", "F3 设置"),
      java.util.Map.entry("more", "更多设置"),
      java.util.Map.entry("onWorldJoin", "进入世界时设置")
   );

   /**
    * Group title for an option.
    *
    * <p>StandardSettings leaves the category {@code null} for most rows (it inherits the vanilla
    * option's group instead), which is why headings never appeared: the old code only drew a heading
    * when the category changed. The group is therefore derived from the option id when the category is
    * missing.</p>
    */
   private static String groupTitle(SpeedrunOption<?> option) {
      String category = option.getCategory();

      if (category != null && !category.isBlank()) {
         return bucketTitle(category);
      }

      String id = option.getID().toLowerCase(java.util.Locale.ROOT);

      if (id.startsWith("key_") || id.startsWith("key.")) {
         return "按键控制";
      }

      if (id.contains("sound") || id.contains("music") || id.contains("volume")) {
         return "声音设置";
      }

      if (id.contains("chat") || id.contains("narrat") || id.contains("notification")) {
         return "聊天设置";
      }

      if (id.contains("mouse") || id.contains("invert") || id.contains("sensitivity")) {
         return "鼠标设置";
      }

      if (id.contains("modelpart") || id.contains("cape") || id.contains("skin")) {
         return "皮肤设置";
      }

      if (id.contains("debug") || id.contains("hitbox") || id.contains("chunk")) {
         return "调试选项";
      }

      if (id.contains("fov") || id.contains("render") || id.contains("graphic") || id.contains("gui")
         || id.contains("gamma") || id.contains("cloud") || id.contains("particles")
         || id.contains("mipmap") || id.contains("vsync") || id.contains("fullscreen")
         || id.contains("anisotropy") || id.contains("fps") || id.contains("simulation")
         || id.contains("ao") || id.contains("biomeblend")) {
         return "视频设置";
      }

      return "杂项";
   }

   private static final java.util.List<String> GROUP_ORDER = java.util.List.of(
      "队列选项", "墙界面选项", "性能选项", "进阶选项", "线程选项", "实验选项",
      "按键控制", "视频设置", "聊天设置", "声音设置", "鼠标设置", "皮肤设置",
      "辅助功能设置", "F3 设置", "进入世界时设置", "更多设置", "杂项", "调试选项"
   );

   private static int groupIndex(SpeedrunOption<?> option) {
      int index = GROUP_ORDER.indexOf(groupTitle(option));
      return index < 0 ? GROUP_ORDER.size() : index;
   }

   private static String bucketTitle(String category) {
      if (category == null) {
         return "选项";
      }

      String bucket = categoryBucket(category);
      String title = BUCKET_TITLES.get(bucket);

      if (title != null) {
         return title;
      }

      // Fall back to the known-category table, then to a readable form of the raw name.
      title = BUCKET_TITLES.get(category);

      if (title != null) {
         return title;
      }

      return pretty(bucket.startsWith("zz.") ? bucket.substring(3) : bucket);
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

   /** One scrollable list instead of pages, like the vanilla options screens. */
   private int maxScroll(int rows) {
      // One extra row of slack: without it the very last option sat just below the clipped area.
      return Math.max(0, rows * ROW_HEIGHT - (this.height - 96) + ROW_HEIGHT);
   }

   private int rowY(int index) {
      return 48 + index * ROW_HEIGHT - this.scroll;
   }

   /** A row counts as visible only when the whole widget fits between the two shadow bands. */
   private boolean rowVisible(int index) {
      int y = this.rowY(index);
      return y >= 40 && y + 20 <= this.height - 42;
   }

   /**
    * Writes the freshly captured value straight into the game's {@code KeyMapping}.
    *
    * <p>StandardSettings only stores the value and pushes it to vanilla when a world is joined, which
    * looked like "rebinding does nothing" while sitting in the menu. Calling {@code setVanilla} here
    * makes the new key effective immediately.</p>
    */
   private static Class<?> keyClass;
   private static java.lang.reflect.Method bindingGet;
   private static java.lang.reflect.Method bindingSetVanilla;
   private static java.lang.reflect.Method bindingSet;
   private static boolean capabilityProbed;
   private static boolean capabilityAvailable;

   /**
    * Resolves the StandardSettings binding API once.
    *
    * <p>Rebinding used to do three {@code getMethod}/{@code Class.forName} lookups per key press; they
    * are cached now and the capability is probed a single time, so a missing StandardSettings version
    * logs one warning instead of one per click.</p>
    */
   private static boolean probeCapability(Object binding) {
      if (!capabilityProbed) {
         capabilityProbed = true;

         try {
            keyClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
            bindingGet = binding.getClass().getMethod("get");
            bindingSetVanilla = binding.getClass().getMethod("setVanilla", keyClass);
            bindingSet = binding.getClass().getMethod("set", keyClass);
            capabilityAvailable = true;
         } catch (Throwable t) {
            capabilityAvailable = false;
            me.contaria.speedrunapi.SpeedrunAPI.LOGGER.warn(
               "[speedrunapi] key rebinding is unavailable; StandardSettings API not found", t
            );
         }
      }

      return capabilityAvailable;
   }

   private void pushToVanilla() {
      if (!probeCapability(this.focusedKeyBinding)) {
         return;
      }

      try {
         Object value = bindingGet.invoke(this.focusedKeyBinding);
         bindingSetVanilla.invoke(this.focusedKeyBinding, value);
      } catch (Throwable t) {
         me.contaria.speedrunapi.SpeedrunAPI.LOGGER.warn("[speedrunapi] could not apply the rebound key", t);
      }
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
         me.contaria.speedrunapi.SpeedrunAPI.LOGGER.warn(
            "[speedrunapi] key capture is unavailable (StandardSettings not installed?)", t
         );
         return false;
      }
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
      if (this.focusedKeyBinding != null && this.keyListener != null) {
         // Escape means "no key": unbind instead of binding Escape itself, and leave the capture.
         if (event.isEscape()) {
            try {
               Object unknown = Class.forName("com.mojang.blaze3d.platform.InputConstants")
                  .getField("UNKNOWN")
                  .get(null);
               Class<?> keyType = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
               bindingSet.invoke(this.focusedKeyBinding, unknown);
            } catch (Throwable t) {
               me.contaria.speedrunapi.SpeedrunAPI.LOGGER.warn("[speedrunapi] could not unbind the key binding", t);
            }

            this.pushToVanilla();
            this.focusedKeyBinding = null;
            return true;
         }

         com.mojang.blaze3d.platform.InputConstants.Key key =
            com.mojang.blaze3d.platform.InputConstants.getKey(event);

         if (this.keyListener.test(key)) {
            this.pushToVanilla();
            this.focusedKeyBinding = null;
            return true;
         }
      }

      return super.keyPressed(event);
   }

   private boolean overScrollbar(double mouseX, double mouseY) {
      return this.maxScroll(this.shownOptions.size()) > 0
         && mouseX >= this.width - 14
         && mouseY >= 34
         && mouseY <= this.height - 40;
   }

   private void scrollToThumb(double mouseY) {
      int max = this.maxScroll(this.shownOptions.size());
      int trackTop = 34;
      int trackBottom = this.height - 40;
      int trackHeight = trackBottom - trackTop;
      int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
      int thumbTop = (int)(mouseY - thumbHeight / 2.0);
      int travel = Math.max(1, trackHeight - thumbHeight);
      int value = Math.max(0, Math.min(max, (thumbTop - trackTop) * max / travel));
      this.scroll = value / ROW_HEIGHT * ROW_HEIGHT;
      this.rebuildWidgets();
   }

   @Override
   public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
      if (this.draggingScrollbar) {
         this.scrollToThumb(event.y());
         return true;
      }

      return super.mouseDragged(event, dragX, dragY);
   }

   @Override
   public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
      this.draggingScrollbar = false;
      return super.mouseReleased(event);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      int max = this.maxScroll(this.shownOptions.size());

      if (max <= 0) {
         return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
      }

      int next = Math.max(0, Math.min(max, this.scroll - (int)(vertical * ROW_HEIGHT)));
      next = next / ROW_HEIGHT * ROW_HEIGHT;   // whole rows only: no half visible entries

      if (next != this.scroll) {
         this.scroll = next;
         this.rebuildWidgets();
      }

      return true;
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
      // Clicking a key binding row starts a capture; the next key press writes it back.
      if (this.overScrollbar(event.x(), event.y())) {
         this.draggingScrollbar = true;
         this.scrollToThumb(event.y());
         return true;
      }

      if (this.focusedKeyBinding != null && this.keyListener != null) {
         // Mouse buttons are bindable too (1..5 = left, middle, right, back, forward).
         // event.button() is already 0-based in 26.3 (vanilla compares it with 0 for left click).
         int mouseIndex = event.button();

         if (mouseIndex >= 0 && mouseIndex <= 7) {
            com.mojang.blaze3d.platform.InputConstants.Key mouseKey =
               com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(mouseIndex);

            if (this.keyListener.test(mouseKey)) {
               this.pushToVanilla();
               this.focusedKeyBinding = null;
               return true;
            }
         }
      }

      int row = (int)((event.y() + this.scroll - 48) / ROW_HEIGHT);
      // Only the option column starts a capture; without the horizontal check a click on the bottom
      // buttons (done / page arrows) could be swallowed when it lined up with a key binding row.
      boolean insideOptions = event.x() >= this.width / 2 - 160 && event.x() <= this.width / 2 + 160
         && event.y() >= 40 && event.y() <= this.height - 42;

      if (insideOptions && row >= 0 && row < this.shownOptions.size()) {
         SpeedrunOption<?> option = this.shownOptions.get(row);

         if (option.getClass().getSimpleName().contains("KeyBindingStandardSetting") && this.beginKeyCapture(option)) {
            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   @Override
   protected void init() {
      int size = this.options.size();
      int x = this.width / 2 + 10;

      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll(size)));
      this.shownOptions.clear();

      for (int i = 0; i < size; i++) {
         SpeedrunOption<?> option = this.options.get(i);
         AbstractWidget widget = option.createWidget();

         // Hover help: options describe themselves through "<id>.description" lang keys.
         Component description = option.getDescription();

         if (description != null) {
            widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(description));
         }

         widget.setX(x);
         widget.setY(this.rowY(i));
         widget.setWidth(WIDGET_WIDTH);
         // Rows that scrolled out of view must not stay clickable underneath the bottom bar.
         widget.visible = this.rowVisible(i);
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

      int bottom = this.height - 34;

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
      // Shadowed header: the band is drawn first, the title on top of it.
      graphics.fill(0, 0, this.width, 34, 0x90000000);
      graphics.centeredText(this.font, this.title, this.width / 2, 14, -1);


      // Scroll bar: track plus a proportional thumb on the right edge.
      int maxScroll = this.maxScroll(this.shownOptions.size());

      if (maxScroll > 0) {
         int trackTop = 34;
         int trackBottom = this.height - 40;
         int trackHeight = trackBottom - trackTop;
         int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + maxScroll));
         int thumbTop = trackTop + (trackHeight - thumbHeight) * this.scroll / maxScroll;
         int barX = this.width - 10;
         graphics.fill(barX, trackTop, barX + 6, trackBottom, 0x40FFFFFF);
         graphics.fill(barX, thumbTop, barX + 6, thumbTop + thumbHeight, 0xFFA0A0A0);
      }

      // Clip the option labels to the list area so nothing bleeds into the title or the button band.
      graphics.enableScissor(0, 36, this.width, this.height - 40);
      int labelRight = this.width / 2 - 10;

      int firstVisible = Math.max(0, this.scroll / ROW_HEIGHT);
      String lastCategory = null;

      // Always show the group of the first visible row in the left column, at a fixed height inside
      // the clipped list area. This is the same draw path the option names use, so it is guaranteed
      // to be visible (previous attempts placed it above the first row, where the title band covered it).
      if (firstVisible < this.shownOptions.size()) {
         graphics.text(this.font, groupTitle(this.shownOptions.get(firstVisible)), this.width / 2 - 170, 40, 0xFFFFFF55);
      }

      for (int i = 0; i < this.shownOptions.size(); i++) {
         SpeedrunOption<?> option = this.shownOptions.get(i);
         String category = option.getCategory();

         String group = groupTitle(option);

         if (!group.equals(lastCategory)) {
            lastCategory = group;


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

         // Same rule as the widget: a row is either fully inside the list area or not drawn at all.
         if (!this.rowVisible(i)) {
            continue;
         }

         int y = this.rowY(i) + 6;
         if (Boolean.getBoolean("speedrunapi.logMissingNames") && drawnLogged.add(this.modID + ":" + option.getID())) {
            me.contaria.speedrunapi.SpeedrunAPI.LOGGER.info(
               "[speedrunapi] drawn {}:{} = '{}'", this.modID, option.getID(), name
            );
         }

         if (i == 0 || !groupTitle(this.shownOptions.get(i - 1)).equals(group)) {
            // Drawn on the same line as the first option of the group, in the left column, so it is
            // always inside the visible list area.
            graphics.text(this.font, group, this.width / 2 - 170, this.rowY(i) + 6, 0xFFFFFF55);
         }

         graphics.text(this.font, name, labelRight - this.font.width(name), y, -1);
      }

      graphics.disableScissor();
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
