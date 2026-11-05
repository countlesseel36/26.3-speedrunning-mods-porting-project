package me.voidxwalker.worldpreview.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the preview reset the camera's smoothed eye height when swapping in another level. */
@Mixin(Camera.class)
public interface CameraAccessor {
   @Accessor("eyeHeight")
   void worldpreview$setEyeHeight(float eyeHeight);

   @Accessor("eyeHeightOld")
   void worldpreview$setEyeHeightOld(float eyeHeightOld);
}
