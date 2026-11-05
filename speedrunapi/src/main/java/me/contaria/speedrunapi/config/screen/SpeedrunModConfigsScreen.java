package me.contaria.speedrunapi.config.screen;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.api.SpeedrunConfigScreenProvider;
import me.contaria.speedrunapi.util.TextUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** SpeedrunAPI mod configs list with icons, name/version, authors and a short description. */
public class SpeedrunModConfigsScreen extends Screen {
   private static final int ROW_HEIGHT = 44;
   private static final Map<String, Identifier> ICONS = new HashMap<>();
   private final Screen parent;
   private final List<Map.Entry<ModContainer, SpeedrunConfigScreenProvider>> mods = new ArrayList<>();
   private int page;

   public SpeedrunModConfigsScreen(Screen parent) {
      super(TextUtil.translatable("speedrunapi.gui.config.title"));
      this.parent = parent;
      this.mods.addAll(SpeedrunConfigAPI.getModConfigScreenProviders().entrySet());
   }

   private int pageSize() {
      return Math.max(1, (this.height - (ROW_HEIGHT + 32)) / ROW_HEIGHT);
   }

   private int pages() {
      return Math.max(1, (this.mods.size() + this.pageSize() - 1) / this.pageSize());
   }

   @Override
   protected void init() {
      int size = this.pageSize();
      int pages = this.pages();
      this.page = Math.max(0, Math.min(this.page, pages - 1));
      int start = this.page * size;
      int rowWidth = Math.min(420, this.width - 40);
      int left = this.width / 2 - rowWidth / 2;

      for (int i = 0; i < size && start + i < this.mods.size(); i++) {
         Map.Entry<ModContainer, SpeedrunConfigScreenProvider> entry = this.mods.get(start + i);
         int y = 34 + i * ROW_HEIGHT;
         this.addRenderableWidget(
            Button.builder(Component.empty(), button -> this.minecraft.gui.setScreen(entry.getValue().createConfigScreen(this)))
               .bounds(left, y, rowWidth, ROW_HEIGHT - 4)
               .build()
         ).setAlpha(0.0F);
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), button -> this.minecraft.gui.setScreen(this.parent))
            .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
            .build()
      );

      if (pages > 1) {
         this.addRenderableWidget(
            Button.builder(Component.literal("<"), button -> {
               this.page--;
               this.rebuildWidgets();
            }).bounds(this.width / 2 - 232, this.height - 27, 20, 20).build()
         ).active = this.page > 0;
         this.addRenderableWidget(
            Button.builder(Component.literal(">"), button -> {
               this.page++;
               this.rebuildWidgets();
            }).bounds(this.width / 2 + 212, this.height - 27, 20, 20).build()
         ).active = this.page < pages - 1;
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      graphics.centeredText(this.font, this.title, this.width / 2, 10, -1);

      int size = this.pageSize();
      int start = this.page * size;
      int rowWidth = Math.min(420, this.width - 40);
      int left = this.width / 2 - rowWidth / 2;

      for (int i = 0; i < size && start + i < this.mods.size(); i++) {
         ModContainer mod = this.mods.get(start + i).getKey();
         int y = 34 + i * ROW_HEIGHT;
         boolean hovered = mouseX >= left && mouseX <= left + rowWidth && mouseY >= y && mouseY <= y + ROW_HEIGHT - 4;

         if (hovered) {
            graphics.fill(left, y, left + rowWidth, y + ROW_HEIGHT - 4, -1601138544);
         }

         Identifier icon = icon(mod);
         int iconX = left + 4;

         if (icon != null) {
            graphics.blit(icon, iconX, y + 2, iconX + 32, y + 34, 0.0F, 1.0F, 0.0F, 1.0F);
         } else {
            graphics.fill(iconX, y + 2, iconX + 32, y + 34, 0xFF303030);
         }

         String name = mod.getMetadata().getName();
         String version = mod.getMetadata().getVersion().getFriendlyString().split("\\+")[0];
         graphics.text(this.font, name + " " + version, left + 42, y + 4, 0xFFFFFFFF);

         List<String> authors = new ArrayList<>();
         mod.getMetadata().getAuthors().forEach(person -> authors.add(person.getName()));

         if (!authors.isEmpty()) {
            String by = " by " + String.join(", ", authors);
            graphics.text(this.font, by, left + 42 + this.font.width(name + " " + version) + 4, y + 4, 0xFFA0A0A0);
         }

         List<String> lines = wrap(mod.getMetadata().getDescription(), rowWidth - 48, 2);

         for (int line = 0; line < lines.size(); line++) {
            graphics.text(this.font, lines.get(line), left + 42, y + 16 + line * 10, 0xFFB0B0B0);
         }
      }
   }

   private List<String> wrap(String text, int maxWidth, int maxLines) {
      List<String> lines = new ArrayList<>();
      StringBuilder current = new StringBuilder();

      for (String word : text.split(" ")) {
         String candidate = current.isEmpty() ? word : current + " " + word;

         if (this.font.width(candidate) > maxWidth && !current.isEmpty()) {
            lines.add(current.toString());
            current = new StringBuilder(word);

            if (lines.size() == maxLines) {
               return lines;
            }
         } else {
            current = new StringBuilder(candidate);
         }
      }

      if (!current.isEmpty() && lines.size() < maxLines) {
         lines.add(current.toString());
      }

      return lines;
   }

   private static Identifier icon(ModContainer mod) {
      String id = mod.getMetadata().getId();
      return ICONS.computeIfAbsent(id, ignored -> {
         try {
            Path path = mod.findPath("assets/" + id + "/icon.png").orElse(null);

            if (path == null) {
               return null;
            }

            try (InputStream in = Files.newInputStream(path)) {
               var image = com.mojang.blaze3d.platform.NativeImage.read(in);
               var texture = new DynamicTexture(() -> "speedrunapi icon " + id, image);
               Identifier textureId = Identifier.fromNamespaceAndPath("speedrunapi", "mods/" + id + "/icon");
               Minecraft.getInstance().getTextureManager().register(textureId, texture);
               return textureId;
            }
         } catch (Throwable t) {
            return null;
         }
      });
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
