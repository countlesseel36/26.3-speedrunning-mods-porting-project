package me.contaria.standardsettings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.BooleanSupplier;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.standardsettings.gui.StandardOptionWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * 单个标准设置的抽象基类：ID、分类、可见性、序列化与启用状态。
 */
public abstract class StandardSetting<T> implements SpeedrunOption<T> {
   private final String id;
   private final String category;
   private BooleanSupplier visible = () -> true;
   private boolean enabled = true;

   protected StandardSetting(String id, String category) {
      this.id = id;
      this.category = category;
   }

   /**
    * 设置项唯一 ID。
    */
   public String getID() {
      return this.id;
   }

   /**
    * 设置项分类。
    */
   public String getCategory() {
      return this.category;
   }

   /**
    * 设置分类。
    */
   public void setCategory(String category) {
      throw new UnsupportedOperationException();
   }

   /**
    * 所属 mod。
    */
   public String getModID() {
      return "standardsettings";
   }

   public void setUnsafely(Object value) throws ClassCastException {
      this.set((T)value);
   }

   /**
    * 是否拥有可交互控件。
    */
   public boolean hasWidget() {
      return this.visible.getAsBoolean();
   }

   /**
    * 设置可见性。
    */
   public void setVisible(boolean visible) {
      this.visible = () -> visible;
   }

   public void setVisible(BooleanSupplier visible) {
      this.visible = visible;
   }

   /**
    * 创建设置控件。
    */
   public final AbstractWidget createWidget() {
      return new StandardOptionWidget(this, this.createMainWidget());
   }

   public abstract AbstractWidget createMainWidget();

   /**
    * 设置描述。
    */
   public Component getDescription() {
      return null;
   }

   /**
    * 当前值的显示文本。
    */
   public final Component getText() {
      return (Component)(!this.isEnabled() ? Component.literal("-") : this.getDisplayText());
   }

   protected abstract Component getDisplayText();

   /**
    * 从 JSON 读取。
    */
   public final void fromJson(JsonElement jsonElement) {
      if (jsonElement.isJsonObject()) {
         this.enabled = jsonElement.getAsJsonObject().get("enabled").getAsBoolean();
         this.valueFromJson(jsonElement.getAsJsonObject().get("value"));
      } else {
         this.enabled = true;
         this.valueFromJson(jsonElement);
      }
   }

   protected abstract void valueFromJson(JsonElement json);

   /**
    * 写入 JSON。
    */
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

   public abstract void setVanilla(T value);

   /**
    * 重置为默认值。
    */
   public void resetOption() {
      /**
       * 是否启用。
       */
      if (this.isEnabled()) {
         this.setVanilla((T)this.get());
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   /**
    * 设置启用状态。
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * 切换启用状态。
    */
   public boolean toggleEnabled() {
      return this.enabled = !this.enabled;
   }

   /**
    * 禁用该设置。
    */
   public void disable() {
      this.enabled = false;
   }
}
