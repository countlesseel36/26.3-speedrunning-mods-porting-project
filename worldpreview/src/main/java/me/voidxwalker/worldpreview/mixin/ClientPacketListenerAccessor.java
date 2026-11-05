package me.voidxwalker.worldpreview.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The preview listener has to know its level before chunk packets can be applied. */
@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
   @Accessor("level")
   void worldpreview$setLevel(ClientLevel level);
}
