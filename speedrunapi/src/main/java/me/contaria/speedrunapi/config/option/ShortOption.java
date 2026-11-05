package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;

public class ShortOption extends NumberOption<Short> {
   public ShortOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public Short get() {
      return (Short)this.rawGet();
   }

   @Override
   public void set(Short value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set(((Number)value).shortValue());
   }

   @Override
   public Short fromDouble(double value) {
      return (short)Math.round(value);
   }

   @Override
   public double toDouble() {
      return this.get();
   }
}
