package com.buzizi.dualplayerconfrontation.modes;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.game.GameMode;
import com.buzizi.dualplayerconfrontation.game.GameModeType;
import com.buzizi.dualplayerconfrontation.game.PlayerSide;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.util.BukkitCompat;
import com.buzizi.dualplayerconfrontation.util.Keys;
import com.buzizi.dualplayerconfrontation.util.Listeners;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * 玩法一：火焰弹乒乓球
 * 每隔 4~8 秒获得一枚火焰弹；右键发射，速度可调、便于徒手击打；
 * 火焰弹击中任何物体都会“原地生成顺爆TNT”来破坏方块（只破坏方块）；
 * 玩家徒手击中火球后，火球会朝玩家瞄准的方向飞去。
 */
public final class FireballMode implements GameMode, Listener {

    private static final Random RANDOM = new Random();

    private final DualPlayerConfrontation plugin;
    private final DuelGame game;
    private final Map<UUID, Integer> giveCountdown = new HashMap<>();
    /** 火球被击打的冷却（防止玩家与火球重叠时每tick重复触发瞄准） */
    private final Map<UUID, Long> lastPunch = new HashMap<>();
    /** 火球当前飞行向量（方向×速度），用于插件自管的“撞方块”判定 */
    private final Map<UUID, Vector> flight = new HashMap<>();
    private BukkitTask flightTask;

    public FireballMode(DualPlayerConfrontation plugin, DuelGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public GameModeType type() {
        return GameModeType.FIREBALL;
    }

    @Override
    public void onRoundStart(DuelGame duelGame) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (PlayerSide side : PlayerSide.values()) {
            Player player = game.playerBySide(side);
            if (player == null) {
                continue;
            }
            giveCountdown.put(player.getUniqueId(), randomDelay());
            grantOne(player);
        }
        flightTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFlight, 0L, 2L);
    }

    private void tickSupply() {
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            int left = giveCountdown.getOrDefault(p.getUniqueId(), 1) - 1;
            if (left <= 0) {
                grantOne(p);
                giveCountdown.put(p.getUniqueId(), randomDelay());
            } else {
                giveCountdown.put(p.getUniqueId(), left);
            }
        }
    }

    @Override
    public void tick(DuelGame duelGame) {
        tickSupply();
        // 清理已消失火球的击打冷却/飞行记录
        if (!lastPunch.isEmpty() || !flight.isEmpty()) {
            lastPunch.entrySet().removeIf(e -> {
                Entity entity = game.getWorld().getEntity(e.getKey());
                return entity == null || !entity.isValid();
            });
            flight.entrySet().removeIf(e -> {
                Entity entity = game.getWorld().getEntity(e.getKey());
                return entity == null || !entity.isValid();
            });
        }
    }

    private int randomDelay() {
        Settings st = plugin.settings();
        int min = Math.max(1, st.fireballGiveIntervalMin());
        int max = Math.max(min, st.fireballGiveIntervalMax());
        return min + RANDOM.nextInt(max - min + 1);
    }

    private void grantOne(Player player) {
        Settings st = plugin.settings();
        int cap = st.fireballCap();
        if (cap > 0 && countFireballs(player) >= cap) {
            return;
        }
        player.getInventory().addItem(fireballItem());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.8f);
    }

    private int countFireballs(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFireballItem(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private ItemStack fireballItem() {
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&c火焰弹 &7(右键发射)"));
            meta.setLore(java.util.List.of(Text.color("&7击中后会爆炸，破坏对方立足的方块！"),
                    Text.color("&7徒手可把飞来的火焰弹打回去")));
            Keys.setKind(meta.getPersistentDataContainer(), Keys.I_FIREBALL);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isFireballItem(ItemStack item) {
        return item != null && item.getType() == Material.FIRE_CHARGE && item.hasItemMeta()
                && Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_FIREBALL);
    }

    @Override
    public void onRoundEnd(DuelGame duelGame) {
        if (flightTask != null) {
            flightTask.cancel();
            flightTask = null;
        }
        lastPunch.clear();
        flight.clear();
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            for (ItemStack item : p.getInventory().getContents()) {
                if (isFireballItem(item)) {
                    item.setAmount(0);
                }
            }
        }
        Listeners.unregister(this);
    }

    // ---------- 事件 ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLaunch(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (gameOf(player) == null || !game.isFighting()) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isFireballItem(item)) {
            return;
        }
        event.setCancelled(true);
        launch(player);
    }

    private void launch(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFireballItem(item)) {
                item.setAmount(item.getAmount() - 1);
                break;
            }
        }
        Settings st = plugin.settings();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        Location spawnLoc = eye.clone().add(dir.clone().multiply(1.2));
        Fireball fb = game.getWorld().spawn(spawnLoc, Fireball.class);
        fb.setShooter(player);
        Vector norm = dir.clone().normalize();
        fb.setDirection(norm.clone());
        // 初速度（配置 fireball.speed，默认 0.9，便于拳头击打）
        fb.setVelocity(norm.clone().multiply(st.fireballSpeed()));
        Keys.setKind(fb.getPersistentDataContainer(), Keys.E_FIREBALL);
        Keys.setOwner(fb.getPersistentDataContainer(), player.getUniqueId());
        flight.put(fb.getUniqueId(), norm.clone().multiply(st.fireballSpeed()));
        game.registerEntity(fb);
        game.getWorld().playSound(spawnLoc, Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);
        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(player.getUniqueId()));
        profile.addFireballsShot(1);
    }

    /** 徒手击打火球：朝玩家瞄准的方向重新飞行（不再反向/消失） */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) {
            return;
        }
        if (!Keys.hasKind(fireball.getPersistentDataContainer(), Keys.E_FIREBALL)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (gameOf(player) == null || !game.isFighting()) {
            return;
        }
        // 冷却：防止火球贴脸时每个tick反复“瞄准”
        long now = System.currentTimeMillis();
        Long prev = lastPunch.get(fireball.getUniqueId());
        if (prev != null && now - prev < 250) {
            event.setCancelled(true);
            return;
        }
        lastPunch.put(fireball.getUniqueId(), now);

        event.setCancelled(true);
        Vector aim = player.getEyeLocation().getDirection().normalize();
        double speed = plugin.settings().fireballDeflectSpeed();
        Vector velocity = aim.clone().multiply(speed);
        fireball.setDirection(aim.clone());
        fireball.setVelocity(velocity);
        fireball.setShooter(player);
        flight.put(fireball.getUniqueId(), velocity.clone());
        // 归属切换为当前击打者
        Keys.setOwner(fireball.getPersistentDataContainer(), player.getUniqueId());
        game.getWorld().playSound(fireball.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 0.8f);
        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(player.getUniqueId()));
        profile.addFireballsHit(1);
    }

    /** 火球撞上实体（玩家/生物）：取消伤害并原地生成顺爆TNT */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onFireballHitsEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fireball fireball)) {
            return;
        }
        if (!Keys.hasKind(fireball.getPersistentDataContainer(), Keys.E_FIREBALL)) {
            return;
        }
        if (!fireball.isValid()) {
            return;
        }
        DuelGame g = plugin.games().getGameByWorld(fireball.getWorld());
        if (g != game || !game.isFighting()) {
            return;
        }
        event.setCancelled(true);
        impact(fireball, event.getEntity().getLocation());
    }

    /**
     * 火球飞行轮询：原版火球实体不会与方块碰撞，因此这里每 2 tick 沿飞行方向
     * 采样前方方块，命中即“击中”——原地生成顺爆TNT（与撞实体行为一致）。
     */
    private void tickFlight() {
        if (!game.isFighting()) {
            return;
        }
        var world = game.getWorld();
        for (Entity e : world.getEntities()) {
            if (!(e instanceof Fireball fb)) {
                continue;
            }
            if (!Keys.hasKind(fb.getPersistentDataContainer(), Keys.E_FIREBALL)) {
                continue;
            }
            if (!fb.isValid() || fb.isDead()) {
                flight.remove(fb.getUniqueId());
                continue;
            }
            Vector velocity = flight.get(fb.getUniqueId());
            if (velocity == null) {
                velocity = fb.getVelocity();
                if (velocity.lengthSquared() < 0.001) {
                    velocity = fb.getDirection().clone().normalize()
                            .multiply(plugin.settings().fireballSpeed());
                }
            }
            fb.setVelocity(velocity.clone());
            Vector norm = velocity.clone().normalize();
            double speed = Math.max(0.4, velocity.length());
            // 前视距离：一个tick位移 + 余量，避免高速下穿透
            double look = Math.min(10.0, speed + 1.2);
            Location loc = fb.getLocation();
            double step = 0.4;
            int steps = (int) Math.ceil(look / step);
            Block hit = null;
            for (int s = 1; s <= steps; s++) {
                Location point = loc.clone().add(norm.clone().multiply(step * s));
                Block b = world.getBlockAt(point);
                if (b.getType().isAir() || BukkitCompat.isLiquidType(b.getType())
                        || BukkitCompat.isUnyielding(b)) {
                    continue;
                }
                hit = b;
                break;
            }
            if (hit != null) {
                impact(fb, hit.getLocation().add(0.5, 0.5, 0.5));
            }
        }
    }

    /** 火球命中判定后统一处理：删除火球并在命中位置生成顺爆TNT */
    private void impact(Fireball fireball, Location at) {
        if (!fireball.isValid()) {
            return;
        }
        UUID owner = ownerOf(fireball);
        flight.remove(fireball.getUniqueId());
        fireball.remove();
        game.spawnFuseTnt(at, owner);
    }

    private UUID ownerOf(Fireball fireball) {
        String s = Keys.getOwner(fireball.getPersistentDataContainer());
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
