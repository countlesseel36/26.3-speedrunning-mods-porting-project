package me.contaria.standardsettings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;

public class PlayerModelPartStandardSetting extends StandardSetting<Boolean> {
   public final PlayerModelPart playerModelPart;
   private boolean value;

   public PlayerModelPartStandardSetting(String id, String category, PlayerModelPart playerModelPart) {
      super(id, category);
      this.playerModelPart = playerModelPart;
      this.set(this.getVanilla());
   }

   public Boolean get() {
      return this.value;
   }

   public void set(Boolean value) {
      this.value = value;
   }

   public Boolean getVanilla() {
      return Minecraft.getInstance().options.isModelPartEnabled(this.playerModelPart);
   }

   public void setVanilla(Boolean value) {
      Minecraft.getInstance().options.setModelPart(this.playerModelPart, value);
   }

   @Override
   protected void valueFromJson(JsonElement jsonElement) {
      this.set(jsonElement.getAsBoolean());
   }

   @Override
   protected JsonElement valueToJson() {
      return new JsonPrimitive(this.get());
   }

   public Component getName() {
      return this.playerModelPart.getName();
   }

   @Override
   public Component getDisplayText() {
      return CommonComponents.optionStatus(this.get());
   }

   @Override
   public AbstractWidget createMainWidget() {
      return Button.builder(this.getText(), button -> {
         this.set(!this.get());
         button.setMessage(this.getText());
      }).bounds(0, 0, 120, 20).build();
   }
}
