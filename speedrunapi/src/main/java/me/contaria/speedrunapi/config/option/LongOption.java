package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;

public class LongOption extends NumberOption<Long> {
   public LongOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public Long get() {
      return (Long)this.rawGet();
   }

   @Override
   public void set(Long value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set(((Number)value).longValue());
   }

   @Override
   public Long fromDouble(double value) {
      return Math.round(value);
   }

   @Override
   public double toDouble() {
      return this.get();
   }
}
