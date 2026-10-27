package com.buzizi.dualplayerconfrontation.manager;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 对局注册表：创建对局、查询某玩家/某世界所在的对局、统计并发数 */
public final class GameManager {

    private final DualPlayerConfrontation plugin;
    private final Map<UUID, DuelGame> byId = new ConcurrentHashMap<>();
    private final Map<UUID, DuelGame> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, DuelGame> byWorld = new ConcurrentHashMap<>();

    // 地图加载中的玩家，防止重复匹配/重复入队
    private final Set<UUID> incoming = ConcurrentHashMap.newKeySet();

    public GameManager(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    public int countActive() {
        return byId.size();
    }

    public List<DuelGame> allGames() {
        return List.copyOf(byId.values());
    }

    public DuelGame getGameById(UUID id) {
        return byId.get(id);
    }

    public DuelGame getGameByPlayer(UUID playerId) {
        return byPlayer.get(playerId);
    }

    public DuelGame getGameByWorld(World world) {
        return world == null ? null : byWorld.get(world.getName());
    }

    public boolean isInGame(UUID playerId) {
        return byPlayer.containsKey(playerId) || incoming.contains(playerId);
    }

    /** 由匹配队列发起的一局（自动选图）；地图不可用/满员时调用 onFail */
    public void launchQueuedMatch(Player a, Player b, Runnable onFail) {
        MapDefinition map = plugin.maps().pick();
        if (map == null) {
            plugin.messages().send(a, "queue.map-none");
            plugin.messages().send(b, "queue.map-none");
            onFail.run();
            return;
        }
        if (countActive() >= plugin.settings().effectiveMaxConcurrentGames()) {
            onFail.run();
            return;
        }
        startLoading(a, b, map, null, onFail);
    }

    /** 管理员强制开局（可选指定地图 id） */
    public void launchAdminMatch(Player a, Player b, MapDefinition map, CommandSender sender) {
        if (map == null) {
            map = plugin.maps().pick();
        }
        if (map == null) {
            plugin.messages().send(sender, "queue.map-none");
            return;
        }
        startLoading(a, b, map, sender, null);
    }

    private void startLoading(Player a, Player b, MapDefinition map, CommandSender sender, Runnable onFail) {
        if (a == null || b == null || !a.isOnline() || !b.isOnline()) {
            if (sender != null) {
                plugin.messages().send(sender, "admin.player-not-found", "player",
                        a == null ? "?" : a.getName());
            }
            if (onFail != null) {
                onFail.run();
            }
            return;
        }
        if (isInGame(a.getUniqueId()) || isInGame(b.getUniqueId())) {
            if (sender != null) {
                Player busy = isInGame(a.getUniqueId()) ? a : b;
                plugin.messages().send(sender, "admin.player-busy", "player", busy.getName());
            }
            if (onFail != null) {
                onFail.run();
            }
            return;
        }
        incoming.add(a.getUniqueId());
        incoming.add(b.getUniqueId());
        plugin.messages().send(a, "queue.found");
        plugin.messages().send(b, "queue.found");

        plugin.arenas().prepare(map, (arena, error) -> {
            incoming.remove(a.getUniqueId());
            incoming.remove(b.getUniqueId());
            if (arena == null) {
                plugin.messages().send(a, "queue.map-unavailable");
                plugin.messages().send(b, "queue.map-unavailable");
                if (sender != null) {
                    plugin.messages().send(sender, "queue.map-unavailable");
                }
                if (onFail != null) {
                    onFail.run();
                }
                return;
            }
            // 加载期间玩家可能离线或进入了其他对局
            if (!a.isOnline() || !b.isOnline() || isInGame(a.getUniqueId()) || isInGame(b.getUniqueId())) {
                plugin.arenas().dispose(arena);
                if (a.isOnline() && !isInGame(a.getUniqueId())) {
                    plugin.messages().send(a, "game-end.opponent-left");
                }
                return;
            }
            DuelGame game = new DuelGame(plugin, a, b, arena);
            register(game);
            game.begin();
        });
    }

    public void register(DuelGame game) {
        byId.put(game.getId(), game);
        for (UUID uuid : game.getPlayerIds()) {
            byPlayer.put(uuid, game);
        }
        byWorld.put(game.getWorld().getName(), game);
    }

    /** 对局结束/被取消后的反注册与副本回收 */
    public void unregisterAndDispose(DuelGame game) {
        byId.remove(game.getId());
        for (UUID uuid : game.getPlayerIds()) {
            byPlayer.remove(uuid, game);
        }
        if (game.getWorld() != null) {
            byWorld.remove(game.getWorld().getName(), game);
        }
        plugin.arenas().dispose(game.getArena());
    }

    /** 服务器关闭/插件卸载：立刻终止所有对局 */
    public void shutdownAll() {
        for (DuelGame game : List.copyOf(byId.values())) {
            game.abortShutdown();
        }
        byId.clear();
        byPlayer.clear();
        byWorld.clear();
        incoming.clear();
    }
}
