package me.contaria.seedqueue.wall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import me.contaria.seedqueue.wall.*;

public final class WallKeybindings {
   private static final Map<WallKeybindings.Action, WallKeybindings.Binding> DEFAULTS = new EnumMap<>(WallKeybindings.Action.class);
   private static final Map<WallKeybindings.Action, WallKeybindings.Binding> BINDINGS = new EnumMap<>(WallKeybindings.Action.class);

   public static int mouseKey(int count) {
      return -1000 - count;
   }

   public static boolean isMouse(int count) {
      return count <= -1000;
   }

   public static int mouseButton(int count) {
      return -1000 - count;
   }

   private WallKeybindings() {
   }

   public static WallKeybindings.Binding binding(WallKeybindings.Action wallKeybindingsAction) {
      return BINDINGS.get(wallKeybindingsAction);
   }

   public static int key(WallKeybindings.Action wallKeybindingsAction) {
      return BINDINGS.get(wallKeybindingsAction).primary;
   }

   public static void setPrimary(WallKeybindings.Action wallKeybindingsAction, int count) {
      BINDINGS.get(wallKeybindingsAction).primary = count;
   }

   public static void setSecondary(WallKeybindings.Action wallKeybindingsAction, List<Integer> list) {
      BINDINGS.get(wallKeybindingsAction).secondary.clear();
      BINDINGS.get(wallKeybindingsAction).secondary.addAll(list);
   }

   public static void setBlocking(WallKeybindings.Action wallKeybindingsAction, List<Integer> list) {
      BINDINGS.get(wallKeybindingsAction).blocking.clear();
      BINDINGS.get(wallKeybindingsAction).blocking.addAll(list);
   }

   public static void resetDefaults() {
      BINDINGS.clear();

      for (Entry entry : DEFAULTS.entrySet()) {
         BINDINGS.put((WallKeybindings.Action)entry.getKey(), new WallKeybindings.Binding(((WallKeybindings.Binding)entry.getValue()).primary));
      }
   }

   private static boolean sameKey(int count, int count2) {
      if (count == count2) {
         return true;
      } else {
         char first = (char)count;
         char second = (char)count2;
         return Character.isLetter(first) && Character.isLetter(second) && Character.toLowerCase(first) == Character.toLowerCase(second);
      }
   }

   public static int key(String text) {
      try {
         return InputConstants.getKey(text).getValue();
      } catch (Throwable error) {
         return -1;
      }
   }

   public static int keyOf(KeyEvent keyEvent) {
      try {
         return InputConstants.getKey(keyEvent).getValue();
      } catch (Throwable error) {
         return keyEvent.key();
      }
   }

   public static WallKeybindings.Action matchCode(int count) {
      for (Entry entry : BINDINGS.entrySet()) {
         WallKeybindings.Binding wallKeybindingsBinding = (WallKeybindings.Binding)entry.getValue();
         if (wallKeybindingsBinding.primary != -1 && sameKey(wallKeybindingsBinding.primary, count)) {
            return (WallKeybindings.Action)entry.getKey();
         }
      }

      return null;
   }

   public static WallKeybindings.Action match(KeyEvent keyEvent) {
      WallKeybindings.Action wallKeybindingsAction = matchCode(keyOf(keyEvent));
      return wallKeybindingsAction != null ? wallKeybindingsAction : matchCode(keyEvent.key());
   }

   public static String translationKey(WallKeybindings.Action wallKeybindingsAction) {
      return switch (wallKeybindingsAction) {
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

   public static String display(int count) {
      if (count == -1) {
         return Language.getInstance().getOrDefault("key.keyboard.unknown");
      } else if (isMouse(count)) {
         int count2 = mouseButton(count);

         return switch (count2) {
            case 0 -> Language.getInstance().getOrDefault("key.mouse.left");
            case 1 -> Language.getInstance().getOrDefault("key.mouse.right");
            case 2 -> Language.getInstance().getOrDefault("key.mouse.middle");
            default -> Language.getInstance().getOrDefault("key.mouse") + " " + (count2 + 1);
         };
      } else {
         return Type.KEYBOARD.getOrCreate(count).getDisplayName().getString();
      }
   }

   public static void load(JsonObject jsonObject) {
      if (jsonObject != null) {
         for (WallKeybindings.Action wallKeybindingsAction : WallKeybindings.Action.values()) {
            JsonElement jsonElement = jsonObject.get(wallKeybindingsAction.name().toLowerCase(Locale.ROOT));
            if (jsonElement != null) {
               WallKeybindings.Binding wallKeybindingsBinding = BINDINGS.get(wallKeybindingsAction);
               wallKeybindingsBinding.secondary.clear();
               wallKeybindingsBinding.blocking.clear();
               if (jsonElement.isJsonPrimitive()) {
                  wallKeybindingsBinding.primary = jsonElement.getAsInt();
               } else if (jsonElement.isJsonObject()) {
                  JsonObject jsonObject2 = jsonElement.getAsJsonObject();
                  wallKeybindingsBinding.primary = jsonObject2.has("primary") ? jsonObject2.get("primary").getAsInt() : -1;
                  if (jsonObject2.has("secondary")) {
                     for (JsonElement jsonElement2 : jsonObject2.getAsJsonArray("secondary")) {
                        wallKeybindingsBinding.secondary.add(jsonElement2.getAsInt());
                     }
                  }

                  if (jsonObject2.has("blocking")) {
                     for (JsonElement jsonElement3 : jsonObject2.getAsJsonArray("blocking")) {
                        wallKeybindingsBinding.blocking.add(jsonElement3.getAsInt());
                     }
                  }
               }
            }
         }
      }
   }

   public static JsonObject save() {
      JsonObject jsonObject = new JsonObject();

      for (WallKeybindings.Action wallKeybindingsAction : WallKeybindings.Action.values()) {
         WallKeybindings.Binding wallKeybindingsBinding = BINDINGS.get(wallKeybindingsAction);
         String text = wallKeybindingsAction.name().toLowerCase(Locale.ROOT);
         if (wallKeybindingsBinding.secondary.isEmpty() && wallKeybindingsBinding.blocking.isEmpty()) {
            jsonObject.addProperty(text, wallKeybindingsBinding.primary);
         } else {
            JsonObject jsonObject2 = new JsonObject();
            jsonObject2.addProperty("primary", wallKeybindingsBinding.primary);
            JsonArray jsonArray = new JsonArray();

            for (int i : wallKeybindingsBinding.secondary) {
               jsonArray.add(new JsonPrimitive(i));
            }

            jsonObject2.add("secondary", jsonArray);
            JsonArray jsonArray2 = new JsonArray();

            for (int j : wallKeybindingsBinding.blocking) {
               jsonArray2.add(new JsonPrimitive(j));
            }

            jsonObject2.add("blocking", jsonArray2);
            jsonObject.add(text, jsonObject2);
         }
      }

      return jsonObject;
   }

   static {
      DEFAULTS.put(WallKeybindings.Action.PLAY, new WallKeybindings.Binding(key("key.keyboard.r")));
      DEFAULTS.put(WallKeybindings.Action.LOCK, new WallKeybindings.Binding(key("key.keyboard.l")));
      DEFAULTS.put(WallKeybindings.Action.RESET, new WallKeybindings.Binding(key("key.keyboard.e")));
      DEFAULTS.put(WallKeybindings.Action.RESET_ALL, new WallKeybindings.Binding(key("key.keyboard.t")));
      DEFAULTS.put(WallKeybindings.Action.FOCUS_RESET, new WallKeybindings.Binding(key("key.keyboard.f")));
      DEFAULTS.put(WallKeybindings.Action.SCHEDULE_JOIN, new WallKeybindings.Binding(key("key.keyboard.g")));
      DEFAULTS.put(WallKeybindings.Action.SCHEDULE_ALL, new WallKeybindings.Binding(key("key.keyboard.h")));
      DEFAULTS.put(WallKeybindings.Action.RESET_COLUMN, new WallKeybindings.Binding(key("key.keyboard.k")));
      DEFAULTS.put(WallKeybindings.Action.RESET_ROW, new WallKeybindings.Binding(key("key.keyboard.j")));
      DEFAULTS.put(WallKeybindings.Action.PLAY_NEXT_LOCK, new WallKeybindings.Binding(key("key.keyboard.m")));
      DEFAULTS.put(WallKeybindings.Action.BACK_TO_WALL, new WallKeybindings.Binding(key("key.keyboard.f6")));
      DEFAULTS.put(WallKeybindings.Action.START_BENCHMARK, new WallKeybindings.Binding(key("key.keyboard.b")));
      DEFAULTS.put(WallKeybindings.Action.CANCEL_BENCHMARK, new WallKeybindings.Binding(key("key.keyboard.n")));
      DEFAULTS.put(WallKeybindings.Action.QUIT, new WallKeybindings.Binding(key("key.keyboard.escape")));
      resetDefaults();
      BINDINGS.get(WallKeybindings.Action.PLAY_NEXT_LOCK).secondary.clear();
   }

   public static enum Action {
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
      QUIT;
   }

   public static final class Binding {
      int primary;
      final List<Integer> secondary = new ArrayList<>();
      final List<Integer> blocking = new ArrayList<>();

      Binding(int count) {
         this.primary = count;
      }
   }
}
