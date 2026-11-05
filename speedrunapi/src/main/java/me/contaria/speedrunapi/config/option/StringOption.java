package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

public class StringOption extends FieldBasedOption<String> {
   public StringOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public String get() {
      return (String)this.rawGet();
   }

   @Override
   public void set(String value) {
      this.rawSet(value == null ? "" : value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set(String.valueOf(value));
   }

   @Override
   public AbstractWidget createWidget() {
      EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 150, 20, this.getName());
      box.setValue(this.get());
      box.setResponder(this::set);
      return box;
   }
}
