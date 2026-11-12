package me.contaria.standardsettings.options;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton.Builder;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class DebugStandardSetting extends StandardSetting<DebugScreenEntryStatus> {
   private final Identifier id;
   private DebugScreenEntryStatus value;

   public DebugStandardSetting(String id, String category, Identifier id2) {
      super(id, category);
      this.id = id2;
      this.set(this.getVanilla());
   }

   public DebugScreenEntryStatus get() {
      return this.value;
   }

   public void set(DebugScreenEntryStatus value) {
      this.value = value;
   }

   public DebugScreenEntryStatus getVanilla() {
      return Minecraft.getInstance().debugEntries.getStatus(this.id);
   }

   public void setVanilla(DebugScreenEntryStatus value) {
      Minecraft.getInstance().debugEntries.setStatus(this.id, value);
   }

   @Override
   protected void valueFromJson(JsonElement jsonElement) {
      DebugScreenEntryStatus.CODEC.parse(JsonOps.INSTANCE, jsonElement).result().ifPresent(this::set);
   }

   @Override
   protected JsonElement valueToJson() {
      return (JsonElement)DebugScreenEntryStatus.CODEC.encodeStart(JsonOps.INSTANCE, this.get()).result().orElseThrow();
   }

   public Component getName() {
      // Debug entries have no translated name in this port; show the vanilla-style name where one
      // exists and fall back to the identifier path ("biome", "chunk_generation_stats", ...).
      String path = this.id.getPath();
      String key = "debug." + path;
      String translated = net.minecraft.locale.Language.getInstance().getOrDefault(key);

      return Component.literal(translated.equals(key) ? path : translated);
   }

   @Override
   public Component getDisplayText() {
      return readableStatus(this.value);
   }

   private static Component readableStatus(DebugScreenEntryStatus status) {
      return (Component)(switch (status) {
         case ALWAYS_ON -> Component.translatable("debug.entry.always");
         case IN_OVERLAY -> Component.translatable("debug.entry.overlay");
         case NEVER -> CommonComponents.OPTION_OFF;
         default -> throw new MatchException(null, null);
      });
   }

   @Override
   public AbstractWidget createMainWidget() {
      return new net.minecraft.client.gui.components.CycleButton.Builder<net.minecraft.client.gui.components.debug.DebugScreenEntryStatus>(DebugStandardSetting::readableStatus, this::get)
         .withValues(DebugScreenEntryStatus.values())
         .displayOnlyValue()
         .create(0, 0, 120, 20, Component.empty(), (button, value) -> this.set(value));
   }
}
