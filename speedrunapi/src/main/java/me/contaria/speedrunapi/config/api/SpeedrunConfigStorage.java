package me.contaria.speedrunapi.config.api;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.config.exceptions.InvalidConfigException;
import me.contaria.speedrunapi.config.option.BooleanOption;
import me.contaria.speedrunapi.config.option.DoubleOption;
import me.contaria.speedrunapi.config.option.EnumOption;
import me.contaria.speedrunapi.config.option.FloatOption;
import me.contaria.speedrunapi.config.option.IntegerOption;
import me.contaria.speedrunapi.config.option.LongOption;
import me.contaria.speedrunapi.config.option.ShortOption;
import me.contaria.speedrunapi.config.option.StringOption;

public interface SpeedrunConfigStorage {
   default Map<String, SpeedrunOption<?>> init(SpeedrunConfig config, String... optionIDPrefix) throws ReflectiveOperationException {
      Map<String, SpeedrunOption<?>> options = new LinkedHashMap<>();
      List<Class<?>> classes = new ArrayList<>();

      for (Class<?> clas = this.getClass(); clas != null; clas = clas.getSuperclass()) {
         classes.add(clas);
      }

      Collections.reverse(classes);

      for (Class<?> clas : classes) {
         for (Field field : clas.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Config.Ignored.class) && (config.shouldParseStaticFields() || !Modifier.isStatic(field.getModifiers()))) {
               Class<?> type = field.getType();
               SpeedrunOption<?> option = this.parseField(field, config, optionIDPrefix);
               if (option != null) {
                  options.put(option.getID(), option);
               } else {
                  if (!SpeedrunConfigStorage.class.isAssignableFrom(type) || SpeedrunConfig.class.isAssignableFrom(type)) {
                     String id = field.getName();
                     if (optionIDPrefix.length != 0) {
                        id = String.join(":", optionIDPrefix) + ":" + id;
                     }

                     throw new InvalidConfigException("Option " + id + " is of an unsupported type (" + type + ") in " + config.modID() + " config.");
                  }

                  field.setAccessible(true);
                  String[] updatedOptionIDPrefix = new String[optionIDPrefix.length + 1];
                  System.arraycopy(optionIDPrefix, 0, updatedOptionIDPrefix, 0, optionIDPrefix.length);
                  updatedOptionIDPrefix[updatedOptionIDPrefix.length - 1] = field.getName();
                  Map<String, SpeedrunOption<?>> configDataOptions = ((SpeedrunConfigStorage)field.get(this)).init(config, updatedOptionIDPrefix);
                  Config.Category category = field.getAnnotation(Config.Category.class);
                  if (category != null) {
                     for (SpeedrunOption<?> o : configDataOptions.values()) {
                        if (o.getCategory() == null) {
                           o.setCategory(category.value());
                        }
                     }
                  }

                  options.putAll(configDataOptions);
               }
            }
         }
      }

      return options;
   }

   default SpeedrunOption<?> parseField(Field field, SpeedrunConfig config, String... idPrefix) {
      Class<?> type = field.getType();
      if (boolean.class.equals(type)) {
         return new BooleanOption(config, this, field, idPrefix);
      } else if (short.class.equals(type)) {
         return new ShortOption(config, this, field, idPrefix);
      } else if (int.class.equals(type)) {
         return new IntegerOption(config, this, field, idPrefix);
      } else if (long.class.equals(type)) {
         return new LongOption(config, this, field, idPrefix);
      } else if (float.class.equals(type)) {
         return new FloatOption(config, this, field, idPrefix);
      } else if (double.class.equals(type)) {
         return new DoubleOption(config, this, field, idPrefix);
      } else if (String.class.equals(type)) {
         return new StringOption(config, this, field, idPrefix);
      } else {
         return type.isEnum() ? new EnumOption(config, this, field, idPrefix) : null;
      }
   }
}
