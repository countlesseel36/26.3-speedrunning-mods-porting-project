package me.contaria.seedqueue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Key rebinding screen for the wall, with primary keys, combination (secondary) keys and blocking
 * keys, matching the 1.16.1/1.21.4 layout: name on the left, the chord button in the middle and
 * "+ n" / "- n" counters for the secondary and blocking key lists.
 */
public final class WallKeybindingsScreen extends Screen {
   private final Screen parent;
   private WallKeybindings.Action editing;
   /** 0 = primary, 1 = secondary, 2 = blocking. */
   private int mode;
   private final Set<Integer> modifiers = new LinkedHashSet<>();
   private int page;
   private int perPage;

   public WallKeybindingsScreen(Screen parent) {
      super(Component.translatable("seedqueue.menu.keys.configure"));
      this.parent = parent;
   }

   @Override
   public boolean isPauseScreen() {
      // Pause like the vanilla options screens so rebinding does not run the world behind the menu.
      return true;
   }

   @Override
   protected void init() {
      super.init();
      this.perPage = Math.max(1, (this.height - 80) / 27);
      WallKeybindings.Action[] actions = WallKeybindings.Action.values();
      int start = this.page * this.perPage;
      int end = Math.min(actions.length, start + this.perPage);
      int center = this.width / 2;

      for (int i = start; i < end; i++) {
         WallKeybindings.Action action = actions[i];
         int y = 36 + (i - start) * 27;
         Button chord = Button.builder(Component.literal(this.chord(action)), button -> this.edit(action, 0))
            .bounds(center - 30, y, 80, 20)
            .build();
         chord.setTooltip(Tooltip.create(Component.translatable("seedqueue.menu.keys.primary")));
         this.addRenderableWidget(chord);

         // Combination (secondary) and blocking keys were removed: the SDL input path could not report
         // modifier state, so those columns only ever produced bindings that did not fire. The screen
         // now edits a single key per action.
      }

      this.addRenderableWidget(
         Button.builder(Component.literal("<"), button -> {
            this.page--;
            this.rebuildWidgets();
         }).bounds(center - 100, this.height - 30, 40, 20).build()
      ).active = this.page > 0;
      this.addRenderableWidget(
         Button.builder(Component.literal(">"), button -> {
            this.page++;
            this.rebuildWidgets();
         }).bounds(center - 55, this.height - 30, 40, 20).build()
      ).active = (this.page + 1) * this.perPage < actions.length;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), button -> this.onClose())
            .bounds(center, this.height - 30, 100, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("controls.reset"), button -> {
            WallKeybindings.resetDefaults();
            QueueConfig.save();
            this.rebuildWidgets();
         }).bounds(center + 105, this.height - 30, 110, 20).build()
      );
   }

   private Tooltip keysTooltip(String kind, List<Integer> keys) {
      StringBuilder text = new StringBuilder(Component.translatable("seedqueue.menu.keys." + kind + ".tooltip").getString());

      if (!keys.isEmpty()) {
         text.append("\n");

         for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
               text.append(" + ");
            }

            text.append(WallKeybindings.display(keys.get(i)));
         }
      }

      return Tooltip.create(Component.literal(text.toString()));
   }

   private String chord(WallKeybindings.Action action) {
      WallKeybindings.Binding binding = WallKeybindings.binding(action);
      StringBuilder text = new StringBuilder();

      for (int key : binding.secondary) {
         text.append(WallKeybindings.display(key)).append(" + ");
      }

      return text.append(WallKeybindings.display(binding.primary)).toString();
   }

   private void edit(WallKeybindings.Action action, int mode) {
      this.editing = action;
      this.mode = mode;
      this.modifiers.clear();
      this.rebuildWidgets();
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (this.editing == null) {
         return super.keyPressed(event);
      }

      if (event.isEscape()) {
         this.editing = null;
         this.modifiers.clear();
         this.rebuildWidgets();
         return true;
      }

      int key = event.key();

      if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
         if (this.mode == 0) {
            WallKeybindings.setPrimary(this.editing, WallKeybindings.UNKNOWN);
            WallKeybindings.setSecondary(this.editing, List.of());
         } else if (this.mode == 1) {
            WallKeybindings.setSecondary(this.editing, List.of());
         } else {
            WallKeybindings.setBlocking(this.editing, List.of());
         }

         this.finishEdit();
         return true;
      }

      // Combination and blocking keys were removed; every action is a single key now.
      WallKeybindings.setPrimary(this.editing, key);
      WallKeybindings.setSecondary(this.editing, List.of());
      WallKeybindings.setBlocking(this.editing, List.of());

      this.finishEdit();
      return true;
   }

   private void finishEdit() {
      this.editing = null;
      this.modifiers.clear();
      QueueConfig.save();
      this.rebuildWidgets();
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
      if (this.editing != null) {
         // 26.3 hands over SDL button numbers (1 = left, 2 = middle, 3 = right), while
         // InputConstants and InputConstants.isKeyDown use GLFW's 0-based indices.
         int mouseIndex = event.button() - 1;

         if (mouseIndex < 0 || mouseIndex > 7) {
            return true;
         }

         int key = com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(mouseIndex).getValue();

         if (this.mode == 0) {
            WallKeybindings.setPrimary(this.editing, key);
            WallKeybindings.setSecondary(this.editing, List.of());
         } else {
            List<Integer> keys = new ArrayList<>(this.mode == 1
               ? WallKeybindings.binding(this.editing).secondary
               : WallKeybindings.binding(this.editing).blocking);

            if (!keys.remove(Integer.valueOf(key))) {
               keys.add(key);
            }

            if (this.mode == 1) {
               WallKeybindings.setSecondary(this.editing, keys);
            } else {
               WallKeybindings.setBlocking(this.editing, keys);
            }
         }

         this.finishEdit();
         return true;
      }

      return super.mouseClicked(event, doubleClick);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      Component title = this.editing == null
         ? this.title
         : Component.translatable("seedqueue.menu.keys");
      graphics.centeredText(this.font, title, this.width / 2, 12, 0xFFFFFFFF);

      WallKeybindings.Action[] actions = WallKeybindings.Action.values();
      int start = this.page * this.perPage;
      int end = Math.min(actions.length, start + this.perPage);

      for (int i = start; i < end; i++) {
         String key = WallKeybindings.translationKey(actions[i]);
         String name = net.minecraft.locale.Language.getInstance().getOrDefault(key);

         if (name.equals(key)) {
            name = actions[i].name().replace('_', ' ');
         }

         graphics.text(this.font, name, Math.max(5, this.width / 2 - 165), 42 + (i - start) * 27, 0xFFFFFFFF);
      }
   }

   @Override
   public void onClose() {
      QueueConfig.save();
      this.minecraft.gui.setScreen(this.parent);
   }
}
