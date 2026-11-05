package me.contaria.speedrunapi.config.option;

import com.google.gson.JsonElement;
import java.lang.reflect.Field;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.config.exceptions.ReflectionConfigException;
import me.contaria.speedrunapi.util.TextUtil;
import net.minecraft.network.chat.Component;

public abstract class FieldBasedOption<T> implements SpeedrunOption<T> {
   protected final SpeedrunConfig config;
   protected final SpeedrunConfigStorage configStorage;
   protected final Field option;
   protected final String id;
   protected final String category;
   protected final String name;
   protected final String description;
   protected final String text;

   protected FieldBasedOption(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field option, String... idPrefix) {
      this.config = config;
      this.configStorage = configStorage;
      this.option = option;
      this.option.setAccessible(true);

      String fieldID = option.getName();
      this.id = idPrefix.length == 0 ? fieldID : String.join(":", idPrefix) + ":" + fieldID;

      Config.Category category = option.getAnnotation(Config.Category.class);
      this.category = category == null ? null : category.value();

      Config.Name name = option.getAnnotation(Config.Name.class);
      this.name = name == null ? null : name.value();

      Config.Description description = option.getAnnotation(Config.Description.class);
      this.description = description == null ? null : description.value();

      Config.Text text = option.getAnnotation(Config.Text.class);
      this.text = text == null ? null : text.value();
   }

   @Override
   public String getID() {
      return this.id;
   }

   @Override
   public String getCategory() {
      return this.category;
   }

   @Override
   public void setCategory(String category) {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getModID() {
      return this.config.modID();
   }

   @Override
   public Component getName() {
      return this.name != null ? TextUtil.translatable(this.name) : SpeedrunOption.super.getName();
   }

   @Override
   public Component getDescription() {
      return this.description != null ? TextUtil.translatable(this.description) : SpeedrunOption.super.getDescription();
   }

   @Override
   public void fromJson(JsonElement jsonElement) {
      try {
         this.setUnsafely(me.contaria.speedrunapi.config.SpeedrunConfigAPI.GSON.fromJson(jsonElement, this.option.getType()));
      } catch (Exception e) {
         throw new ReflectionConfigException("Failed to read option " + this.id, e);
      }
   }

   @Override
   public JsonElement toJson() {
      try {
         return me.contaria.speedrunapi.config.SpeedrunConfigAPI.GSON.toJsonTree(this.get(), this.option.getType());
      } catch (Exception e) {
         throw new ReflectionConfigException("Failed to write option " + this.id, e);
      }
   }

   @Override
   public boolean hasWidget() {
      return !this.option.isAnnotationPresent(Config.Hide.class);
   }

   protected Object rawGet() {
      try {
         return this.option.get(this.configStorage);
      } catch (IllegalAccessException e) {
         throw new ReflectionConfigException("Failed to read option " + this.id, e);
      }
   }

   protected void rawSet(Object value) {
      try {
         this.option.set(this.configStorage, value);
      } catch (IllegalAccessException e) {
         throw new ReflectionConfigException("Failed to write option " + this.id, e);
      }
   }
}
