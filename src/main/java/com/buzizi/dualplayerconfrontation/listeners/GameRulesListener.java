package com.buzizi.dualplayerconfrontation.listeners;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.util.Keys;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * 竞技场全局规则：
 * 1) 玩家只受 岩浆/火焰/坠落/虚空 伤害（准备与结算阶段完全免疫）
 * 2) 任何爆炸只破坏方块，无伤害无冲击
 * 3) 玩家不可徒手破坏/放置方块、不可丢弃/拾取掉落物
 * 4) 对局中不可用传送/桶等离开竞技场
 * 5) 沙子等重力方块坠落前/落地时记录回滚所需方块状态
 */
public final class GameRulesListener implements Listener {

    private final DualPlayerConfrontation plugin;

    public GameRulesListener(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    private DuelGame gameOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player player) {
            return plugin.games().getGameByPlayer(player.getUniqueId());
        }
        return plugin.games().getGameByWorld(entity.getWorld());
    }

    // ---------- 伤害 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelGame game = gameOf(player);
        if (game == null) {
            return;
        }
        if (!game.isFighting()) {
            event.setCancelled(true);
            return;
        }
        Set<EntityDamageEvent.DamageCause> allowed = plugin.settings().allowedDamage();
        if (!allowed.contains(event.getCause())) {
            event.setCancelled(true);
        }
    }

    /**
     * 兜底：战斗阶段中属于“允许致死”的伤害若被其他插件（如 DeluxeHub 的世界保护）
     * 拦截，则强制死亡，保证回合不会被保护插件卡住。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.isCancelled()) {
            return;
        }
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null || !game.isFighting()) {
            return;
        }
        if (!plugin.settings().forceDeathOnCancelledLethalDamage()) {
            return;
        }
        if (!plugin.settings().allowedDamage().contains(event.getCause())) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && game.isFighting() && !player.isDead()
                    && player.getHealth() > 0) {
                player.setHealth(0.0);
            }
        });
    }

    // ---------- 爆炸：只破坏方块 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        DuelGame game = plugin.games().getGameByWorld(event.getLocation().getWorld());
        if (game == null) {
            return;
        }
        event.setCancelled(true);
        event.setYield(0f);
        Entity source = event.getEntity();
        PersistentDataContainer tag = source == null ? null : source.getPersistentDataContainer();
        String kind = Keys.getKind(tag);
        String ownerStr = Keys.getOwner(tag);
        java.util.UUID owner = ownerStr == null ? null : java.util.UUID.fromString(ownerStr);
        if (Keys.E_FIREBALL.equals(kind)) {
            // 火焰弹击中后不直接爆炸：原地生成“顺爆TNT”，由下方 E_FIRE_TNT 分支统一爆炸。
            // 若火球已被模式监听器移除（撞实体分支），此处跳过避免重复。
            if (source != null && !source.isValid()) {
                return;
            }
            if (source != null) {
                source.remove();
            }
            game.spawnFuseTnt(event.getLocation(), owner);
            return;
        }
        if (Keys.E_FIRE_TNT.equals(kind)) {
            // 火焰弹转化出的顺爆TNT：按配置半径破坏方块（只破坏方块、无伤害无冲击）
            game.explodeSphere(event.getLocation(), plugin.settings().fireballRadius(), owner);
            return;
        }
        // 苦力怕（含闪电苦力怕）：其爆炸方块可能受 mobGriefing 影响拿不到列表，
        // 直接按“原版半径”球形破坏：普通 3 格、闪电翻倍（闪电由 setPowered 或标签识别）
        if (source instanceof Creeper || Keys.E_CREEPER.equals(kind) || Keys.E_CHARGED.equals(kind)) {
            double radius = plugin.settings().creeperExplosionRadius();
            if ((source instanceof Creeper creeper && creeper.isPowered())
                    || Keys.E_CHARGED.equals(kind)) {
                radius *= 2;
            }
            game.explodeSphere(event.getLocation(), radius, owner);
            return;
        }
        // TNT羽毛球：爆炸半径大（默认5格），仍只破坏方块
        if (Keys.E_BADMINTON_TNT.equals(kind)) {
            game.explodeSphere(event.getLocation(), plugin.settings().badmintonExplosionRadius(), owner);
            return;
        }
        int broken = 0;
        for (Block b : event.blockList()) {
            if (game.destroyBlock(b)) {
                broken++;
            }
        }
        game.countBlocksTo(owner, broken);
        game.playExplosionFx(event.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        DuelGame game = plugin.games().getGameByWorld(event.getBlock().getWorld());
        if (game == null) {
            return;
        }
        event.setCancelled(true);
        event.setYield(0f);
        int broken = 0;
        for (Block b : event.blockList()) {
            if (game.destroyBlock(b)) {
                broken++;
            }
        }
        game.playExplosionFx(event.getBlock().getLocation().add(0.5, 0.5, 0.5));
    }

    // ---------- 火焰蔓延 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (plugin.settings().disableFireSpread()
                && plugin.games().getGameByWorld(event.getBlock().getWorld()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.settings().disableFireSpread()
                && plugin.games().getGameByWorld(event.getBlock().getWorld()) != null) {
            event.setCancelled(true);
        }
    }

    // ---------- 玩家不能破坏/放置方块 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        DuelGame game = plugin.games().getGameByPlayer(event.getPlayer().getUniqueId());
        if (game != null) {
            event.setCancelled(true);
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        DuelGame game = plugin.games().getGameByPlayer(event.getPlayer().getUniqueId());
        if (game != null) {
            event.setCancelled(true);
        }
    }

    // ---------- 掉落物：不能丢、不能捡、桶不可用 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        DuelGame game = plugin.games().getGameByPlayer(event.getPlayer().getUniqueId());
        if (game != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
            if (game != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.games().getGameByPlayer(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (plugin.games().getGameByPlayer(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    // ---------- 交互：仅在战斗阶段允许使用玩法道具 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!game.isFighting() || item == null || item.getType() == Material.AIR) {
            event.setCancelled(true);
            return;
        }
        PersistentDataContainer data = item.getItemMeta() == null ? null
                : item.getItemMeta().getPersistentDataContainer();
        String kind = Keys.getKind(data);
        if (kind == null) {
            event.setCancelled(true);
        }
        // 带标签的玩法道具放行，行为由玩法监听器处理
    }

    // ---------- 传送锁：对局中不能离开竞技场世界 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null || !game.isActive()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (!from.getWorld().equals(game.getWorld())) {
            return;
        }
        if (event.getCause() == TeleportCause.PLUGIN) {
            return;
        }
        if (!to.getWorld().equals(game.getWorld())) {
            event.setCancelled(true);
        }
    }

    // ---------- 准备阶段冻结（防走位/防坠落） ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game == null || game.getPhase() != DuelGame.Phase.PREPARE) {
            return;
        }
        if (!plugin.settings().freezeInPrepare()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            player.teleport(game.spawnOf(game.sideOf(player.getUniqueId())));
        }
    }

    // ---------- 重力方块回滚记录 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        DuelGame game = plugin.games().getGameByWorld(falling.getWorld());
        if (game == null) {
            return;
        }
        // 无论开始坠落(to=AIR)还是落地(to=实体方块)，都记录当前状态用于回滚
        game.changeLog().record(event.getBlock());
    }
}
