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
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * 玩法二：天降好运
 * 地图上空生成许多悬浮的TNT；玩家获得附魔火矢+无限的弓与一根箭，
 * 用火焰箭射中天上的TNT即可将其激活坠落轰炸对方。
 * 每隔可配置秒数会补满天上被射掉的悬浮TNT，防止弹药耗尽。
 */
public final class TntRainMode implements GameMode, Listener {

    private static final Random RANDOM = new Random();

    private final DualPlayerConfrontation plugin;
    private final DuelGame game;
    private final List<Location> gridPositions = new ArrayList<>();
    private int regenCounter;

    public TntRainMode(DualPlayerConfrontation plugin, DuelGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public GameModeType type() {
        return GameModeType.TNT_RAIN;
    }

    @Override
    public void onRoundStart(DuelGame duelGame) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Settings st = plugin.settings();
        for (PlayerSide side : PlayerSide.values()) {
            Player p = game.playerBySide(side);
            if (p == null) {
                continue;
            }
            p.getInventory().setItem(st.tntBowSlot(), bowItem());
            p.getInventory().setItem(st.tntArrowSlot(), arrowItem());
            p.updateInventory();
        }
        spawnTntGrid();
    }

    @Override
    public void tick(DuelGame duelGame) {
        Settings st = plugin.settings();
        for (PlayerSide side : PlayerSide.values()) {
            Player p = game.playerBySide(side);
            if (p == null) {
                continue;
            }
            boolean hasArrow = false;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() == Material.ARROW) {
                    hasArrow = true;
                    break;
                }
            }
            if (!hasArrow) {
                p.getInventory().setItem(st.tntArrowSlot(), arrowItem());
            }
        }
        // 周期性补货悬浮TNT
        int interval = st.tntRegenIntervalSeconds();
        if (interval > 0) {
            regenCounter++;
            if (regenCounter >= interval) {
                regenCounter = 0;
                refillTntGrid();
            }
        }
    }

    /** 把天上被消耗掉的悬浮TNT补回来（只补空缺位置） */
    private void refillTntGrid() {
        for (Location loc : gridPositions) {
            Block block = loc.getBlock();
            if (block.getType().isAir()) {
                game.changeLog().record(block);
                block.setType(Material.TNT, false);
            }
        }
        game.getWorld().playSound(game.getMap().getCenter().toLocation(game.getWorld()),
                Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.4f);
    }

    @Override
    public void onRoundEnd(DuelGame duelGame) {
        gridPositions.clear();
        regenCounter = 0;
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && (item.getType() == Material.BOW || item.getType() == Material.ARROW)) {
                    item.setAmount(0);
                }
            }
        }
        Listeners.unregister(this);
    }

    // ---------- 道具 ----------

    private ItemStack bowItem() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6火焰之弓 &7(射爆天上的TNT)"));
            // 用 key 取附魔以兼容不同版本名称（火矢/无限）
            org.bukkit.enchantments.Enchantment flame =
                    org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("flame"));
            org.bukkit.enchantments.Enchantment infinity =
                    org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("infinity"));
            if (flame != null) {
                meta.addEnchant(flame, 1, true);
            }
            if (infinity != null) {
                meta.addEnchant(infinity, 1, true);
            }
            Keys.setKind(meta.getPersistentDataContainer(), Keys.I_BOW);
            bow.setItemMeta(meta);
        }
        return bow;
    }

    private ItemStack arrowItem() {
        return new ItemStack(Material.ARROW, 1);
    }

    // ---------- 悬浮TNT网格 ----------

    private void spawnTntGrid() {
        Settings st = plugin.settings();
        int cx = game.getMap().getCenter().getBlockX();
        int cz = game.getMap().getCenter().getBlockZ();
        int topMax = Math.max(game.getMap().getTowerA() == null ? 0 : game.getMap().getTowerA().getMaxY(),
                game.getMap().getTowerB() == null ? 0 : game.getMap().getTowerB().getMaxY());
        int spacing = Math.max(2, st.tntSpacing());
        int cols = Math.max(1, st.tntColumns());
        int rows = Math.max(1, st.tntRows());

        int x0 = cx - (cols - 1) * spacing / 2;
        int z0 = cz - (rows - 1) * spacing / 2;
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                int yOffset = st.tntMinHeightOffset()
                        + RANDOM.nextInt(Math.max(1, st.tntMaxHeightOffset() - st.tntMinHeightOffset() + 1));
                placeFloatingTnt(x0 + i * spacing, topMax + yOffset, z0 + j * spacing);
            }
        }
        int extra = Math.max(0, st.tntExtraRandom());
        int spread = Math.max(2, cols * spacing / 2 + 4);
        for (int n = 0; n < extra; n++) {
            int x = cx - spread + RANDOM.nextInt(spread * 2 + 1);
            int z = cz - spread + RANDOM.nextInt(spread * 2 + 1);
            int y = topMax + st.tntMinHeightOffset()
                    + RANDOM.nextInt(Math.max(1, st.tntMaxHeightOffset() - st.tntMinHeightOffset() + 1));
            placeFloatingTnt(x, y, z);
        }
    }

    private void placeFloatingTnt(int x, int y, int z) {
        Block block = game.getWorld().getBlockAt(x, y, z);
        if (!block.getType().isAir()) {
            return;
        }
        game.changeLog().record(block); // 记录生成前状态，回合结束回滚自动清除
        block.setType(Material.TNT, false);
        gridPositions.add(block.getLocation());
    }

    // ---------- 箭触发 ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onArrowHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!Keys.hasKind(projectile.getPersistentDataContainer(), Keys.E_ARROW)) {
            return;
        }
        UUID owner = ownerOf(projectile);
        DuelGame g = owner == null ? null : plugin.games().getGameByPlayer(owner);
        if (g != game || !game.isFighting()) {
            return;
        }
        if (event.getHitEntity() instanceof Player) {
            if (plugin.settings().tntArrowPassesPlayers()) {
                event.setCancelled(true); // 箭穿过玩家（不伤人），继续飞行
            } else {
                projectile.remove();
            }
            return;
        }
        // 箭命中方块或非玩家实体：处理TNT并删除箭矢，避免“悬空箭”残留
        Block hit = event.getHitBlock();
        if (hit != null && hit.getType() == Material.TNT) {
            activateTnt(hit, owner);
        }
        projectile.remove();
    }

    private void activateTnt(Block block, UUID owner) {
        game.eraseBlock(block);
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        TNTPrimed tnt = game.getWorld().spawn(loc, TNTPrimed.class);
        tnt.setFuseTicks(Math.max(10, plugin.settings().tntFuseTicks()));
        tnt.setVelocity(new Vector(0, -0.2, 0));
        Keys.setKind(tnt.getPersistentDataContainer(), Keys.E_TNT);
        if (owner != null) {
            Keys.setOwner(tnt.getPersistentDataContainer(), owner);
        }
        game.registerEntity(tnt);
        game.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 1f, 0.7f);
        if (owner != null) {
            PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(owner));
            profile.addTntActivated(1);
        }
    }

    private UUID ownerOf(Projectile projectile) {
        String s = Keys.getOwner(projectile.getPersistentDataContainer());
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
