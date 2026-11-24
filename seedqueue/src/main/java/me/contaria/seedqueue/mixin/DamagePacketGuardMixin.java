package me.contaria.seedqueue.mixin;

import java.util.Optional;
import java.util.Set;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({ServerCommonPacketListenerImpl.class})
public abstract class DamagePacketGuardMixin {
   private static final Set<String> UNSAFE_DAMAGE_TYPES = Set.of("spear");
   private static final ThreadLocal<Boolean> REMAPPING = ThreadLocal.withInitial(() -> Boolean.FALSE);
   @Shadow
   @Final
   protected MinecraftServer server;

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$dropForeignDamageType(Packet<?> packet, CallbackInfo callback) {
      if (packet instanceof ClientboundDamageEventPacket clientboundDamageEventPacket) {
         String text = clientboundDamageEventPacket.sourceType().unwrapKey().map(arg -> arg.identifier().getPath()).orElse("");
         if (UNSAFE_DAMAGE_TYPES.contains(text)) {
            Optional optional = this.server
               .registryAccess()
               .lookupOrThrow(Registries.DAMAGE_TYPE)
               .get(ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.withDefaultNamespace("generic")));
            if (optional.isPresent()) {
               if (!Boolean.TRUE.equals(REMAPPING.get())) {
                  REMAPPING.set(Boolean.TRUE);

                  try {
                     (((ServerCommonPacketListenerImpl)(Object)this))
                        .send(
                           new ClientboundDamageEventPacket(
                              clientboundDamageEventPacket.entityId(), (Holder)optional.get(), clientboundDamageEventPacket.sourceCauseId(), clientboundDamageEventPacket.sourceDirectId(), clientboundDamageEventPacket.sourcePosition()
                           )
                        );
                  } finally {
                     REMAPPING.set(Boolean.FALSE);
                  }

                  callback.cancel();
               }
            } else {
               if (!SeedQueue.isDamageTypeRegistered(this.server, clientboundDamageEventPacket)) {
                  if (optional.isPresent()) {
                     SeedQueue.LOGGER.info("[seedqueue] remapped damage_event to generic (entity={}, cause={})", clientboundDamageEventPacket.entityId(), clientboundDamageEventPacket.sourceCauseId());
                     (((ServerCommonPacketListenerImpl)(Object)this))
                        .send(
                           new ClientboundDamageEventPacket(
                              clientboundDamageEventPacket.entityId(), (Holder)optional.get(), clientboundDamageEventPacket.sourceCauseId(), clientboundDamageEventPacket.sourceDirectId(), clientboundDamageEventPacket.sourcePosition()
                           )
                        );
                     callback.cancel();
                     return;
                  }

                  SeedQueue.LOGGER
                     .warn(
                        "[seedqueue] dropped damage_event with an unregistered damage type (entity={}, cause={}, direct={})",
                        clientboundDamageEventPacket.entityId(),
                        clientboundDamageEventPacket.sourceCauseId(),
                        clientboundDamageEventPacket.sourceDirectId()
                     );
                  callback.cancel();
               }
            }
         }
      }
   }
}
