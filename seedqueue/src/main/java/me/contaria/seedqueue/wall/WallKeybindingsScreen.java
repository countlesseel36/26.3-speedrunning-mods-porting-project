package me.contaria.seedqueue.wall;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.config.*;

public final class WallKeybindingsScreen extends Screen {
   private final Screen parent;
   private WallKeybindings.Action editing;
   private int mode;
   private final Set<Integer> modifiers = new LinkedHashSet<>();
   private int scroll;

   public WallKeybindingsScreen(Screen screen) {
      super(Component.translatable("seedqueue.menu.keys.configure"));
      this.parent = screen;
   }

   public boolean isPauseScreen() {
      return true;
   }

   protected void init() {
      super.init();
      WallKeybindings.Action[] actions = WallKeybindings.Action.values();
      int count = actions.length;
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
      int count2 = this.width / 2;

      for (int i = 0; i < count; i++) {
         WallKeybindings.Action action = actions[i];
         int count3 = 36 + i * 27 - this.scroll;
         Button button = Button.builder(Component.literal(this.chord(action)), arg -> this.edit(action, 0)).bounds(count2 - 30, count3, 80, 20).build();
         button.visible = count3 >= 30 && count3 + 20 <= this.height - 44;
         button.setTooltip(Tooltip.create(Component.translatable("seedqueue.menu.keys.primary")));
         this.addRenderableWidget(button);
      }

      this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), arg -> this.onClose()).bounds(count2, this.height - 30, 100, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("controls.reset"), arg -> {
         WallKeybindings.resetDefaults();
         QueueConfig.save();
         this.rebuildWidgets();
      }).bounds(count2 + 105, this.height - 30, 110, 20).build());
   }

   private int maxScroll() {
      int count = this.height - 44 - 36;
      int count2 = WallKeybindings.Action.values().length * 27;
      return Math.max(0, count2 - count);
   }

   public boolean mouseScrolled(double ratio, double ratio2, double ratio3, double ratio4) {
      int count = this.maxScroll();
      int count2 = Math.max(0, Math.min(count, this.scroll - (int)(ratio4 * 27.0)));
      count2 = count2 >= count ? count : count2 / 27 * 27;
      if (count2 != this.scroll) {
         this.scroll = count2;
         this.rebuildWidgets();
      }

      return true;
   }

   private String chord(WallKeybindings.Action wallKeybindingsAction) {
      WallKeybindings.Binding wallKeybindingsBinding = WallKeybindings.binding(wallKeybindingsAction);
      StringBuilder builder = new StringBuilder();

      for (int i : wallKeybindingsBinding.secondary) {
         builder.append(WallKeybindings.display(i)).append(" + ");
      }

      return builder.append(WallKeybindings.display(wallKeybindingsBinding.primary)).toString();
   }

   private void edit(WallKeybindings.Action wallKeybindingsAction, int count) {
      this.editing = wallKeybindingsAction;
      this.mode = count;
      this.modifiers.clear();
      this.rebuildWidgets();
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      if (this.editing == null) {
         return super.keyPressed(keyEvent);
      } else if (keyEvent.isEscape()) {
         if (this.editing == WallKeybindings.Action.QUIT) {
            WallKeybindings.setPrimary(this.editing, 41);
            WallKeybindings.setSecondary(this.editing, List.of());
            WallKeybindings.setBlocking(this.editing, List.of());
            this.finishEdit();
            return true;
         } else {
            WallKeybindings.setPrimary(this.editing, -1);
            WallKeybindings.setSecondary(this.editing, List.of());
            WallKeybindings.setBlocking(this.editing, List.of());
            this.finishEdit();
            return true;
         }
      } else {
         int count = WallKeybindings.keyOf(keyEvent);
         if (count == 42) {
            if (this.mode == 0) {
               WallKeybindings.setPrimary(this.editing, -1);
               WallKeybindings.setSecondary(this.editing, List.of());
            } else if (this.mode == 1) {
               WallKeybindings.setSecondary(this.editing, List.of());
            } else {
               WallKeybindings.setBlocking(this.editing, List.of());
            }

            this.finishEdit();
            return true;
         } else {
            WallKeybindings.setPrimary(this.editing, count);
            WallKeybindings.setSecondary(this.editing, List.of());
            WallKeybindings.setBlocking(this.editing, List.of());
            this.finishEdit();
            return true;
         }
      }
   }

   private void finishEdit() {
      this.editing = null;
      this.modifiers.clear();
      QueueConfig.save();
      this.rebuildWidgets();
   }

   public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean flag) {
      return this.editing != null ? true : super.mouseClicked(mouseButtonEvent, flag);
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int count, int count2, float ratio) {
      super.extractRenderState(guiGraphicsExtractor, count, count2, ratio);
      Object obj = this.editing == null ? this.title : Component.translatable("seedqueue.menu.keys");
      guiGraphicsExtractor.centeredText(this.font, (Component)obj, this.width / 2, 12, -1);
      WallKeybindings.Action[] wallKeybindingsAction = WallKeybindings.Action.values();

      for (int i = 0; i < wallKeybindingsAction.length; i++) {
         String text = WallKeybindings.translationKey(wallKeybindingsAction[i]);
         String text2 = Language.getInstance().getOrDefault(text);
         if (text2.equals(text)) {
            text2 = wallKeybindingsAction[i].name().replace('_', ' ');
         }

         int count3 = 42 + i * 27 - this.scroll;
         if (count3 - 6 >= 30 && count3 - 6 + 20 <= this.height - 44) {
            guiGraphicsExtractor.text(this.font, text2, Math.max(5, this.width / 2 - 165), count3, -1);
         }
      }
   }

   public void onClose() {
      QueueConfig.save();
      this.minecraft.gui.setScreen(this.parent);
   }
}
