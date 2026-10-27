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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * 玩法三：投掷苦力怕
 * 每隔 4~8 秒获得一只可投掷的苦力怕（10%概率为闪电苦力怕）；右键以抛物线投掷；
 * 另有 1/2/3 档力度调节道具。
 *
 * 苦力怕的所有行为与普通原版苦力怕完全一致：
 *  - 可被玩家攻击/击退（原版受伤会加速引爆）；
 *  - 靠近玩家（约3格）才进入自爆进程；
 *  - 唯一不同：其爆炸只破坏方块，不伤害玩家、没有冲击（由全局规则监听统一实现）。
 */
public final class CreeperMode implements GameMode, Listener {

    private static final Random RANDOM = new Random();

    private final DualPlayerConfrontation plugin;
    private final DuelGame game;

    private final Map<UUID, Integer> giveCountdown = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPower = new ConcurrentHashMap<>();

    public CreeperMode(DualPlayerConfrontation plugin, DuelGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public GameModeType type() {
        return GameModeType.CREEPER;
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
            playerPower.put(p.getUniqueId(), 1);
            giveCountdown.put(p.getUniqueId(), randomDelay());
            grantEgg(p);
            p.getInventory().setItem(st.creeperPowerSlot(), powerTool(1));
            p.updateInventory();
        }
    }

    @Override
    public void tick(DuelGame duelGame) {
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            int left = giveCountdown.getOrDefault(p.getUniqueId(), 1) - 1;
            if (left <= 0) {
                grantEgg(p);
                giveCountdown.put(p.getUniqueId(), randomDelay());
            } else {
                giveCountdown.put(p.getUniqueId(), left);
            }
        }
    }

    @Override
    public void onRoundEnd(DuelGame duelGame) {
        giveCountdown.clear();
        playerPower.clear();
        for (UUID id : game.getPlayerIds()) {
            Player p = game.playerOf(id);
            if (p == null) {
                continue;
            }
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && (isEgg(item) || isChargedEgg(item) || isPowerTool(item))) {
                    item.setAmount(0);
                }
            }
        }
        Listeners.unregister(this);
    }

    private int randomDelay() {
        Settings st = plugin.settings();
        int min = Math.max(1, st.creeperGiveIntervalMin());
        int max = Math.max(min, st.creeperGiveIntervalMax());
        return min + RANDOM.nextInt(max - min + 1);
    }

    // ---------- 道具 ----------

    private ItemStack eggItem(boolean charged) {
        ItemStack item = new ItemStack(Material.CREEPER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (charged) {
                meta.setDisplayName(Text.color("&e&l闪电苦力怕 &7(右键投掷)"));
                meta.setLore(List.of(Text.color("&7爆炸范围更大，行为与闪电苦力怕一致")));
            } else {
                meta.setDisplayName(Text.color("&a苦力怕 &7(右键投掷)"));
                meta.setLore(List.of(Text.color("&7右键把它扔向对方塔上")));
            }
            Keys.setKind(meta.getPersistentDataContainer(), charged ? Keys.I_CHARGED_EGG : Keys.I_EGG);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack powerTool(int power) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&f投掷力度: &e" + power + "档 &7(右键切换)"));
            meta.setLore(List.of(Text.color("&71档近 &72档中 &73档远")));
            Keys.setKind(meta.getPersistentDataContainer(), Keys.I_POWER);
            Keys.setPower(meta.getPersistentDataContainer(), power);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isEgg(ItemStack item) {
        return item != null && item.getType() == Material.CREEPER_SPAWN_EGG && item.hasItemMeta()
                && Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_EGG);
    }

    private boolean isChargedEgg(ItemStack item) {
        return item != null && item.getType() == Material.CREEPER_SPAWN_EGG && item.hasItemMeta()
                && Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_CHARGED_EGG);
    }

    private boolean isPowerTool(ItemStack item) {
        return item != null && item.getType() == Material.CLOCK && item.hasItemMeta()
                && Keys.hasKind(item.getItemMeta().getPersistentDataContainer(), Keys.I_POWER);
    }

    private void grantEgg(Player player) {
        Settings st = plugin.settings();
        boolean charged = RANDOM.nextInt(100) < st.creeperChargedChance();
        ItemStack egg = eggItem(charged);
        int slot = st.creeperEggSlot();
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && existing.getType() != Material.AIR) {
            if (isEgg(existing) && !charged) {
                existing.setAmount(Math.min(64, existing.getAmount() + 1));
                player.updateInventory();
                return;
            }
            if (isChargedEgg(existing) && charged) {
                existing.setAmount(Math.min(64, existing.getAmount() + 1));
                player.updateInventory();
                return;
            }
            int empty = player.getInventory().firstEmpty();
            if (empty >= 0) {
                player.getInventory().setItem(empty, egg);
            }
            return;
        }
        player.getInventory().setItem(slot, egg);
        player.updateInventory();
    }

    // ---------- 交互：切换力度 / 投掷苦力怕 ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (gameOf(player) == null || !game.isFighting()) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (isPowerTool(item)) {
            event.setCancelled(true);
            cyclePower(player);
        } else if (isEgg(item) || isChargedEgg(item)) {
            event.setCancelled(true);
            throwCreeper(player, item);
        }
    }

    private void cyclePower(Player player) {
        int power = (playerPower.getOrDefault(player.getUniqueId(), 1) % 3) + 1;
        playerPower.put(player.getUniqueId(), power);
        Settings st = plugin.settings();
        player.getInventory().setItem(st.creeperPowerSlot(), powerTool(power));
        player.updateInventory();
        player.sendActionBar(Text.color("&f投掷力度已切换为 &e" + power + "档"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private void throwCreeper(Player player, ItemStack egg) {
        boolean charged = isChargedEgg(egg);
        egg.setAmount(egg.getAmount() - 1);

        Settings st = plugin.settings();
        int power = Math.max(1, Math.min(3, playerPower.getOrDefault(player.getUniqueId(), 1)));
        double velocity = st.creeperPowerVelocity(power);

        Location eye = player.getEyeLocation();
        Location aim = eye.clone();
        aim.setPitch(Math.max(-75f, aim.getPitch() - (float) st.creeperLaunchPitchOffset()));
        Vector dir = aim.getDirection().normalize();

        Location spawnLoc = eye.clone().add(dir.clone().multiply(0.9));
        Creeper creeper = game.getWorld().spawn(spawnLoc, Creeper.class);
        creeper.setPowered(charged);
        creeper.setTarget(null);
        creeper.setVelocity(dir.clone().multiply(velocity * 1.2));
        Keys.setKind(creeper.getPersistentDataContainer(), charged ? Keys.E_CHARGED : Keys.E_CREEPER);
        Keys.setOwner(creeper.getPersistentDataContainer(), player.getUniqueId());
        // 苦力怕保持原版 AI：会被攻击击退、靠近玩家才自爆；只注册以便回合结束清理
        game.registerEntity(creeper);
        game.getWorld().playSound(spawnLoc, Sound.ENTITY_CREEPER_PRIMED, 0.4f, 1.4f);

        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(player.getUniqueId()));
        profile.addCreepersThrown(1);
        if (charged) {
            profile.addChargedCreepersThrown(1);
        }
    }

    private DuelGame gameOf(Player player) {
        DuelGame g = plugin.games().getGameByPlayer(player.getUniqueId());
        return g == game ? g : null;
    }
}
