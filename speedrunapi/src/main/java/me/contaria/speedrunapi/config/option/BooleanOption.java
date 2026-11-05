package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;

public class BooleanOption extends FieldBasedOption<Boolean> {
   public BooleanOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public Boolean get() {
      return (Boolean)this.rawGet();
   }

   @Override
   public void set(Boolean value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set((Boolean)value);
   }

   @Override
   public AbstractWidget createWidget() {
      Button button = Button.builder(this.getText(), b -> {
         this.set(!this.get());
         b.setMessage(this.getText());
      }).bounds(0, 0, 150, 20).build();
      button.active = this.hasWidget();
      return button;
   }

   @Override
   public net.minecraft.network.chat.Component getDefaultText() {
      return this.get() ? net.minecraft.network.chat.CommonComponents.OPTION_ON : net.minecraft.network.chat.CommonComponents.OPTION_OFF;
   }
}
