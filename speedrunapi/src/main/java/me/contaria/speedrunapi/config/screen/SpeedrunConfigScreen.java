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
               .thenComparing(Map.Entry::getKey)
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

   private static int categoryIndex(String category) {
      if (category == null) {
         return CATEGORY_ORDER.size();
      }

      int index = CATEGORY_ORDER.indexOf(category);
      return index < 0 ? CATEGORY_ORDER.size() : index;
   }

   private int pageSize() {
      return Math.max(1, (this.height - 110) / ROW_HEIGHT);
   }

   private int pages() {
      return Math.max(1, (this.options.size() + this.pageSize() - 1) / this.pageSize());
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
         widget.setX(x);
         widget.setY(52 + i * ROW_HEIGHT);
         widget.setWidth(WIDGET_WIDTH);
         this.addRenderableWidget(widget);
         this.shownOptions.add(option);
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
               } else if (id.startsWith("key_key.")) {
                  // StandardSettings registers key bindings as "key_" + KeyMapping#getName(), e.g.
                  // "key_key.playerList"; translate the underlying key instead of showing it raw.
                  String mapping = id.substring("key_key.".length());
                  String translated = net.minecraft.locale.Language.getInstance().getOrDefault(mapping);
                  name = translated.equals(mapping) ? pretty(id) : translated;
               } else {
                  name = pretty(id);
               }
            }
         }

         int y = 52 + i * ROW_HEIGHT + 6;
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
