package me.contaria.speedrunapi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import me.contaria.speedrunapi.SpeedrunAPI;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigScreenProvider;
import me.contaria.speedrunapi.config.api.SpeedrunConfigStorage;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.config.exceptions.InitializeConfigException;
import me.contaria.speedrunapi.config.exceptions.InvalidConfigException;
import me.contaria.speedrunapi.config.exceptions.NoSuchConfigException;
import me.contaria.speedrunapi.config.exceptions.SpeedrunConfigAPIException;
import me.contaria.speedrunapi.config.option.FieldBasedOption;
import me.contaria.speedrunapi.config.screen.SpeedrunConfigScreen;
import me.contaria.speedrunapi.config.toast.SpeedrunConfigErrorToast;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

public final class SpeedrunConfigAPI {
   private static final EnumMap<Config.InitPoint, TreeSet<Uninitialized<?>>> CONFIGS_TO_INITIALIZE = new EnumMap<>(Config.InitPoint.class);
   private static final Map<String, SpeedrunConfigContainer<?>> CONFIGS = Collections.synchronizedMap(new HashMap<>());
   private static final Map<String, SpeedrunConfigScreenProvider> CUSTOM_CONFIG_SCREENS = Collections.synchronizedMap(new HashMap<>());
   private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("mcsr");
   private static final Path GLOBAL_CONFIG_DIR = Optional.ofNullable(System.getenv("XDG_CONFIG_HOME"))
      .map(Paths::get)
      .orElse(Paths.get(System.getProperty("user.home"), ".config"))
      .resolve("mcsr")
      .resolve("config");

   public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

   private SpeedrunConfigAPI() {
   }

   public static void initialize() {
      List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
      mods.sort(Comparator.comparing(mod -> mod.getMetadata().getId()));

      for (ModContainer mod : mods) {
         ModMetadata metadata = mod.getMetadata();
         String modID = metadata.getId();

         try {
            CustomValue customValues = metadata.getCustomValue("speedrunapi");
            if (customValues == null) {
               continue;
            }

            CustomValue.CvObject customObject = customValues.getAsObject();
            CustomValue config = customObject.get("config");
            if (config != null) {
               Class<?> configClass = Class.forName(config.getAsString());
               if (!SpeedrunConfig.class.isAssignableFrom(configClass)) {
                  throw new InitializeConfigException("Provided config class from " + modID + " does not implement SpeedrunConfig.");
               }

               Config configAnnotation = configClass.getAnnotation(Config.class);
               Config.InitPoint init = configAnnotation == null ? Config.InitPoint.ONINITIALIZE : configAnnotation.init();
               int priority = configAnnotation == null ? 1000 : configAnnotation.priority();
               CONFIGS_TO_INITIALIZE.computeIfAbsent(init, key -> new TreeSet<>()).add(new Uninitialized<SpeedrunConfig>((Class<SpeedrunConfig>)configClass, mod, priority));
            }

            CustomValue screen = customObject.get("screen");
            if (screen != null) {
               if (config != null) {
                  throw new InitializeConfigException("Tried to provide both a SpeedrunConfig and a SpeedrunConfigScreenProvider for " + modID + ".");
               }

               Class<?> screenProviderClass = Class.forName(screen.getAsString());
               if (!SpeedrunConfigScreenProvider.class.isAssignableFrom(screenProviderClass)) {
                  throw new InitializeConfigException("Provided config screen provider class from " + modID + " does not implement SpeedrunConfigScreenProvider.");
               }

               CUSTOM_CONFIG_SCREENS.put(modID, (SpeedrunConfigScreenProvider)constructClass(screenProviderClass));
            }
         } catch (ClassCastException e) {
            throw new InitializeConfigException("Faulty fabric.mod.json values from " + modID + ".", e);
         } catch (ClassNotFoundException e) {
            throw new InitializeConfigException("Provided class from " + modID + " does not exist.", e);
         } catch (ReflectiveOperationException e) {
            throw new InitializeConfigException("Failed to initialize config for " + modID + ".", e);
         }
      }
   }

   public static void onPreLaunch() {
      initialize();
      registerConfigsForInitPoint(Config.InitPoint.PRELAUNCH);
   }

   public static void onInitialize() {
      registerConfigsForInitPoint(Config.InitPoint.ONINITIALIZE);
   }

   public static void onPostLaunch() {
      registerConfigsForInitPoint(Config.InitPoint.POSTLAUNCH);
   }

   private static void registerConfigsForInitPoint(Config.InitPoint initPoint) {
      Set<Uninitialized<?>> configs = CONFIGS_TO_INITIALIZE.get(initPoint);
      if (configs != null) {
         configs.forEach(SpeedrunConfigAPI::register);
         configs.clear();
      }
   }

   private static <T extends SpeedrunConfig> void register(Uninitialized<T> uninitialized) {
      ModContainer mod = uninitialized.mod;
      String modID = mod.getMetadata().getId();
      if (CONFIGS.containsKey(modID)) {
         throw new InitializeConfigException("Config for " + modID + " is already registered!");
      }

      try {
         T config = (T)constructClass(uninitialized.config);
         SpeedrunConfigContainer<T> container = new SpeedrunConfigContainer<>(config, mod);
         if (!modID.equals(container.getConfig().modID())) {
            throw new InvalidConfigException("The provided SpeedrunConfig's mod ID (" + container.getConfig().modID() + ") doesn't match the providers mod ID (" + modID + ").");
         }

         CONFIGS.put(modID, container);
         config.finishInitialization(container);
      } catch (ReflectiveOperationException e) {
         throw new InitializeConfigException("Failed to build config for " + modID + ".", e);
      }
   }

   private static <T> T constructClass(Class<T> type) throws ReflectiveOperationException {
      Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
   }

   private static SpeedrunConfigContainer<?> getConfig(String modID) throws NoSuchConfigException {
      SpeedrunConfigContainer<?> config = CONFIGS.get(modID);
      if (config == null) {
         throw new NoSuchConfigException("No config found for mod id " + modID + ".");
      }

      return config;
   }

   public static Path getConfigDir() throws SpeedrunConfigAPIException {
      if (!Files.exists(CONFIG_DIR)) {
         try {
            Files.createDirectories(CONFIG_DIR);
         } catch (IOException e) {
            throw new SpeedrunConfigAPIException("Failed to create speedrun config directory.", e);
         }
      }

      return CONFIG_DIR;
   }

   public static Path getGlobalConfigDir() throws SpeedrunConfigAPIException {
      if (!Files.exists(GLOBAL_CONFIG_DIR)) {
         try {
            Files.createDirectories(GLOBAL_CONFIG_DIR);
         } catch (IOException e) {
            throw new SpeedrunConfigAPIException("Failed to create global speedrun config directory.", e);
         }
      }

      return GLOBAL_CONFIG_DIR;
   }

   public static Object getConfigValue(String modID, String option) throws NoSuchConfigException {
      return getConfig(modID).getOption(option).get();
   }

   public static Optional<Object> getConfigValueOptionally(String modID, String option) {
      try {
         return Optional.ofNullable(getConfigValue(modID, option));
      } catch (NoSuchConfigException e) {
         return Optional.empty();
      }
   }

   public static void setConfigValue(String modID, String option, Object value) throws NoSuchConfigException {
      getConfig(modID).getOption(option).setUnsafely(value);
   }

   public static boolean setConfigValueOptionally(String modID, String option, Object value) {
      try {
         setConfigValue(modID, option, value);
         return true;
      } catch (NoSuchConfigException e) {
         return false;
      }
   }

   public static SpeedrunConfigContainer<?> getConfigContainer(String modID) throws NoSuchConfigException {
      return getConfig(modID);
   }

   public static Map<ModContainer, SpeedrunConfigScreenProvider> getModConfigScreenProviders() {
      Map<ModContainer, SpeedrunConfigScreenProvider> providers = new TreeMap<>(Comparator.comparing(mod -> mod.getMetadata().getName()));
      CUSTOM_CONFIG_SCREENS.forEach(
         (modID, provider) -> FabricLoader.getInstance().getModContainer(modID).ifPresent(mod -> providers.put(mod, provider))
      );
      CONFIGS.forEach(
         (modID, config) -> FabricLoader.getInstance()
            .getModContainer(modID)
            .ifPresent(mod -> {
               if (config.getConfig().hasConfigScreen()) {
                  providers.put(mod, config.getConfig());
               }
            })
      );
      return providers;
   }

   public static Screen createDefaultModConfigScreen(String modID, Predicate<com.mojang.blaze3d.platform.InputConstants.Key> inputListener, Screen parent) {
      return new SpeedrunConfigScreen(modID, parent);
   }

   public static void handleLoadException(String modID, Exception e) {
      SpeedrunAPI.LOGGER.error("Failed to load {} config!", modID, e);
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null) {
         minecraft.execute(() -> SpeedrunConfigErrorToast.showLoadError(modID, e));
      }
   }

   public static void handleSaveException(String modID, Exception e) {
      SpeedrunAPI.LOGGER.error("Failed to save {} config!", modID, e);
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null) {
         minecraft.execute(() -> SpeedrunConfigErrorToast.showSaveError(modID, e));
      }
   }

   static final class Uninitialized<T extends SpeedrunConfig> implements Comparable<Uninitialized<?>> {
      final Class<T> config;
      final ModContainer mod;
      final int priority;

      Uninitialized(Class<T> config, ModContainer mod, int priority) {
         this.config = config;
         this.mod = mod;
         this.priority = priority;
      }

      @Override
      public int compareTo(Uninitialized<?> other) {
         int result = Integer.compare(this.priority, other.priority);
         return result != 0 ? result : this.config.getName().compareTo(other.config.getName());
      }
   }

   /** A config option that is backed by a field but with custom accessors. */
   public static final class CustomOption<T> extends FieldBasedOption<T> {
      private final Getter<T> getter;
      private final Setter<T> setter;
      private final Deserializer<T> fromJson;
      private final Serializer<T> toJson;
      private final WidgetProvider<T> widgetProvider;
      private final boolean hasDefault;
      private final T defaultValue;

      private CustomOption(Builder<T> builder) {
         super(builder.config, builder.configStorage, builder.optionField, builder.idPrefix);
         this.getter = builder.getter;
         this.setter = builder.setter;
         this.fromJson = builder.fromJson;
         this.toJson = builder.toJson;
         this.widgetProvider = builder.widgetProvider;
         this.hasDefault = builder.hasDefault;
         this.defaultValue = builder.defaultValue;
      }

      @Override
      public T get() {
         return this.getter.get(this, this.config, this.configStorage, this.option);
      }

      @Override
      public void set(T value) {
         this.setter.set(this, this.config, this.configStorage, this.option, value);
      }

      @Override
      public void setUnsafely(Object value) {
         this.set((T)value);
      }

      @Override
      public void fromJson(JsonElement jsonElement) {
         this.fromJson.fromJson(this, this.config, this.configStorage, this.option, jsonElement);
      }

      @Override
      public JsonElement toJson() {
         return this.toJson.toJson(this, this.config, this.configStorage, this.option);
      }

      @Override
      public boolean hasWidget() {
         return super.hasWidget();
      }

      @Override
      public AbstractWidget createWidget() {
         return this.widgetProvider != null ? this.widgetProvider.create(this, this.config, this.configStorage, this.option) : this.createDefaultWidget();
      }

      @Override
      public boolean hasDefault() {
         return this.hasDefault;
      }

      @Override
      public T getDefault() {
         return this.defaultValue;
      }

      private AbstractWidget createDefaultWidget() {
         return net.minecraft.client.gui.components.Button.builder(this.getText(), button -> {
         }).bounds(0, 0, 150, 20).build();
      }

      public interface Getter<T> {
         T get(SpeedrunOption<T> option, SpeedrunConfig config, SpeedrunConfigStorage storage, Field field);
      }

      public interface Setter<T> {
         void set(SpeedrunOption<T> option, SpeedrunConfig config, SpeedrunConfigStorage storage, Field field, T value);
      }

      public interface Deserializer<T> {
         void fromJson(SpeedrunOption<T> option, SpeedrunConfig config, SpeedrunConfigStorage storage, Field field, JsonElement json);
      }

      public interface Serializer<T> {
         JsonElement toJson(SpeedrunOption<T> option, SpeedrunConfig config, SpeedrunConfigStorage storage, Field field);
      }

      public interface WidgetProvider<T> {
         AbstractWidget create(SpeedrunOption<T> option, SpeedrunConfig config, SpeedrunConfigStorage storage, Field field);
      }

      public static final class Builder<T> {
         private final SpeedrunConfig config;
         private final SpeedrunConfigStorage configStorage;
         private final Field optionField;
         private final String[] idPrefix;
         private Getter<T> getter = (option, config, storage, field) -> {
            try {
               return (T)field.get(storage);
            } catch (IllegalAccessException e) {
               throw new SpeedrunConfigAPIException("Failed to read " + field.getName(), e);
            }
         };
         private Setter<T> setter = (option, config, storage, field, value) -> {
            try {
               field.set(storage, value);
            } catch (IllegalAccessException e) {
               throw new SpeedrunConfigAPIException("Failed to write " + field.getName(), e);
            }
         };
         private Deserializer<T> fromJson = (option, config, storage, field, json) -> option.set(
            (T)GSON.fromJson(json, field.getType())
         );
         private Serializer<T> toJson = (option, config, storage, field) -> GSON.toJsonTree(option.get(), field.getType());
         private WidgetProvider<T> widgetProvider;
         private boolean hasDefault;
         private T defaultValue;

         public Builder(SpeedrunConfig config, SpeedrunConfigStorage configStorage, Field optionField, String... idPrefix) {
            this.config = config;
            this.configStorage = configStorage;
            this.optionField = optionField;
            this.idPrefix = idPrefix;
         }

         public Builder<T> getter(Getter<T> getter) {
            this.getter = getter;
            return this;
         }

         public Builder<T> setter(Setter<T> setter) {
            this.setter = setter;
            return this;
         }

         public Builder<T> fromJson(Deserializer<T> fromJson) {
            this.fromJson = fromJson;
            return this;
         }

         public Builder<T> toJson(Serializer<T> toJson) {
            this.toJson = toJson;
            return this;
         }

         public Builder<T> createWidget(WidgetProvider<T> widgetProvider) {
            this.widgetProvider = widgetProvider;
            return this;
         }

         public Builder<T> hasDefault() {
            this.hasDefault = true;
            return this;
         }

         public SpeedrunOption<T> build() {
            return new CustomOption<>(this);
         }
      }
   }
}
