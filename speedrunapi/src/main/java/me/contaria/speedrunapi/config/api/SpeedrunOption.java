package me.contaria.speedrunapi.config.api;

import com.google.gson.JsonElement;
import me.contaria.speedrunapi.util.TextUtil;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public interface SpeedrunOption<T> {
   String getID();

   String getCategory();

   void setCategory(String category);

   String getModID();

   default Component getName() {
      return TextUtil.translatable("speedrunapi.config." + this.getModID() + ".option." + this.getID());
   }

   default Component getDescription() {
      String key = "speedrunapi.config." + this.getModID() + ".option." + this.getID() + ".description";
      return Language.getInstance().has(key) ? TextUtil.translatable(key) : null;
   }

   default Component getText() {
      Language language = Language.getInstance();
      String value = "speedrunapi.config." + this.getModID() + ".option." + this.getID() + ".value";
      String valueSpecified = value + "." + this.get();
      if (language.has(valueSpecified)) {
         return TextUtil.translatable(valueSpecified);
      }

      return language.has(value) ? TextUtil.translatable(value, this.get()) : this.getDefaultText();
   }

   default Component getDefaultText() {
      return TextUtil.literal(this.getString());
   }

   default String getString() {
      return String.valueOf(this.get());
   }

   T get();

   void set(T value);

   void setUnsafely(Object value) throws ClassCastException;

   default boolean hasDefault() {
      return false;
   }

   default T getDefault() {
      throw new UnsupportedOperationException("SpeedrunOption does not have a default value!");
   }

   void fromJson(JsonElement json);

   JsonElement toJson();

   boolean hasWidget();

   AbstractWidget createWidget();
}
