package me.contaria.speedrunapi.config.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 配置界面的选项分组：分类桶、组标题/排序与显示名称。
 * 从 SpeedrunConfigScreen 拆分出来，行为保持不变。
 */
import static me.contaria.speedrunapi.config.screen.SpeedrunConfigScreen.*;

final class OptionGrouping {
   private OptionGrouping() {
   }

   static String categoryBucket(String category) {
      String value = category == null ? "" : category.toLowerCase(java.util.Locale.ROOT);

      if (value.contains("key.category") || value.contains("controls") || value.contains("key")) {
         return "zz.controls";
      }

      if (value.contains("sound") || value.contains("music")) {
         return "zz.sound";
      }

      if (value.contains("video") || value.contains("graphic") || value.contains("render")) {
         return "zz.video";
      }

      if (value.contains("chat")) {
         return "zz.chat";
      }

      if (value.contains("mouse") || value.contains("touch")) {
         return "zz.mouse";
      }

      if (value.contains("skin") || value.contains("model")) {
         return "zz.skin";
      }

      if (value.contains("debug") || value.contains("f3")) {
         return "zz.debug";
      }

      if (value.contains("accessib") || value.contains("narrat")) {
         return "zz.accessibility";
      }

      return "zz." + value;
   }

   static String groupLabel(String chinese, String key) {
      return net.minecraft.locale.Language.getInstance().getOrDefault(key);
   }

   static int optionPriority(SpeedrunOption<?> option) {
      String id = option.getID();

      if (id.equals("toggleStandardSettings")) {
         return -2;   // 放在最顶部
      }

      if (id.equals("toggleAll")) {
         return -1;
      }

      return 0;
   }

   static String groupKey(SpeedrunOption<?> option) {
      // Debug render toggles must be checked *before* the category mapping: they carry a vanilla
      // category (video/misc) whose bucket would otherwise win and split them away from the F3 entries.
      String debugId = option.getID().toLowerCase(java.util.Locale.ROOT);

      // 全局设置：置顶，包含"全部切换 / 使用 StandardSettings / 进世界时暂停 / 首次进世界暂停延迟"
      // Real ids from the diagnostics: toggleAll / toggleStandardSettings (there is no "useStandardSettings").
      // "进世界时暂停" / "首次进世界暂停延迟" 的真实 id 是 autoF3Esc / firstAutoF3EscDelay
      boolean joinPause = debugId.contains("autof3esc");
      boolean pieOrFov = debugId.contains("pie") || debugId.contains("fov");
      boolean globalToggle = debugId.equals("toggleall") || debugId.equals("togglestandardsettings")
         || joinPause;
      boolean worldJoinPause = !debugId.startsWith("key_") && !debugId.startsWith("key.")
         && debugId.contains("pause") && !debugId.contains("focus");

      // 进世界时暂停 / 首次进世界暂停延迟 → 进入世界时设置
      if (joinPause) {
         return "speedrunapi.group.onWorldJoin";
      }

      // "进世界时视场角"(fovOnWorldJoin) 属于进入世界时设置；"视场角"(fov) 留在全局设置
      if (debugId.contains("fov") && debugId.contains("worldjoin")) {
         return "speedrunapi.group.onWorldJoin";
      }

      if (globalToggle || worldJoinPause || pieOrFov) {
         return "speedrunapi.group.global";
      }

      // Key bindings come first: "key.debug.showHitboxes" / "...chunkBorders" are rebindable controls,
      // not debug toggles.
      if (debugId.startsWith("key_") || debugId.startsWith("key.")) {
         return "speedrunapi.group.controls";
      }

      // Requested grouping: FOV, server listing, Realms notifications, telemetry, pie directory and the
      // world-join pause options live in the misc group.
      if (debugId.contains("serverlisting") || debugId.contains("server_listing")
         || debugId.contains("realms") || debugId.contains("telemetry")) {
         return "speedrunapi.group.misc";
      }

      // Chunk fade belongs to video, not debug.
      if (debugId.contains("fade")) {
         return "speedrunapi.group.video";
      }

      if (debugId.contains("crosshair") || debugId.contains("hitbox") || debugId.contains("section")
         || debugId.contains("octree") || debugId.contains("border") || debugId.contains("visualize")
         || debugId.contains("debug.chunk") || debugId.contains("chunk_section")) {
         return "speedrunapi.group.debug";
      }

      String category = option.getCategory();

      if (category != null && !category.isBlank()) {
         String key = BUCKET_TITLES.get(categoryBucket(category));

         if (key == null) {
            key = BUCKET_TITLES.get(category);
         }

         if (key != null) {
            return key;
         }
      }

      String id = option.getID().toLowerCase(java.util.Locale.ROOT);

      if (id.startsWith("key_") || id.startsWith("key.")) return "speedrunapi.group.controls";
      if (id.contains("sound") || id.contains("music") || id.contains("volume")) return "speedrunapi.group.sound";
      if (id.contains("chat") || id.contains("narrat") || id.contains("notification")) return "speedrunapi.group.chat";
      if (id.contains("mouse") || id.contains("invert") || id.contains("sensitivity")) return "speedrunapi.group.mouse";
      if (id.contains("modelpart") || id.contains("cape") || id.contains("skin")) return "speedrunapi.group.skin";
      // Debug *render* toggles (crosshair / chunk borders / section overlays / hitboxes) belong next to
      // the F3 screen entries, not in video or misc.
      if (id.contains("debug") || id.contains("hitbox")
         || id.contains("crosshair") || id.contains("section") || id.contains("octree")
         || id.contains("border") || id.contains("overlay")) {
         return "speedrunapi.group.debug";
      }

      if (id.contains("fov") || id.contains("render") || id.contains("graphic") || id.contains("gui")
         || id.contains("gamma") || id.contains("cloud") || id.contains("particles")
         || id.contains("mipmap") || id.contains("vsync") || id.contains("fullscreen")
         || id.contains("anisotropy") || id.contains("fps") || id.contains("simulation")
         || id.contains("ao") || id.contains("biomeblend")) {
         return "speedrunapi.group.video";
      }

      return "speedrunapi.group.misc";
   }

   static String groupTitle(SpeedrunOption<?> option) {
      String category = option.getCategory();

      // Always go through the same key: a second code path produced the duplicated "调试选项" heading.
      return net.minecraft.locale.Language.getInstance().getOrDefault(groupKey(option));
   }

   static int groupIndex(SpeedrunOption<?> option) {
      int index = GROUP_ORDER.indexOf(groupKey(option));
      return index < 0 ? GROUP_ORDER.size() : index;
   }

   static String bucketTitle(String category) {
      if (category == null) {
         return "选项";
      }

      String bucket = categoryBucket(category);
      String key = BUCKET_TITLES.get(bucket);

      if (key != null) {
         return net.minecraft.locale.Language.getInstance().getOrDefault(key);
      }

      // Fall back to the known-category table, then to a readable form of the raw name.
      key = BUCKET_TITLES.get(category);

      if (key != null) {
         return net.minecraft.locale.Language.getInstance().getOrDefault(key);
      }

      return pretty(bucket.startsWith("zz.") ? bucket.substring(3) : bucket);
   }

   static int categoryIndex(String category) {
      if (category == null) {
         return CATEGORY_ORDER.size();
      }

      int index = CATEGORY_ORDER.indexOf(category);

      if (index >= 0) {
         return index;
      }

      // Unknown (vanilla) categories are grouped into stable buckets after the known ones.
      return CATEGORY_ORDER.size() + Math.abs(categoryBucket(category).hashCode() % 1000);
   }

   static String pretty(String id) {
      StringBuilder text = new StringBuilder();

      for (int i = 0; i < id.length(); i++) {
         char character = id.charAt(i);

         if (character == '_') {
            text.append(' ');
            continue;
         }

         if (i > 0 && Character.isUpperCase(character) && !Character.isUpperCase(id.charAt(i - 1))) {
            text.append(' ');
         }

         text.append(character);
      }

      String result = text.toString();
      return result.isEmpty() ? id : Character.toUpperCase(result.charAt(0)) + result.substring(1);
   }
}
