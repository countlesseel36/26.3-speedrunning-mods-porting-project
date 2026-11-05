package me.contaria.standardsettings.interfaces;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.OptionInstance.ValueSet;

public interface StandardSettingsSimpleOption<T> {
   OptionInstance<T> standardsettings$copy();

   OptionInstance<T> standardsettings$copy(ValueSet<T> var1);
}
