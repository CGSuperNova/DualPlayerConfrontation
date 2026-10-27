package com.buzizi.dualplayerconfrontation.modes;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.game.GameMode;
import com.buzizi.dualplayerconfrontation.game.GameModeType;
import com.buzizi.dualplayerconfrontation.game.PlayerSide;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.util.Keys;
import com.buzizi.dualplayerconfrontation.util.Listeners;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * 玩法四：超级潜影贝导弹
 * 手持道具右键以 0.2s/个 的速度发射导弹；导弹击中方块会破坏该方块；
 * 可左键打掉飞来的导弹；不同玩家发射的导弹彼此靠近会互相吸引并抵消；
 * 导弹不伤害玩家也没有冲击。
 */
public final class ShulkerMode implements GameMode, Listener {

    private final DualPlayerConfrontation plugin;
    private final DuelGame game;
    private final Map<UUID, Long> lastFire = new HashMap<>();
    private final Map<UUID, Vector> bulletDirections = new HashMap<>();
    private BukkitTask tickTask;

    public ShulkerMode(DualPlayerConfrontation plugin, DuelGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public GameModeType type() {
        return GameModeType.SHULKER;
    }

    @Override
    public void onRoundStart(DuelGame duelGame) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (PlayerSide side : PlayerSide.values()) {
            Player p = game.playerBySide(side);
            if (p == null) {
                continue;
            }
            p.getInventory().addItem(launcherItem());
            p.updateInventory();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBullets, 0L, 2L);
    }

    @Override
    public void onRoundEnd(DuelGame duelGame) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        bulletDirections.clear();
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            for (ItemStack item : p.getInventory().getContents()) {
                if (isLauncher(item)) {
                    item.setAmount(0);
                }
            }
        }
        Listeners.unregister(this);
    }

    // ---------- 道具 ----------

    private ItemStack launcherItem() {
        ItemStack item = new ItemStack(Material.SHULKER_SHELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&d潜影贝导弹发射器 &7(右键连发)"));
            meta.setLore(List.of(Text.color("&7导弹击中方块会将其破坏"),
                    Text.color("&7可左键打掉飞来的导弹，双方导弹会互相抵消")));
            Keys.setKind(meta.getPersistentDataContainer(), Keys.I_LAUNCHER);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isLauncher(ItemStack item) {
        return item != null && item.hasItemMeta()
                && Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_LAUNCHER);
    }

    // ---------- 射击 ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onShoot(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (gameOf(player) == null || !game.isFighting()) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isLauncher(item)) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(1, plugin.settings().shulkerFireIntervalTicks()) * 50L;
        long last = lastFire.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < intervalMs) {
            return;
        }
        lastFire.put(player.getUniqueId(), now);
        fire(player);
    }

    private void fire(Player player) {
        Settings st = plugin.settings();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location spawnLoc = eye.clone().add(dir.clone().multiply(0.8));
        ShulkerBullet bullet = game.getWorld().spawn(spawnLoc, ShulkerBullet.class);
        bullet.setTarget(null);
        bullet.setVelocity(dir.clone().multiply(st.shulkerBulletSpeed()));
        bullet.setShooter(player);
        Keys.setKind(bullet.getPersistentDataContainer(), Keys.E_BULLET);
        Keys.setOwner(bullet.getPersistentDataContainer(), player.getUniqueId());
        bulletDirections.put(bullet.getUniqueId(), dir.clone());
        game.registerEntity(bullet);
        game.getWorld().playSound(spawnLoc, Sound.ENTITY_SHULKER_SHOOT, 0.6f, 1.4f);
        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(player.getUniqueId()));
        profile.addBulletsShot(1);
    }

    // ---------- 导弹行为 ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ShulkerBullet bullet)) {
            return;
        }
        if (!Keys.hasKind(bullet.getPersistentDataContainer(), Keys.E_BULLET)) {
            return;
        }
        UUID owner = parseOwner(bullet);
        if (event.getHitEntity() instanceof Player) {
            if (plugin.settings().shulkerPassesPlayers()) {
                event.setCancelled(true); // 穿过玩家
            }
            return;
        }
        Block hitBlock = event.getHitBlock();
        if (hitBlock != null) {
            // 扩大摧毁：命中点约“一格半径”的区域一并被破坏（约7~19个方块，视地形而定）
            game.explodeSphere(hitBlock.getLocation().add(0.5, 0.5, 0.5),
                    plugin.settings().shulkerBulletDestroyRadius(), owner);
        }
    }

    /** 玩家左键打掉导弹 */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (!plugin.settings().shulkerPunchable()) {
            return;
        }
        if (!(event.getEntity() instanceof ShulkerBullet bullet)) {
            return;
        }
        if (!Keys.hasKind(bullet.getPersistentDataContainer(), Keys.E_BULLET)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        DuelGame g = plugin.games().getGameByPlayer(player.getUniqueId());
        if (g != game || !game.isFighting()) {
            return;
        }
        event.setCancelled(true);
        bullet.remove();
        bulletDirections.remove(bullet.getUniqueId());
        game.getWorld().playSound(bullet.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.6f);
        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(player.getUniqueId()));
        profile.addBulletsHit(1);
    }

    /** 导弹持续飞行 + 双方导弹互相吸引/抵消 */
    private void tickBullets() {
        if (!game.isFighting()) {
            return;
        }
        Settings st = plugin.settings();
        double speed = st.shulkerBulletSpeed();
        double attract = st.shulkerAttractRadius();
        double cancel = st.shulkerCancelRadius();

        List<ShulkerBullet> bullets = new ArrayList<>();
        for (Entity e : game.getWorld().getEntities()) {
            if (e instanceof ShulkerBullet b && b.isValid()
                    && Keys.hasKind(b.getPersistentDataContainer(), Keys.E_BULLET)) {
                bullets.add(b);
            }
        }
        for (int i = 0; i < bullets.size(); i++) {
            ShulkerBullet a = bullets.get(i);
            if (!a.isValid()) {
                continue;
            }
            UUID aOwner = parseOwner(a);
            if (aOwner == null) {
                continue;
            }
            Vector heading = bulletDirections.get(a.getUniqueId());
            if (heading == null) {
                Vector vel = a.getVelocity();
                heading = vel.lengthSquared() > 0.01 ? vel.normalize() : new Vector(0, 0, 1);
            }
            for (int j = i + 1; j < bullets.size(); j++) {
                ShulkerBullet b = bullets.get(j);
                if (!b.isValid()) {
                    continue;
                }
                UUID bOwner = parseOwner(b);
                if (bOwner == null || bOwner.equals(aOwner)) {
                    continue;
                }
                Vector delta = b.getLocation().toVector().subtract(a.getLocation().toVector());
                double dist = delta.length();
                if (dist < cancel) {
                    cancelPair(a, b);
                    continue;
                }
                if (dist < attract && dist > 0.01) {
                    Vector toward = delta.normalize();
                    double strength = Math.min(0.45, 0.05 + 0.4 * (1 - dist / attract));
                    heading = heading.normalize().multiply(1 - strength).add(toward.multiply(strength)).normalize();
                    bulletDirections.put(a.getUniqueId(), heading.clone());
                }
            }
            if (a.isValid()) {
                Vector norm = heading.clone().normalize();
                a.setVelocity(norm.multiply(speed));
                bulletDirections.put(a.getUniqueId(), norm.clone());
            }
        }
    }

    private void cancelPair(ShulkerBullet a, ShulkerBullet b) {
        Location mid = a.getLocation().clone().add(b.getLocation()).multiply(0.5);
        a.remove();
        b.remove();
        bulletDirections.remove(a.getUniqueId());
        bulletDirections.remove(b.getUniqueId());
        game.playExplosionFx(mid);
        game.getWorld().playSound(mid, Sound.ENTITY_SHULKER_BULLET_HURT, 1f, 0.6f);
    }

    private UUID parseOwner(ShulkerBullet bullet) {
        String s = Keys.getOwner(bullet.getPersistentDataContainer());
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private DuelGame gameOf(Player player) {
        DuelGame g = plugin.games().getGameByPlayer(player.getUniqueId());
        return g == game ? g : null;
    }
}
