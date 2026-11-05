package me.voidxwalker.worldpreview.mixin;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The spawn the server reports through {@code getRespawnData()} can be moved to a safe position
 * while the world generates, so the preview camera would not match where the player actually
 * spawns. The authoritative position arrives in the player position packet; mirror it onto the
 * preview player while the preview is still visible.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMoveMixin {
   @Inject(method = "handleMovePlayer", at = @At("RETURN"))
   private void worldpreview$syncPlayerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      if (!WorldPreview.isActive()) {
         return;
      }

      var change = packet.change();
      WorldPreview.syncServerPosition(change.position(), change.yRot(), change.xRot());
   }
}
