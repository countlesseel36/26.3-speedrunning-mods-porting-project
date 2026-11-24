package me.contaria.seedqueue.render;
import me.contaria.seedqueue.SeedQueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import me.contaria.seedqueue.render.*;

public final class SodiumLevelCache {
   private static final Map<ClientLevel, Object> MANAGERS = new ConcurrentHashMap<>();
   private static final Set<ClientLevel> PREVIEW_LEVELS = ConcurrentHashMap.newKeySet();
   private static final Set<ClientLevel> PENDING_EVICT = ConcurrentHashMap.newKeySet();
   private static final ArrayDeque<ClientLevel> ORDER = new ArrayDeque<>();
   private static final ThreadLocal<Integer> THREAD_LIMIT = new ThreadLocal<>();

   private SodiumLevelCache() {
   }

   public static void markPreview(ClientLevel level) {
      if (level != null) {
         PREVIEW_LEVELS.add(level);
      }
   }

   public static void markPreview(ClientLevel level, BlockPos pos) {
      markPreview(level);
   }

   public static boolean isPreview(ClientLevel level) {
      return level != null && PREVIEW_LEVELS.contains(level);
   }

   public static int size() {
      return MANAGERS.size();
   }

   public static Object get(ClientLevel level) {
      return level == null ? null : MANAGERS.get(level);
   }

   public static void put(ClientLevel level, Object obj) {
      if (level != null && obj != null) {
         Object obj2 = MANAGERS.put(level, obj);
         if (obj2 == null) {
            ORDER.addLast(level);
         }

         while (ORDER.size() > 1) {
            ClientLevel level2 = ORDER.pollFirst();
            if (level2 != null && level2 != level) {
               Object obj3 = MANAGERS.remove(level2);
               PREVIEW_LEVELS.remove(level2);
               destroy(obj3);
            }
         }
      }
   }

   public static Object remove(ClientLevel level) {
      if (level == null) {
         return null;
      } else {
         ORDER.remove(level);
         return MANAGERS.remove(level);
      }
   }

   public static void evictLater(ClientLevel level) {
      if (level != null) {
         PENDING_EVICT.add(level);
         SeedQueue.LOGGER.info("[seedqueue] sodium: queued eviction for level {} (cache={})", System.identityHashCode(level), MANAGERS.size());
      }
   }

   public static void evictAllExcept(ClientLevel level) {
      for (ClientLevel level2 : new ArrayList<>(MANAGERS.keySet())) {
         if (level2 != level) {
            evictLater(level2);
         }
      }
   }

   public static void processPending() {
      if (!SeedQueue.shuttingDown && !PENDING_EVICT.isEmpty()) {
         int count = PENDING_EVICT.size() > 4 ? 2 : 1;

         for (int i = 0; i < count && !PENDING_EVICT.isEmpty(); i++) {
            destroyOne();
         }

         if (SeedQueue.shuttingDown || PENDING_EVICT.isEmpty()) {
            SeedQueue.LOGGER.info("[seedqueue] sodium: eviction queue drained");
         }
      }
   }

   private static void destroyOne() {
      ClientLevel level = PENDING_EVICT.iterator().next();
      PENDING_EVICT.remove(level);
      Object obj = remove(level);
      PREVIEW_LEVELS.remove(level);
      if (Boolean.getBoolean("seedqueue.sodiumLog")) {
         SeedQueue.LOGGER
            .info(
               "[seedqueue] sodium: evicting level {} manager={} (cache={}, pending={})",
               System.identityHashCode(level),
               System.identityHashCode(obj),
               MANAGERS.size(),
               PENDING_EVICT.size()
            );
      }

      destroy(obj);
      if (SeedQueue.shuttingDown || PENDING_EVICT.isEmpty()) {
         SeedQueue.LOGGER.info("[seedqueue] sodium: eviction queue drained");
      }
   }

   private static void destroy(Object obj) {
      if (obj instanceof RenderSectionManager renderSectionManager) {
         try {
            renderSectionManager.destroy();
         } catch (Throwable error) {
            SeedQueue.LOGGER.warn("[seedqueue] failed to destroy cached sodium section manager", error);
         }
      }
   }

   public static void sweep() {
      ClientLevel level = Minecraft.getInstance().level;
      PREVIEW_LEVELS.removeIf(previewLevel -> previewLevel != level && !MANAGERS.containsKey(previewLevel) && !PENDING_EVICT.contains(previewLevel));
   }

   public static void setThreadLimit(int count) {
      THREAD_LIMIT.set(count);
   }

   public static void clearThreadLimit() {
      THREAD_LIMIT.remove();
   }

   public static int threadLimit() {
      Integer integer = THREAD_LIMIT.get();
      return integer != null ? integer : 0;
   }
}
