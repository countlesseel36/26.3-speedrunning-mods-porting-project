package me.contaria.seedqueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.input.KeyEvent;

/**
 * Wall key bindings with a primary key, secondary (combination) keys and blocking keys, matching the
 * 1.16.1/1.21.4 keybindings screen. A binding only triggers when the primary key (plus any secondary
 * keys) is pressed and no blocking key is held.
 */
public final class WallKeybindings {
   public enum Action {
      PLAY,
      LOCK,
      RESET,
      RESET_ALL,
      FOCUS_RESET,
      SCHEDULE_JOIN,
      SCHEDULE_ALL,
      RESET_COLUMN,
      RESET_ROW,
      PLAY_NEXT_LOCK,
      START_BENCHMARK,
      CANCEL_BENCHMARK,
      QUIT
   }

   public static final int UNKNOWN = -1;

   public static final class Binding {
      public int primary;
      public final List<Integer> secondary = new ArrayList<>();
      public final List<Integer> blocking = new ArrayList<>();

      Binding(int primary) {
         this.primary = primary;
      }
   }

   private static final Map<Action, Binding> DEFAULTS = new EnumMap<>(Action.class);
   private static final Map<Action, Binding> BINDINGS = new EnumMap<>(Action.class);

   static {
      DEFAULTS.put(Action.PLAY, new Binding(InputConstants.KEY_R));
      DEFAULTS.put(Action.LOCK, new Binding(InputConstants.KEY_L));
      DEFAULTS.put(Action.RESET, new Binding(InputConstants.KEY_E));
      DEFAULTS.put(Action.RESET_ALL, new Binding(InputConstants.KEY_T));
      DEFAULTS.put(Action.FOCUS_RESET, new Binding(InputConstants.KEY_F));
      DEFAULTS.put(Action.SCHEDULE_JOIN, new Binding(InputConstants.KEY_G));
      DEFAULTS.put(Action.SCHEDULE_ALL, new Binding(InputConstants.KEY_H));
      DEFAULTS.put(Action.RESET_COLUMN, new Binding(InputConstants.KEY_K));
      DEFAULTS.put(Action.RESET_ROW, new Binding(InputConstants.KEY_J));
      DEFAULTS.put(Action.PLAY_NEXT_LOCK, new Binding(InputConstants.KEY_M));
      DEFAULTS.put(Action.START_BENCHMARK, new Binding(InputConstants.KEY_B));
      DEFAULTS.put(Action.CANCEL_BENCHMARK, new Binding(InputConstants.KEY_N));
      DEFAULTS.put(Action.QUIT, new Binding(InputConstants.KEY_ESCAPE));
      resetDefaults();
      // 1.21.4 default for "Play Next Lock" is Shift + M.
      BINDINGS.get(Action.PLAY_NEXT_LOCK).secondary.add(InputConstants.KEY_LSHIFT);
   }

   private WallKeybindings() {
   }

   public static Binding binding(Action action) {
      return BINDINGS.get(action);
   }

   public static int key(Action action) {
      return BINDINGS.get(action).primary;
   }

   public static void setPrimary(Action action, int key) {
      BINDINGS.get(action).primary = key;
   }

   public static void setSecondary(Action action, List<Integer> keys) {
      BINDINGS.get(action).secondary.clear();
      BINDINGS.get(action).secondary.addAll(keys);
   }

   public static void setBlocking(Action action, List<Integer> keys) {
      BINDINGS.get(action).blocking.clear();
      BINDINGS.get(action).blocking.addAll(keys);
   }

   public static void resetDefaults() {
      BINDINGS.clear();

      for (Map.Entry<Action, Binding> entry : DEFAULTS.entrySet()) {
         BINDINGS.put(entry.getKey(), new Binding(entry.getValue().primary));
      }
   }

   /** Returns the action this key event triggers, checking secondary and blocking keys. */
   public static Action match(KeyEvent event) {
      for (Map.Entry<Action, Binding> entry : BINDINGS.entrySet()) {
         Binding binding = entry.getValue();

         if (binding.primary == UNKNOWN || binding.primary != event.key()) {
            continue;
         }

         boolean down = true;

         for (int key : binding.secondary) {
            if (!InputConstants.isKeyDown(key)) {
               down = false;
               break;
            }
         }

         if (!down) {
            continue;
         }

         for (int key : binding.blocking) {
            if (InputConstants.isKeyDown(key)) {
               down = false;
               break;
            }
         }

         if (down) {
            return entry.getKey();
         }
      }

      return null;
   }

   public static String translationKey(Action action) {
      return switch (action) {
         case PLAY -> "seedqueue.key.play";
         case LOCK -> "seedqueue.key.lock";
         case RESET -> "seedqueue.key.reset";
         case RESET_ALL -> "seedqueue.key.resetAll";
         case FOCUS_RESET -> "seedqueue.key.focusReset";
         case SCHEDULE_JOIN -> "seedqueue.key.scheduleJoin";
         case SCHEDULE_ALL -> "seedqueue.key.scheduleAll";
         case RESET_COLUMN -> "seedqueue.key.resetColumn";
         case RESET_ROW -> "seedqueue.key.resetRow";
         case PLAY_NEXT_LOCK -> "seedqueue.key.playNextLock";
         case START_BENCHMARK -> "seedqueue.key.startBenchmark";
         case CANCEL_BENCHMARK -> "seedqueue.key.cancelBenchmark";
         case QUIT -> "seedqueue.key.quitWall";
      };
   }

   public static String display(int key) {
      if (key == UNKNOWN) {
         return "?";
      }

      return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
   }

   public static void load(JsonObject json) {
      if (json == null) {
         return;
      }

      for (Action action : Action.values()) {
         JsonElement element = json.get(action.name().toLowerCase(java.util.Locale.ROOT));

         if (element == null) {
            continue;
         }

         Binding binding = BINDINGS.get(action);
         binding.secondary.clear();
         binding.blocking.clear();

         if (element.isJsonPrimitive()) {
            binding.primary = element.getAsInt();
         } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            binding.primary = object.has("primary") ? object.get("primary").getAsInt() : UNKNOWN;

            if (object.has("secondary")) {
               for (JsonElement key : object.getAsJsonArray("secondary")) {
                  binding.secondary.add(key.getAsInt());
               }
            }

            if (object.has("blocking")) {
               for (JsonElement key : object.getAsJsonArray("blocking")) {
                  binding.blocking.add(key.getAsInt());
               }
            }
         }
      }
   }

   public static JsonObject save() {
      JsonObject json = new JsonObject();

      for (Action action : Action.values()) {
         Binding binding = BINDINGS.get(action);
         String id = action.name().toLowerCase(java.util.Locale.ROOT);

         if (binding.secondary.isEmpty() && binding.blocking.isEmpty()) {
            json.addProperty(id, binding.primary);
         } else {
            JsonObject object = new JsonObject();
            object.addProperty("primary", binding.primary);
            JsonArray secondary = new JsonArray();

            for (int key : binding.secondary) {
               secondary.add(new JsonPrimitive(key));
            }

            object.add("secondary", secondary);
            JsonArray blocking = new JsonArray();

            for (int key : binding.blocking) {
               blocking.add(new JsonPrimitive(key));
            }

            object.add("blocking", blocking);
            json.add(id, object);
         }
      }

      return json;
   }
}
