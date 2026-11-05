package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;

public class FloatOption extends NumberOption<Float> {
   public FloatOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public Float get() {
      return (Float)this.rawGet();
   }

   @Override
   public void set(Float value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set(((Number)value).floatValue());
   }

   @Override
   public Float fromDouble(double value) {
      return (float)value;
   }

   @Override
   public double toDouble() {
      return this.get();
   }
}
