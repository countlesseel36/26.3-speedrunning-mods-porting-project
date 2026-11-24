package me.contaria.seedqueue;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import me.contaria.seedqueue.*;

public final class SeedQueueMixinPlugin implements IMixinConfigPlugin {
   private boolean sodium;

   public void onLoad(String text) {
      this.sodium = FabricLoader.getInstance().isModLoaded("sodium");
   }

   public boolean shouldApplyMixin(String text, String text2) {
      return !text2.contains(".mixin.sodium.") || this.sodium;
   }

   public String getRefMapperConfig() {
      return null;
   }

   public void acceptTargets(Set<String> set, Set<String> set2) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String text, ClassNode classNode, String text2, IMixinInfo iMixinInfo) {
   }

   public void postApply(String text, ClassNode classNode, String text2, IMixinInfo iMixinInfo) {
   }
}
