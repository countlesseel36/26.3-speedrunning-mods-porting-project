package me.contaria.speedrunapi.config.api;

import net.fabricmc.loader.api.Version;

public interface SpeedrunConfigParsedMetadata {
   Version getApiVersion();

   Version getModVersion();

   int getDataVersion();
}
