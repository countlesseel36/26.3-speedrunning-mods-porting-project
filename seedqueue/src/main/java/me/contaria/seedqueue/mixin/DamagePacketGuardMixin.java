package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops "damage_event" packets whose damage type is not registered in this server.
 *
 * <p>SeedQueue runs several integrated servers with separate registry instances. A damage source whose
 * {@code DamageType} holder belongs to another server cannot be written to the connection:
 * {@code RegistryFriendlyByteBuf.writeId} throws and Netty reports
 * {@code EncoderException: Failed to encode packet 'clientbound/minecraft:damage_event'}, which drops the
 * player ("Disconnected"). Checking the key against this server's registries is the only reliable test -
 * the holder's key itself is present, so {@code unwrapKey()} alone did not catch it.</p>
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class DamagePacketGuardMixin {
   @Shadow
   @Final
   protected MinecraftServer server;

   @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
   private void seedqueue$dropForeignDamageType(Packet<?> packet, CallbackInfo ci) {
      if (!(packet instanceof ClientboundDamageEventPacket damage)) {
         return;
      }

      // 队列里的其它服务端与当前连接不是同一套注册表：它们的伤害事件永远无法编码。
      // 只有玩家真正所在的服务端才允许发送该包（实测 minecraft:spear 绕过了注册表检查）。
      net.minecraft.server.MinecraftServer live = SeedQueue.liveServer();

      if (live != null && live != this.server) {
         SeedQueue.LOGGER.info(
            "[seedqueue] dropped damage_event from a background server (entity={})", damage.entityId()
         );
         ci.cancel();
         return;
      }

      if (!SeedQueue.isDamageTypeRegistered(this.server, damage)) {
         SeedQueue.LOGGER.warn(
            "[seedqueue] dropped damage_event with an unregistered damage type (entity={}, cause={}, direct={})",
            damage.entityId(), damage.sourceCauseId(), damage.sourceDirectId()
         );
         ci.cancel();
      }
   }
}
