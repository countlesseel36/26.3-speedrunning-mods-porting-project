package me.voidxwalker.worldpreview.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the level the extractor is currently bound to. */
@Mixin(LevelExtractor.class)
public interface LevelExtractorAccessor {
   @Accessor("level")
   ClientLevel worldpreview$getLevel();
}
