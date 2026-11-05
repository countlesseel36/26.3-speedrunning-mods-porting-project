package me.contaria.standardsettings.options;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.function.Function;
import me.contaria.standardsettings.interfaces.StandardSettingsSimpleOption;
import me.contaria.standardsettings.mixin.accessors.CyclingButtonWidgetAccessor;
import me.contaria.standardsettings.mixin.accessors.SimpleOptionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton.DisplayState;
import net.minecraft.network.chat.Component;

public class SimpleOptionStandardSetting<T> extends StandardSetting<T> {
   protected final OptionInstance<T> option;
   protected final OptionInstance<T> copy;

   public SimpleOptionStandardSetting(String id, String category, OptionInstance<T> option) {
      super(id, category);
      this.option = option;
      this.copy = this.copy(option);
      this.set(this.getVanilla());
   }

   protected OptionInstance<T> copy(OptionInstance<T> option) {
      OptionInstance<T> copy = this.copySimpleOption(option);
      SimpleOptionAccessor<T> copyAccessor = (SimpleOptionAccessor<T>)(Object)copy;
      Function<T, Component> textGetter = copyAccessor.standardsettings$getTextGetter();
      copyAccessor.standardsettings$setTextGetter(value -> {
         Component name = this.getName();
         Component text = textGetter.apply(value);
         String prefix = name.getString() + ": ";
         String string = text.getString();
         return (Component)(string.startsWith(prefix) ? Component.literal(string.substring(prefix.length())) : text);
      });
      return copy;
   }

   protected OptionInstance<T> copySimpleOption(OptionInstance<T> option) {
      return ((StandardSettingsSimpleOption)(Object)option).standardsettings$copy();
   }

   public T get() {
      return this.get(this.copy);
   }

   public void set(T value) {
      this.set(this.copy, value);
   }

   @Override
   public T getVanilla() {
      return this.get(this.option);
   }

   @Override
   public void setVanilla(T value) {
      this.set(this.option, value);
   }

   protected T get(OptionInstance<T> option) {
      return (T)option.get();
   }

   protected void set(OptionInstance<T> option, T value) {
      option.set(value);
   }

   @Override
   protected void valueFromJson(JsonElement jsonElement) {
      this.copy.codec().parse(JsonOps.INSTANCE, jsonElement).result().ifPresent(this::set);
   }

   @Override
   protected JsonElement valueToJson() {
      return (JsonElement)this.copy.codec().encodeStart(JsonOps.INSTANCE, this.copy.get()).result().orElseThrow();
   }

   public Component getName() {
      return ((SimpleOptionAccessor)(Object)this.copy).standardsettings$getText();
   }

   @Override
   public Component getDisplayText() {
      return (Component)((SimpleOptionAccessor)(Object)this.copy).standardsettings$getTextGetter().apply(this.get());
   }

   @Override
   public AbstractWidget createMainWidget() {
      AbstractWidget widget = this.copy.createButton(Minecraft.getInstance().options, 0, 0, 120, this::set);
      if (widget instanceof CyclingButtonWidgetAccessor cyclingWidget) {
         cyclingWidget.standardsettings$setOptionTextOmitted(DisplayState.VALUE);
      }

      return widget;
   }
}
