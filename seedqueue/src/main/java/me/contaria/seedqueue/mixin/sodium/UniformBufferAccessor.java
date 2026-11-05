package me.contaria.seedqueue.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UniformBufferManager.class)
public interface UniformBufferAccessor {
   @Accessor("hasUpdatedThisFrame")
   boolean seedqueue$hasUpdatedThisFrame();
}
