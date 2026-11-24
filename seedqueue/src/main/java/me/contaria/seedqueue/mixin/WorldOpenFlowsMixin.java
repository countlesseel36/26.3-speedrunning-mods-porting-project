package me.contaria.seedqueue.mixin;

import java.util.Optional;
import java.util.function.Function;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions.WorldDataAndGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.contaria.seedqueue.*;
import me.contaria.seedqueue.queue.*;
import me.contaria.seedqueue.preview.*;
import me.contaria.seedqueue.wall.*;
import me.contaria.seedqueue.render.*;
import me.contaria.seedqueue.config.*;

@Mixin({WorldOpenFlows.class})
public abstract class WorldOpenFlowsMixin {
   @Inject(
      method = {"createLevelFromExistingSettings"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$consumeQueuedWorldFromSettings(
      LevelStorageAccess levelStorageAccess,
      ReloadableServerResources serverResources,
      LayeredRegistryAccess<RegistryLayer> registryAccess,
      WorldDataAndGenSettings worldDataAndGenSettings,
      Optional<GameRules> gameRules,
      CallbackInfo callback
   ) {
      if (SeedQueue.consumeForReset(Minecraft.getInstance())) {
         callback.cancel();
      } else {
         SeedQueue.prepareForVanillaWorldCreation(Minecraft.getInstance());
      }
   }

   @Inject(
      method = {"createFreshLevel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void seedqueue$consumeQueuedWorld(
      String levelId, LevelSettings levelSettings, WorldOptions options, Function<Provider, WorldDimensions> dimensionsProvider, Screen parentScreen, CallbackInfo callback
   ) {
      if (SeedQueue.consumeForReset(Minecraft.getInstance())) {
         callback.cancel();
      } else {
         SeedQueue.prepareForVanillaWorldCreation(Minecraft.getInstance());
      }
   }
}
