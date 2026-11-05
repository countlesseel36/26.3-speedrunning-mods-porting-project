package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;

public class IntegerOption extends NumberOption<Integer> {
   public IntegerOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public Integer get() {
      return (Integer)this.rawGet();
   }

   @Override
   public void set(Integer value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set(((Number)value).intValue());
   }

   @Override
   public Integer fromDouble(double value) {
      return (int)Math.round(value);
   }

   @Override
   public double toDouble() {
      return this.get();
   }
}
