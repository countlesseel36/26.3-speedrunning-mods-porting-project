package me.contaria.seedqueue;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Enables the Sodium mixins only when Sodium is installed. */
public final class SeedQueueMixinPlugin implements IMixinConfigPlugin {
   private boolean sodium;

   @Override
   public void onLoad(String mixinPackage) {
      this.sodium = FabricLoader.getInstance().isModLoaded("sodium");
   }

   @Override
   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return !mixinClassName.contains(".mixin.sodium.") || this.sodium;
   }

   @Override
   public String getRefMapperConfig() {
      return null;
   }

   @Override
   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   @Override
   public List<String> getMixins() {
      return null;
   }

   @Override
   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   @Override
   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
