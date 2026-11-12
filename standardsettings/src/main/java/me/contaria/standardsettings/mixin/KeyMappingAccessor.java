package me.contaria.standardsettings.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the raw key code, which SDL3 does not round-trip reliably through names. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
   @Accessor("key")
   InputConstants.Key standardsettings$key();
}
