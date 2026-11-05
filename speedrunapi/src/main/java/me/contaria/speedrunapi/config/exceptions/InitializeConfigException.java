package me.contaria.speedrunapi.config.exceptions;

public class InitializeConfigException extends SpeedrunConfigAPIException {
   public InitializeConfigException(String message) {
      super(message);
   }

   public InitializeConfigException(String message, Throwable cause) {
      super(message, cause);
   }
}
