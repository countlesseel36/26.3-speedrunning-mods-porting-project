package me.contaria.standardsettings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.util.function.Supplier;
import me.contaria.standardsettings.StandardSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button.Plain;
import net.minecraft.network.chat.Component;

public class KeyBindingStandardSetting extends StandardSetting<Key> {
   private final KeyMapping keyBinding;
   private Key value;

   public KeyBindingStandardSetting(String id, String category, KeyMapping keyBinding) {
      super(id, category);
      this.keyBinding = keyBinding;
      this.set(this.getVanilla());
   }

   public Key get() {
      return this.value;
   }

   public void set(Key value) {
      this.value = value;
   }

   public Key getVanilla() {
      return InputConstants.getKey(this.keyBinding.saveString());
   }

   public void setVanilla(Key value) {
      this.keyBinding.setKey(value);
   }

   @Override
   protected void valueFromJson(JsonElement jsonElement) {
      this.set(InputConstants.getKey(jsonElement.getAsString()));
   }

   @Override
   protected JsonElement valueToJson() {
      return new JsonPrimitive(this.get().getName());
   }

   public Component getName() {
      return Component.translatable(this.keyBinding.getName());
   }

   @Override
   public Component getDisplayText() {
      Component text = this.value.getDisplayName();
      if (StandardSettings.config.isFocusedKeyBinding(this)) {
         return Component.literal("> ")
            .append(text.copy().withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.UNDERLINE}))
            .append(" <")
            .withStyle(ChatFormatting.YELLOW);
      }

      if (this.value != InputConstants.UNKNOWN) {
         for (StandardSetting<?> setting : StandardSettings.config.standardSettings) {
            if (setting != this
               && setting instanceof KeyBindingStandardSetting
               && setting.isEnabled()
               && this.value.equals(((KeyBindingStandardSetting)setting).value)) {
               return Component.literal("[ ").append(text.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(ChatFormatting.YELLOW);
            }
         }
      }

      return text;
   }

   @Override
   public AbstractWidget createMainWidget() {
      return new Plain(0, 0, 120, 20, this.getText(), button -> StandardSettings.config.setFocusedKeyBinding(this), Supplier::get) {
         protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.setMessage(KeyBindingStandardSetting.this.getText());
            super.extractContents(graphics, mouseX, mouseY, a);
         }
      };
   }
}
