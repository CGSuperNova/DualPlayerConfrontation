package com.buzizi.dualplayerconfrontation.manager;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.stats.StarManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** 匹配队列：满两人即配对；可选按星级相邻配对 */
public final class QueueManager {

    private final DualPlayerConfrontation plugin;
    private final Map<UUID, Long> queue = new LinkedHashMap<>();
    private BukkitTask tickTask;

    public QueueManager(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    public void startTicking() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public boolean isQueued(UUID uuid) {
        return queue.containsKey(uuid);
    }

    public int size() {
        return queue.size();
    }

    public boolean join(Player player) {
        UUID id = player.getUniqueId();
        if (queue.containsKey(id) || plugin.games().isInGame(id)) {
            return false;
        }
        queue.put(id, System.currentTimeMillis());
        return true;
    }

    public boolean leave(UUID uuid) {
        return queue.remove(uuid) != null;
    }

    public void removeQuitter(UUID uuid) {
        queue.remove(uuid);
    }

    public List<UUID> queuedPlayers() {
        return new ArrayList<>(queue.keySet());
    }

    private int starOf(UUID uuid) {
        PlayerProfile profile = plugin.stats().find(uuid).orElse(null);
        if (profile == null) {
            return plugin.settings().starInitial();
        }
        return StarManager.starFromXp(plugin.settings().starXpPerStar(), profile.getXp());
    }

    private List<UUID> ordered() {
        List<UUID> list = new ArrayList<>(queue.keySet());
        if (plugin.settings().queueStarMatchmaking() && list.size() > 2) {
            list.sort(Comparator
                    .comparingInt((UUID u) -> starOf(u))
                    .thenComparingLong(queue::get));
        } else {
            list.sort(Comparator.comparingLong(queue::get));
        }
        return list;
    }

    private void tick() {
        if (queue.size() < 2) {
            return;
        }
        int capacity = plugin.settings().effectiveMaxConcurrentGames() - plugin.games().countActive();
        if (capacity <= 0) {
            return;
        }
        List<UUID> list = ordered();
        int pairs = Math.min(capacity, list.size() / 2);
        for (int i = 0; i < pairs; i++) {
            UUID u1 = list.get(i * 2);
            UUID u2 = list.get(i * 2 + 1);
            queue.remove(u1);
            queue.remove(u2);
            Player p1 = Bukkit.getPlayer(u1);
            Player p2 = Bukkit.getPlayer(u2);
            if (p1 == null || p2 == null || !p1.isOnline() || !p2.isOnline()) {
                if (p1 != null && p1.isOnline()) {
                    queue.put(u1, System.currentTimeMillis());
                }
                if (p2 != null && p2.isOnline()) {
                    queue.put(u2, System.currentTimeMillis());
                }
                continue;
            }
            plugin.games().launchQueuedMatch(p1, p2, () -> {
                if (p1.isOnline() && !plugin.games().isInGame(p1.getUniqueId())) {
                    queue.put(u1, System.currentTimeMillis());
                }
                if (p2.isOnline() && !plugin.games().isInGame(p2.getUniqueId())) {
                    queue.put(u2, System.currentTimeMillis());
                }
            });
        }
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        queue.clear();
    }
}
