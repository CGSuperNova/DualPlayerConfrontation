package com.buzizi.dualplayerconfrontation.modes;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.game.GameMode;
import com.buzizi.dualplayerconfrontation.game.GameModeType;
import com.buzizi.dualplayerconfrontation.game.PlayerSide;
import com.buzizi.dualplayerconfrontation.util.Keys;
import com.buzizi.dualplayerconfrontation.util.Listeners;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * 玩法五：TNT羽毛球
 * 系统在双方塔顶轮流生成一颗TNT（约5秒后自爆，爆炸半径大，只破坏方块）。
 * 玩家持有小/大击退棒，攻击TNT可把它沿抛物线“抽”向对面；小棒劲短、大棒劲长。
 * 只有当前TNT爆炸（或消失）后，才会在另一座塔顶召唤下一颗。
 */
public final class TntBadmintonMode implements GameMode, Listener {

    private static final Random RANDOM = new Random();

    private final DualPlayerConfrontation plugin;
    private final DuelGame game;

    private UUID currentTnt;
    private PlayerSide nextSide;
    private int spawnDelay;

    public TntBadmintonMode(DualPlayerConfrontation plugin, DuelGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public GameModeType type() {
        return GameModeType.TNT_BADMINTON;
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
            p.getInventory().setItem(st.badmintonSlotSmall(), paddle(false));
            p.getInventory().setItem(st.badmintonSlotBig(), paddle(true));
            p.updateInventory();
        }
        nextSide = RANDOM.nextBoolean() ? PlayerSide.A : PlayerSide.B;
        currentTnt = null;
        spawnDelay = 0;
    }

    @Override
    public void tick(DuelGame duelGame) {
        // 当前TNT已爆炸/消失 → 延迟片刻后在另一座塔顶召唤下一颗
        boolean alive = currentTnt != null && game.getWorld().getEntity(currentTnt) != null
                && game.getWorld().getEntity(currentTnt).isValid();
        if (alive) {
            return;
        }
        currentTnt = null;
        spawnDelay++;
        if (spawnDelay >= 1) { // 每1秒检查，保证“爆炸后才刷下一颗”
            spawnDelay = 0;
            spawnTnt();
        }
    }

    private void spawnTnt() {
        PlayerSide side = nextSide;
        nextSide = side.opposite();
        Location loc = towerTop(side);
        if (loc == null) {
            return;
        }
        TNTPrimed tnt = game.getWorld().spawn(loc, TNTPrimed.class);
        tnt.setFuseTicks(Math.max(20, plugin.settings().badmintonFuseTicks()));
        Keys.setKind(tnt.getPersistentDataContainer(), Keys.E_BADMINTON_TNT);
        game.registerEntity(tnt);
        currentTnt = tnt.getUniqueId();
        game.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 0.8f);
        for (Player p : game.getPlayerIds().stream().map(game::playerOf)
                .filter(java.util.Objects::nonNull).toList()) {
            p.sendActionBar(Text.color("&cTNT已出现！&7(" + (side == PlayerSide.A ? "&aA塔" : "&cB塔") + "&7) 快用击退棒打向对面！"));
        }
    }

    /** 塔顶中心出生位置 */
    private Location towerTop(PlayerSide side) {
        var region = game.towerOf(side);
        if (region == null) {
            return null;
        }
        int cx = (region.getMinX() + region.getMaxX()) / 2;
        int cz = (region.getMinZ() + region.getMaxZ()) / 2;
        int topY = region.topBlock(game.getWorld(), cx, cz).getY();
        return new Location(game.getWorld(), cx + 0.5, topY + 1.0, cz + 0.5);
    }

    // ---------- 道具 ----------

    private ItemStack paddle(boolean big) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (big) {
                meta.setDisplayName(Text.color("&e大击退棒 &7(远)"));
                meta.setLore(List.of(Text.color("&7攻击TNT可把它远远抽向对面")));
                Keys.setKind(meta.getPersistentDataContainer(), Keys.I_PADDLE_BIG);
            } else {
                meta.setDisplayName(Text.color("&f小击退棒 &7(近)"));
                meta.setLore(List.of(Text.color("&7攻击TNT可把它向前击打")));
                Keys.setKind(meta.getPersistentDataContainer(), Keys.I_PADDLE_SMALL);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isPaddle(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
                && (Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_PADDLE_SMALL)
                || Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_PADDLE_BIG));
    }

    private boolean isBigPaddle(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
                && Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_PADDLE_BIG);
    }

    @Override
    public void onRoundEnd(DuelGame duelGame) {
        currentTnt = null;
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            for (ItemStack item : p.getInventory().getContents()) {
                if (isPaddle(item)) {
                    item.setAmount(0);
                }
            }
        }
        Listeners.unregister(this);
    }

    // ---------- 击打 ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        if (!Keys.hasKind(tnt.getPersistentDataContainer(), Keys.E_BADMINTON_TNT)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        DuelGame g = plugin.games().getGameByPlayer(player.getUniqueId());
        if (g != game || !game.isFighting()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isPaddle(hand)) {
            return; // 徒手不给击打距离，避免误操作
        }
        event.setCancelled(true);
        launch(player, tnt, isBigPaddle(hand));
    }

    private void launch(Player player, TNTPrimed tnt, boolean big) {
        Settings st = plugin.settings();
        double power = big ? st.badmintonBigPower() : st.badmintonSmallPower();

        Location eye = player.getEyeLocation();
        Location aim = eye.clone();
        // 抛物线：瞄准方向叠加仰角补偿（向斜上方抽击）
        aim.setPitch(Math.max(-75f, aim.getPitch() - (float) st.badmintonLaunchPitchOffset()));
        Vector dir = aim.getDirection().normalize();
        Vector velocity = dir.clone().multiply(power);
        tnt.setVelocity(velocity);
        // 击打时重新点燃？不：保持原引信计时即可，让玩家尽快击出
        game.getWorld().playSound(tnt.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.7f, 0.7f);
    }
}
