package me.contaria.standardsettings.gui;

import java.util.ArrayList;
import java.util.List;
import me.contaria.standardsettings.options.StandardSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;

public class StandardOptionWidget extends AbstractWidget implements ContainerEventHandler {
   private final StandardSetting<?> setting;
   private final AbstractWidget mainWidget;
   private final AbstractWidget toggle;
   private GuiEventListener focused;
   private boolean isDragging;

   public StandardOptionWidget(StandardSetting<?> setting, AbstractWidget mainWidget) {
      super(mainWidget.getX(), mainWidget.getY(), mainWidget.getWidth() + 30, mainWidget.getHeight(), mainWidget.getMessage());
      this.setting = setting;
      this.mainWidget = mainWidget;
      this.toggle = Button.builder(CommonComponents.optionStatus(setting.isEnabled()), button -> {
         boolean enabled = this.setting.toggleEnabled();
         button.setMessage(CommonComponents.optionStatus(enabled));
         this.setEnabled(enabled);
      }).bounds(mainWidget.getWidth() + 5, 0, 25, 20).build();
      this.setEnabled(setting.isEnabled());
   }

   private void setEnabled(boolean enabled) {
      this.mainWidget.active = enabled;
      if (this.mainWidget instanceof EditBox) {
         ((EditBox)this.mainWidget).setEditable(enabled);
      }

      this.mainWidget.setMessage(this.setting.getText());
   }

   protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.mainWidget.setPosition(this.getX(), this.getY());
      this.mainWidget.extractRenderState(graphics, mouseX, mouseY, a);
      this.toggle.setPosition(this.getX() + this.mainWidget.getWidth() + 5, this.getY());
      this.toggle.extractRenderState(graphics, mouseX, mouseY, a);
   }

   public List<? extends GuiEventListener> children() {
      List<GuiEventListener> children = new ArrayList<>();
      children.add(this.mainWidget);
      children.add(this.toggle);
      return children;
   }

   public final boolean isDragging() {
      return this.isDragging;
   }

   public final void setDragging(boolean dragging) {
      this.isDragging = dragging;
   }

   public GuiEventListener getFocused() {
      return this.focused;
   }

   public void setFocused(GuiEventListener focused) {
      if (this.focused != null) {
         this.focused.setFocused(false);
      }

      if (focused != null) {
         focused.setFocused(true);
      }

      this.focused = focused;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      // Dispatch to the option widget / enable toggle; AbstractWidget#mouseClicked would swallow
      // the click without doing anything, which made every StandardSettings option read-only.
      return ContainerEventHandler.super.mouseClicked(event, doubleClick);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return ContainerEventHandler.super.mouseReleased(event);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return ContainerEventHandler.super.mouseDragged(event, dx, dy);
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
   }
}
