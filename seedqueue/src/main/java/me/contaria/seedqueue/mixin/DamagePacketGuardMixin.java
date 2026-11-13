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
   private static final ThreadLocal<Boolean> REMAPPING = ThreadLocal.withInitial(() -> Boolean.FALSE);

   @Shadow
   @Final
   protected MinecraftServer server;

   @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
   private void seedqueue$dropForeignDamageType(Packet<?> packet, CallbackInfo ci) {
      if (!(packet instanceof ClientboundDamageEventPacket damage)) {
         return;
      }


      // 实测编码用的注册表与 server / client 世界两套注册表都对不上（minecraft:spear 一直漏网），
      // 因此统一改写成每个注册表都必然存在的 minecraft:generic。
      var generic = this.server.registryAccess()
         .lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
         .get(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE,
            net.minecraft.resources.Identifier.withDefaultNamespace("generic")
         ));

      if (generic.isPresent()) {
         if (Boolean.TRUE.equals(REMAPPING.get())) {
            return;
         }

         REMAPPING.set(Boolean.TRUE);

         try {
            ((ServerCommonPacketListenerImpl) (Object) this).send(new ClientboundDamageEventPacket(
               damage.entityId(),
               generic.get(),
               damage.sourceCauseId(),
               damage.sourceDirectId(),
               damage.sourcePosition()
            ));
         } finally {
            REMAPPING.set(Boolean.FALSE);
         }

         ci.cancel();
         return;
      }

      if (!SeedQueue.isDamageTypeRegistered(this.server, damage)) {
         // 客户端注册表里没有该伤害类型（例如矛的 minecraft:spear）。
         // 直接丢弃会丢失受击动画/音效，所以改写成客户端一定认识的 generic，其余字段保持不变。
         if (generic.isPresent()) {
            SeedQueue.LOGGER.info(
               "[seedqueue] remapped damage_event to generic (entity={}, cause={})",
               damage.entityId(), damage.sourceCauseId()
            );
            ((ServerCommonPacketListenerImpl) (Object) this).send(new ClientboundDamageEventPacket(
               damage.entityId(),
               generic.get(),
               damage.sourceCauseId(),
               damage.sourceDirectId(),
               damage.sourcePosition()
            ));
            ci.cancel();
            return;
         }

         SeedQueue.LOGGER.warn(
            "[seedqueue] dropped damage_event with an unregistered damage type (entity={}, cause={}, direct={})",
            damage.entityId(), damage.sourceCauseId(), damage.sourceDirectId()
         );
         ci.cancel();
      }
   }
}
