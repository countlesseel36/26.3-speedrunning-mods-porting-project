package me.contaria.standardsettings;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import me.contaria.standardsettings.options.StandardSetting;

public class StandardSettingsCache {
   private final String id;
   protected final Set<StandardSettingsCache.Entry<?>> cache;

   public StandardSettingsCache(String id) {
      this.id = id;
      this.cache = new HashSet<>();

      for (StandardSetting<?> setting : StandardSettings.config.standardSettings) {
         this.cache.add(new StandardSettingsCache.Entry<>(setting));
      }
   }

   public String getId() {
      return this.id;
   }

   public void load() {
      for (StandardSettingsCache.Entry<?> cacheEntry : this.cache) {
         cacheEntry.load();
      }

      StandardSettings.updateSettings();
   }

   protected static class Entry<T> {
      public final StandardSetting<T> setting;
      public final T value;

      private Entry(StandardSetting<T> setting) {
         this.setting = setting;
         this.value = setting.getVanilla();
      }

      public void load() {
         this.setting.setVanilla(this.value);
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            StandardSettingsCache.Entry<?> entry = (StandardSettingsCache.Entry<?>)o;
            return Objects.equals(this.setting, entry.setting) && Objects.equals(this.value, entry.value);
         } else {
            return false;
         }
      }
   }
}
