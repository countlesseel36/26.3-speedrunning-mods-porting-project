package me.contaria.speedrunapi.config.api.option;

import net.minecraft.network.chat.Component;

public interface EnumTextProvider<T extends Enum<T>> {
   Component getText(T value);
}
