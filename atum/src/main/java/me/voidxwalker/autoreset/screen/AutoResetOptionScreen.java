package me.voidxwalker.autoreset.screen;

import java.util.List;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Small settings screen: seed, difficulty, world type, structures and bonus chest. */
public class AutoResetOptionScreen extends Screen {
   private final @Nullable Screen parent;
   private EditBox seedField;
   private String seed;
   private int difficulty;
   private int generatorType;
   private boolean structures;
   private boolean bonusChest;

   public AutoResetOptionScreen(@Nullable Screen parent) {
      super(Atum.text("menu.autoresetTitle", "Autoreset Options"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.seed = Atum.seed == null ? "" : Atum.seed;
      this.difficulty = Atum.difficulty;
      this.generatorType = Atum.generatorType;
      this.structures = Atum.structures;
      this.bonusChest = Atum.bonusChest;

      int centerX = this.width / 2;

      this.seedField = new EditBox(this.font, centerX - 100, 60, 200, 20, Atum.text("menu.enterSeed", "Seed (leave empty for a random seed)"));
      this.seedField.setValue(this.seed);
      this.seedField.setResponder(value -> this.seed = value);
      this.addRenderableWidget(this.seedField);

      this.addRenderableWidget(
         CycleButton.<Integer>builder(Atum::difficultyName, this.difficulty)
            .withValues(List.of(-1, 0, 1, 2, 3))
            .create(centerX - 155, 100, 150, 20, Component.translatable("options.difficulty"), (button, value) -> this.difficulty = value)
      );

      this.addRenderableWidget(
         CycleButton.<Integer>builder(Atum::generatorName, this.generatorType)
            .withValues(List.of(0, 1, 2, 3, 4))
            .create(centerX + 5, 100, 150, 20, Component.translatable("selectWorld.mapType"), (button, value) -> this.generatorType = value)
      );

      this.addRenderableWidget(
         CycleButton.onOffBuilder(this.structures)
            .create(centerX - 155, 130, 150, 20, Component.translatable("selectWorld.mapFeatures"), (button, value) -> this.structures = value)
      );

      this.addRenderableWidget(
         CycleButton.onOffBuilder(this.bonusChest)
            .create(centerX + 5, 130, 150, 20, Component.translatable("selectWorld.bonusItems"), (button, value) -> this.bonusChest = value)
      );

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), button -> {
            Atum.seed = this.seed;
            Atum.difficulty = this.difficulty;
            Atum.generatorType = this.generatorType;
            Atum.structures = this.structures;
            Atum.bonusChest = this.bonusChest;
            Atum.save();
            this.minecraft.gui.setScreen(this.parent);
         }).bounds(centerX - 155, this.height - 28, 150, 20).build()
      );

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.cancel"), button -> this.minecraft.gui.setScreen(this.parent))
            .bounds(centerX + 5, this.height - 28, 150, 20)
            .build()
      );
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      graphics.centeredText(this.font, this.title, this.width / 2, 20, -1);
      graphics.centeredText(this.font, Atum.text("menu.enterSeed", "Seed (leave empty for a random seed)"), this.width / 2, 45, -6250336);
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
