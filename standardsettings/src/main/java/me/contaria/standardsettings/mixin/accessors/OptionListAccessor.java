package me.contaria.standardsettings.mixin.accessors;

import java.util.Comparator;
import java.util.Map.Entry;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.screens.debug.DebugOptionsScreen.OptionList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionList.class)
public interface OptionListAccessor {
   @Accessor("COMPARATOR")
   static Comparator<Entry<Identifier, DebugScreenEntry>> getComparator() {
      throw new UnsupportedOperationException();
   }
}
