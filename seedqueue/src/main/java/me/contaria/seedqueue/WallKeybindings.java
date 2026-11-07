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
      BACK_TO_WALL,
      START_BENCHMARK,
      CANCEL_BENCHMARK,
      QUIT
   }

   public static final int UNKNOWN = -1;
   /** Mouse bindings are encoded as -1000 - button so they never collide with keyboard codes. */
   public static final int MOUSE_BASE = -1000;

   public static int mouseKey(int button) {
      return MOUSE_BASE - button;
   }

   public static boolean isMouse(int key) {
      return key <= MOUSE_BASE;
   }

   public static int mouseButton(int key) {
      return MOUSE_BASE - key;
   }

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
      DEFAULTS.put(Action.BACK_TO_WALL, new Binding(InputConstants.KEY_F6));
      DEFAULTS.put(Action.START_BENCHMARK, new Binding(InputConstants.KEY_B));
      DEFAULTS.put(Action.CANCEL_BENCHMARK, new Binding(InputConstants.KEY_N));
      // Escape closes the wall through the vanilla screen handling; the binding stays unbound.
      DEFAULTS.put(Action.QUIT, new Binding(InputConstants.KEY_ESCAPE));
      resetDefaults();
      // "Play Next Lock" is a single key (M). Combination keys were removed because the SDL input
      // path could not report modifier state, which made Shift + M silently dead.
      BINDINGS.get(Action.PLAY_NEXT_LOCK).secondary.clear();
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

   /**
    * Modifier keys through the GUI's own state. {@code InputConstants.isKeyDown} reads the window key
    * state, which does not report SDL modifiers, so Shift+M never saw Shift as held.
    */
   private static boolean modifierDown(int key) {
      // Input is SDL in 26.3, so modifiers have to be read from SDL (the game itself does this when it
      // builds MouseButtonInfo). InputConstants.isKeyDown only knows the keyboard state.
      int mods = org.lwjgl.sdl.SDLKeyboard.SDL_GetModState();

      if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
         return (mods & 0x0003) != 0;
      }

      if (key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL) {
         return (mods & 0x00C0) != 0;
      }

      if (key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT) {
         return (mods & 0x0300) != 0;
      }

      return false;
   }

   /** Compares stored and reported keys, ignoring the upper case shift produces ('M' vs 'm'). */
   private static boolean sameKey(int stored, int reported) {
      if (stored == reported) {
         return true;
      }

      char a = (char) stored;
      char b = (char) reported;
      return Character.isLetter(a) && Character.isLetter(b) && Character.toLowerCase(a) == Character.toLowerCase(b);
   }

   /**
    * Normalises a key event to the codes the bindings store.
    *
    * <p>{@code KeyEvent#key()} is an SDL scancode on 26.3, while {@code InputConstants.KEY_*} are GLFW
    * values, so a default such as F6 (295) never matched the raw event code. Bindings and comparisons
    * now both go through this conversion.</p>
    */
   public static int keyOf(KeyEvent event) {
      try {
         return InputConstants.getKey(event).getValue();
      } catch (Throwable t) {
         return event.key();
      }
   }

   /** Returns the action this key event triggers, checking secondary and blocking keys. */
   /** Returns the action bound to a normalised key code, or null. */
   public static Action matchCode(int code) {
      for (java.util.Map.Entry<Action, Binding> entry : BINDINGS.entrySet()) {
         Binding binding = entry.getValue();

         if (binding.primary == UNKNOWN || !sameKey(binding.primary, code)) {
            continue;
         }

         return entry.getKey();
      }

      return null;
   }

   public static Action match(KeyEvent event) {
      return matchCode(keyOf(event));
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
         case BACK_TO_WALL -> "seedqueue.key.backToWall";
         case START_BENCHMARK -> "seedqueue.key.startBenchmark";
         case CANCEL_BENCHMARK -> "seedqueue.key.cancelBenchmark";
         case QUIT -> "seedqueue.key.quitWall";
      };
   }

   public static String display(int key) {
      if (key == UNKNOWN) {
         // Same wording as the vanilla unbound state ("未指定" in Chinese).
         return net.minecraft.locale.Language.getInstance().getOrDefault("key.keyboard.unknown");
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
