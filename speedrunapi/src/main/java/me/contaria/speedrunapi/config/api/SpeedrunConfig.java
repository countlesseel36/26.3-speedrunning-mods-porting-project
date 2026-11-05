package me.contaria.speedrunapi.config.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.util.Map;
import java.util.function.Predicate;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import net.minecraft.client.gui.screens.Screen;

public interface SpeedrunConfig extends SpeedrunConfigStorage, SpeedrunConfigScreenProvider {
   String modID();

   default Map<String, SpeedrunOption<?>> init() throws ReflectiveOperationException {
      return this.init(this);
   }

   default void finishInitialization(SpeedrunConfigContainer<?> container) {
   }

   default void preLoad() {
   }

   default void onLoad(JsonObject jsonObject, SpeedrunConfigParsedMetadata metadata) {
   }

   default void finishLoading() {
   }

   default void preSave() {
   }

   default void onSave(JsonObject jsonObject) {
   }

   default void finishSaving() {
   }

   default void onConfigScreenClose(Screen current, Screen parent) {
   }

   default File getConfigFile() {
      return SpeedrunConfigAPI.getConfigDir().resolve(this.modID() + ".json").toFile();
   }

   default int getDataVersion() {
      return 0;
   }

   @Override
   default boolean hasConfigScreen() {
      return true;
   }

   @Override
   default Screen createConfigScreen(Screen parent) {
      return SpeedrunConfigAPI.createDefaultModConfigScreen(this.modID(), null, parent);
   }

   default Predicate<InputConstants.Key> createInputListener() {
      return null;
   }

   default boolean shouldShowCategory(String category) {
      return true;
   }

   default boolean shouldShowOption(String option) {
      return true;
   }

   default boolean shouldParseStaticFields() {
      return false;
   }

   default void handleLoadException(Exception e) throws Exception {
      SpeedrunConfigAPI.handleLoadException(this.modID(), e);
   }

   default void handleLoadException(Exception e, SpeedrunOption<?> option, JsonElement jsonElement) throws Exception {
      throw e;
   }

   default void handleSaveException(Exception e) throws Exception {
      SpeedrunConfigAPI.handleSaveException(this.modID(), e);
   }

   default void handleSaveException(Exception e, SpeedrunOption<?> option) throws Exception {
      throw e;
   }
}
