package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;

public class DoubleOption extends NumberOption<Double> {
   public DoubleOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   @Override
   public Double get() {
      return (Double)this.rawGet();
   }

   @Override
   public void set(Double value) {
      this.rawSet(value);
   }

   @Override
   public void setUnsafely(Object value) {
      this.set(((Number)value).doubleValue());
   }

   @Override
   public Double fromDouble(double value) {
      return value;
   }

   @Override
   public double toDouble() {
      return this.get();
   }
}
