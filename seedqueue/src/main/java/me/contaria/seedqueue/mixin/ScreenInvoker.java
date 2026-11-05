package me.contaria.seedqueue.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Screen#addRenderableWidget so other mixins can add widgets. */
@Mixin(Screen.class)
public interface ScreenInvoker {
   @Invoker("addRenderableWidget")
   <T extends GuiEventListener & Renderable & NarratableEntry> T seedqueue$addRenderableWidget(T widget);
}