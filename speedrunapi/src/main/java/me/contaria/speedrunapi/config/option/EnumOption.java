package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import me.contaria.speedrunapi.config.api.option.EnumTextProvider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;

public class EnumOption extends FieldBasedOption<Enum<?>> {
   private final Class<?> enumClass;
   private final EnumTextProvider textProvider;

   public EnumOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
      this.enumClass = option.getType();
      this.textProvider = null;
   }

   @Override
   public Enum<?> get() {
      return (Enum<?>)this.rawGet();
   }

   @Override
   public void set(Enum<?> value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      if (value instanceof String string) {
         this.rawSet(Enum.valueOf((Class)this.enumClass, string));
      } else {
         this.rawSet(value);
      }
   }

   @Override
   public AbstractWidget createWidget() {
      return Button.builder(this.getText(), button -> {
         Enum<?>[] values = (Enum<?>[])this.enumClass.getEnumConstants();
         int next = (this.get().ordinal() + 1) % values.length;
         this.set(values[next]);
         button.setMessage(this.getText());
      }).bounds(0, 0, 150, 20).build();
   }
}
