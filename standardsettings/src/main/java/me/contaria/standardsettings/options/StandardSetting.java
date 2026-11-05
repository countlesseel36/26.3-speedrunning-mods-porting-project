package me.contaria.standardsettings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.BooleanSupplier;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.standardsettings.gui.StandardOptionWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public abstract class StandardSetting<T> implements SpeedrunOption<T> {
   private final String id;
   private final String category;
   private BooleanSupplier visible = () -> true;
   private boolean enabled = true;

   protected StandardSetting(String id, String category) {
      this.id = id;
      this.category = category;
   }

   public String getID() {
      return this.id;
   }

   public String getCategory() {
      return this.category;
   }

   public void setCategory(String category) {
      throw new UnsupportedOperationException();
   }

   public String getModID() {
      return "standardsettings";
   }

   public void setUnsafely(Object value) throws ClassCastException {
      this.set((T)value);
   }

   public boolean hasWidget() {
      return this.visible.getAsBoolean();
   }

   public void setVisible(boolean visible) {
      this.visible = () -> visible;
   }

   public void setVisible(BooleanSupplier visible) {
      this.visible = visible;
   }

   public final AbstractWidget createWidget() {
      return new StandardOptionWidget(this, this.createMainWidget());
   }

   public abstract AbstractWidget createMainWidget();

   public Component getDescription() {
      return null;
   }

   public final Component getText() {
      return (Component)(!this.isEnabled() ? Component.literal("-") : this.getDisplayText());
   }

   protected abstract Component getDisplayText();

   public final void fromJson(JsonElement jsonElement) {
      if (jsonElement.isJsonObject()) {
         this.enabled = jsonElement.getAsJsonObject().get("enabled").getAsBoolean();
         this.valueFromJson(jsonElement.getAsJsonObject().get("value"));
      } else {
         this.enabled = true;
         this.valueFromJson(jsonElement);
      }
   }

   protected abstract void valueFromJson(JsonElement var1);

   public final JsonElement toJson() {
      if (this.enabled) {
         return this.valueToJson();
      }

      JsonObject jsonObject = new JsonObject();
      jsonObject.add("enabled", new JsonPrimitive(false));
      jsonObject.add("value", this.valueToJson());
      return jsonObject;
   }

   protected abstract JsonElement valueToJson();

   public abstract T getVanilla();

   public abstract void setVanilla(T var1);

   public void resetOption() {
      if (this.isEnabled()) {
         this.setVanilla((T)this.get());
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean toggleEnabled() {
      return this.enabled = !this.enabled;
   }

   public void disable() {
      this.enabled = false;
   }
}
