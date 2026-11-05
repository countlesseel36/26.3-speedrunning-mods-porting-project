package me.contaria.speedrunapi.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import me.contaria.speedrunapi.SpeedrunAPI;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.exceptions.NoSuchConfigException;
import net.fabricmc.loader.api.ModContainer;

public final class SpeedrunConfigContainer<T extends SpeedrunConfig> {
   private final T config;
   private final ModContainer mod;
   private final Map<String, SpeedrunOption<?>> options;

   SpeedrunConfigContainer(T config, ModContainer mod) throws ReflectiveOperationException {
      this.config = config;
      this.mod = mod;
      this.options = Collections.synchronizedMap(config.init());

      try {
         this.load();
      } catch (IOException | JsonParseException e) {
         SpeedrunAPI.LOGGER.warn("Failed to load config file for {}.", this.config.modID(), e);
      }

      try {
         this.save();
      } catch (IOException e) {
         SpeedrunAPI.LOGGER.warn("Failed to save config file for {}.", this.config.modID(), e);
      }
   }

   public T getConfig() {
      return this.config;
   }

   public ModContainer getMod() {
      return this.mod;
   }

   public Map<String, SpeedrunOption<?>> getOptions() {
      return this.options;
   }

   public SpeedrunOption<?> getOption(String id) throws NoSuchConfigException {
      SpeedrunOption<?> option = this.options.get(id);
      if (option == null) {
         throw new NoSuchConfigException("No option " + id + " in " + this.config.modID() + " config.");
      }

      return option;
   }

   public void load() throws IOException, JsonParseException {
      if (!this.config.getConfigFile().isFile()) {
         return;
      }

      this.config.preLoad();

      try {
         String content = Files.readString(this.config.getConfigFile().toPath(), StandardCharsets.UTF_8);
         JsonObject json = SpeedrunConfigAPI.GSON.fromJson(content, JsonObject.class);
         if (json != null) {
            this.config.onLoad(json, null);

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
               SpeedrunOption<?> option = this.options.get(entry.getKey());
               if (option != null) {
                  option.fromJson(entry.getValue());
               }
            }
         }
      } catch (Exception e) {
         try {
            this.config.handleLoadException(e);
         } catch (Exception wrapped) {
            SpeedrunAPI.LOGGER.error("Failed to handle load exception for {}", this.config.modID(), wrapped);
         }
      }

      this.config.finishLoading();
   }

   public void save() throws IOException {
      JsonObject json = new JsonObject();
      this.config.preSave();

      for (Map.Entry<String, SpeedrunOption<?>> entry : this.options.entrySet()) {
         try {
            json.add(entry.getKey(), entry.getValue().toJson());
         } catch (Exception e) {
            try {
               this.config.handleSaveException(e, entry.getValue());
            } catch (Exception wrapped) {
               SpeedrunAPI.LOGGER.error("Failed to handle save exception for {}", this.config.modID(), wrapped);
            }
         }
      }

      this.config.onSave(json);
      Files.createDirectories(this.config.getConfigFile().toPath().getParent());
      Files.writeString(this.config.getConfigFile().toPath(), SpeedrunConfigAPI.GSON.toJson(json), StandardCharsets.UTF_8);
      this.config.finishSaving();
   }
}
