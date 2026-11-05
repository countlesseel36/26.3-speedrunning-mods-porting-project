package me.contaria.speedrunapi.config.option;

import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import me.contaria.speedrunapi.config.api.annotations.Config;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public abstract class NumberOption<T extends Number> extends FieldBasedOption<T> {
   protected NumberOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      super(config, configStorage, option, idPrefix);
   }

   public abstract T fromDouble(double value);

   public abstract double toDouble();

   protected double min() {
      Config.Numbers.Whole.Bounds whole = this.option.getAnnotation(Config.Numbers.Whole.Bounds.class);
      if (whole != null) {
         return whole.min();
      }

      Config.Numbers.Fractional.Bounds fractional = this.option.getAnnotation(Config.Numbers.Fractional.Bounds.class);
      return fractional != null ? fractional.min() : 0.0;
   }

   protected double max() {
      Config.Numbers.Whole.Bounds whole = this.option.getAnnotation(Config.Numbers.Whole.Bounds.class);
      if (whole != null) {
         return whole.max();
      }

      Config.Numbers.Fractional.Bounds fractional = this.option.getAnnotation(Config.Numbers.Fractional.Bounds.class);
      return fractional != null ? fractional.max() : 1.0;
   }

   protected boolean hasBounds() {
      return this.option.isAnnotationPresent(Config.Numbers.Whole.Bounds.class)
         || this.option.isAnnotationPresent(Config.Numbers.Fractional.Bounds.class);
   }

   @Override
   public AbstractWidget createWidget() {
      if (this.hasBounds() && this.max() > this.min()) {
         double min = this.min();
         double max = this.max();
         double initial = (Math.max(min, Math.min(max, this.toDouble())) - min) / (max - min);

         return new AbstractSliderButton(0, 0, 150, 20, this.getText(), initial) {
            {
               this.updateMessage();
            }

            @Override
            protected void updateMessage() {
               this.setMessage(NumberOption.this.getText());
            }

            @Override
            protected void applyValue() {
               NumberOption.this.set(NumberOption.this.fromDouble(min + this.value * (max - min)));
            }
         };
      }

      EditBox box = new EditBox(net.minecraft.client.Minecraft.getInstance().font, 0, 0, 150, 20, this.getName());
      box.setValue(this.getString());
      box.setResponder(value -> {
         try {
            this.setUnsafely(Double.parseDouble(value));
         } catch (NumberFormatException ignored) {
         }
      });
      return box;
   }

   @Override
   public Component getDefaultText() {
      return Component.literal(this.getString());
   }
}
