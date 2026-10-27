package com.buzizi.dualplayerconfrontation.listeners;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.util.Keys;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * 玩家生命周期：加入预热数据；退出处理（移除队列/统计落盘/对局判负）；
 * 死亡控制（清除掉落、隐藏原版死亡信息）；重生点送回塔顶；为玩家射出的箭打标记。
 */
public final class PlayerLifecycleListener implements Listener {

    private final DualPlayerConfrontation plugin;

    public PlayerLifecycleListener(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        plugin.stats().onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        plugin.queue().removeQuitter(id);
        DuelGame game = plugin.games().getGameByPlayer(id);
        if (game != null) {
            game.onPlayerQuit(player);
        }
        plugin.stats().onPlayerQuit(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null) {
            return;
        }
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        game.onPlayerDeath(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null || !game.isActive()) {
            return;
        }
        Location spawn = game.spawnOf(game.sideOf(player.getUniqueId()));
        event.setRespawnLocation(spawn);
    }

    /** 给玩家射出的箭标记归属（天降好运模式识别火焰箭） */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null || !game.isFighting()) {
            return;
        }
        Keys.setKind(projectile.getPersistentDataContainer(), Keys.E_ARROW);
        Keys.setOwner(projectile.getPersistentDataContainer(), player.getUniqueId());
    }
}
