package me.contaria.seedqueue.mixin;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/** Replaces a fresh world creation with a pre-generated queued world when one is ready. */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
   @Inject(method = "createLevelFromExistingSettings", at = @At("HEAD"), cancellable = true)
   private void seedqueue$consumeQueuedWorldFromSettings(
      net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess access,
      net.minecraft.server.ReloadableServerResources resources,
      net.minecraft.core.LayeredRegistryAccess<net.minecraft.server.RegistryLayer> registries,
      net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings settings,
      java.util.Optional<net.minecraft.world.level.gamerules.GameRules> gameRules,
      CallbackInfo ci
   ) {
      if (SeedQueue.consumeForReset(Minecraft.getInstance())) {
         ci.cancel();
      } else {
         // Vanilla is about to build a brand new world instead of using a queued one. The wall must
         // let go of Minecraft.level first, otherwise its preview cycle keeps swapping preview levels
         // into the client while the new world loads and the "Preparing world generation" screen never
         // goes away.
         SeedQueue.prepareForVanillaWorldCreation(Minecraft.getInstance());
      }
   }

   @Inject(method = "createFreshLevel", at = @At("HEAD"), cancellable = true)
   private void seedqueue$consumeQueuedWorld(
      String levelId,
      LevelSettings settings,
      WorldOptions options,
      Function<HolderLookup.Provider, WorldDimensions> dimensions,
      Screen parent,
      CallbackInfo ci
   ) {
      if (SeedQueue.consumeForReset(Minecraft.getInstance())) {
         ci.cancel();
      } else {
         SeedQueue.prepareForVanillaWorldCreation(Minecraft.getInstance());
      }
   }
}