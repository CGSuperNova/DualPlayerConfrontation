package com.buzizi.dualplayerconfrontation.config;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

/** 对 config.yml 的类型化封装，所有读取都有安全默认值 */
public final class Settings {

    private final DualPlayerConfrontation plugin;
    private FileConfiguration cfg;

    public Settings(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        cfg = plugin.getConfig();
    }

    // ---------- 基础 ----------
    public boolean debug() {
        return cfg.getBoolean("debug", false);
    }

    // ---------- 匹配/对局 ----------
    public int winScore() {
        return cfg.getInt("match.win-score", 7);
    }

    public int roundPrepareSeconds() {
        return cfg.getInt("match.round-prepare-seconds", 5);
    }

    public int roundEndSeconds() {
        return cfg.getInt("match.round-end-seconds", 3);
    }

    public int maxRoundTime() {
        return cfg.getInt("match.max-round-time", 120);
    }

    public int tieTntInterval() {
        return cfg.getInt("match.tie-tnt-interval", 5);
    }

    // ---------- 岩浆上升（超过 lava-start 秒后触发） ----------
    public int lavaStart() {
        return cfg.getInt("match.lava-start", 180);
    }

    public int lavaRiseIntervalMin() {
        return cfg.getInt("match.lava-rise-interval-min", 3);
    }

    public int lavaRiseIntervalMax() {
        return cfg.getInt("match.lava-rise-interval-max", 4);
    }

    public int lavaFillPadding() {
        return cfg.getInt("match.lava-fill-padding", 6);
    }

    public int queueMaxConcurrentGames() {
        return Math.max(1, cfg.getInt("match.queue.max-concurrent-games", 4));
    }

    public boolean queueStarMatchmaking() {
        return cfg.getBoolean("match.queue.star-matchmaking", true);
    }

    // ---------- 竞技场槽位（固定世界名便于 DeluxeHub 等按世界名加入黑名单/禁用列表） ----------
    public String arenaNamePrefix() {
        String p = cfg.getString("arena.name-prefix", "duel");
        return (p == null || p.isEmpty()) ? "duel" : p;
    }

    public int arenaSlots() {
        return Math.max(1, cfg.getInt("arena.slots", 8));
    }

    /** 实际并发上限 = min(队列并发, 竞技场槽位) */
    public int effectiveMaxConcurrentGames() {
        return Math.min(queueMaxConcurrentGames(), arenaSlots());
    }

    // ---------- 规则 ----------
    public Set<EntityDamageEvent.DamageCause> allowedDamage() {
        Set<DamageCause> set = EnumSet.noneOf(DamageCause.class);
        List<String> raw = cfg.getStringList("rules.allowed-damage");
        if (raw.isEmpty()) {
            raw = List.of("LAVA", "FIRE", "FIRE_TICK", "HOT_FLOOR", "FALL", "VOID");
        }
        for (String s : raw) {
            try {
                set.add(DamageCause.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("未知的伤害类型: " + s);
            }
        }
        return set;
    }

    public boolean foodLoss() {
        return cfg.getBoolean("rules.food-loss", false);
    }

    public boolean naturalRegen() {
        return cfg.getBoolean("rules.natural-regen", false);
    }

    public boolean explosionNoDamage() {
        return cfg.getBoolean("rules.explosion-no-damage", true);
    }

    public boolean explosionNoKnockback() {
        return cfg.getBoolean("rules.explosion-no-knockback", true);
    }

    public boolean disableFireSpread() {
        return cfg.getBoolean("rules.disable-fire-spread", true);
    }

    public int itemCleanupTicks() {
        return cfg.getInt("rules.item-cleanup-ticks", 20);
    }

    public boolean freezeInPrepare() {
        return cfg.getBoolean("rules.freeze-in-prepare", true);
    }

    /** 当 DeluxeHub 等插件取消了岩浆/坠落等致死伤害时，是否强制死亡以保证回合正常结束 */
    public boolean forceDeathOnCancelledLethalDamage() {
        return cfg.getBoolean("rules.force-death-on-cancelled-lethal-damage", true);
    }

    // ---------- 大厅/返回点 ----------
    public String lobbyExitWorld() {
        return cfg.getString("lobby.exit-world", "world");
    }

    public Location lobbyExitLocation() {
        String s = cfg.getString("lobby.exit-location", "");
        if (s == null || s.isEmpty()) {
            return null;
        }
        return com.buzizi.dualplayerconfrontation.util.BukkitCompat.parseLocation(s);
    }

    // ---------- 玩法一 火焰弹 ----------
    public int fireballRadius() {
        return cfg.getInt("fireball.radius", 3);
    }

    public int fireballGiveIntervalMin() {
        return cfg.getInt("fireball.give-interval-min", 4);
    }

    public int fireballGiveIntervalMax() {
        return cfg.getInt("fireball.give-interval-max", 8);
    }

    public double fireballSpeed() {
        return cfg.getDouble("fireball.speed", 0.9);
    }

    public int fireballCap() {
        return cfg.getInt("fireball.cap", 5);
    }

    public double fireballDeflectSpeed() {
        return cfg.getDouble("fireball.deflect-speed", 1.0);
    }

    // ---------- 玩法二 天降好运 ----------
    public int tntBowSlot() {
        return cfg.getInt("tnt-rain.bow-slot", 0);
    }

    public int tntArrowSlot() {
        return cfg.getInt("tnt-rain.arrow-slot", 1);
    }

    public int tntMinHeightOffset() {
        return cfg.getInt("tnt-rain.grid.min-height-offset", 6);
    }

    public int tntMaxHeightOffset() {
        return cfg.getInt("tnt-rain.grid.max-height-offset", 18);
    }

    public int tntSpacing() {
        return cfg.getInt("tnt-rain.grid.spacing", 4);
    }

    public int tntColumns() {
        return cfg.getInt("tnt-rain.grid.columns", 7);
    }

    public int tntRows() {
        return cfg.getInt("tnt-rain.grid.rows", 7);
    }

    public int tntExtraRandom() {
        return cfg.getInt("tnt-rain.grid.extra-random", 8);
    }

    public int tntFuseTicks() {
        return cfg.getInt("tnt-rain.fuse-ticks", 60);
    }

    /** 天降好运：每隔多少秒把天上被消耗的悬浮TNT补满（0=关闭补货） */
    public int tntRegenIntervalSeconds() {
        return cfg.getInt("tnt-rain.regen-interval-seconds", 30);
    }

    public boolean tntArrowPassesPlayers() {
        return cfg.getBoolean("tnt-rain.arrow-passes-players", true);
    }

    // ---------- 玩法三 投掷苦力怕 ----------
    public int creeperGiveIntervalMin() {
        return cfg.getInt("creeper.give-interval-min", 4);
    }

    public int creeperGiveIntervalMax() {
        return cfg.getInt("creeper.give-interval-max", 8);
    }

    public int creeperEggSlot() {
        return cfg.getInt("creeper.egg-slot", 7);
    }

    public int creeperPowerSlot() {
        return cfg.getInt("creeper.power-slot", 8);
    }

    public int creeperChargedChance() {
        return cfg.getInt("creeper.charged-chance", 10);
    }

    public double creeperPowerVelocity(int power) {
        List<Double> list = new ArrayList<>();
        for (Object o : cfg.getList("creeper.power-velocity", List.of(0.9, 1.3, 1.7))) {
            if (o instanceof Number n) {
                list.add(n.doubleValue());
            } else {
                try {
                    list.add(Double.parseDouble(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (list.isEmpty()) {
            list = List.of(0.9, 1.3, 1.7);
        }
        int idx = Math.max(0, Math.min(power - 1, list.size() - 1));
        return list.get(idx);
    }

    public double creeperLaunchPitchOffset() {
        return cfg.getDouble("creeper.launch-pitch-offset", 8.0);
    }

    public int creeperFuseOnLandTicks() {
        return cfg.getInt("creeper.fuse-on-land-ticks", 15);
    }

    public int creeperFuseProximityTicks() {
        return cfg.getInt("creeper.fuse-proximity-ticks", 30);
    }

    public double creeperExplosionRadius() {
        return cfg.getDouble("creeper.explosion-radius", 3.0);
    }

    // ---------- 玩法四 潜影贝导弹 ----------
    public int shulkerFireIntervalTicks() {
        return cfg.getInt("shulker.fire-interval-ticks", 4);
    }

    public double shulkerBulletSpeed() {
        return cfg.getDouble("shulker.bullet-speed", 1.4);
    }

    /** 潜影贝导弹命中时的摧毁半径（格）。约一格半径=7~19个方块 */
    public double shulkerBulletDestroyRadius() {
        return cfg.getDouble("shulker.destroy-radius", 1.2);
    }

    public double shulkerAttractRadius() {
        return cfg.getDouble("shulker.attract-radius", 10.0);
    }

    public double shulkerCancelRadius() {
        return cfg.getDouble("shulker.cancel-radius", 2.0);
    }

    public boolean shulkerPassesPlayers() {
        return cfg.getBoolean("shulker.passes-players", true);
    }

    public boolean shulkerPunchable() {
        return cfg.getBoolean("shulker.punchable", true);
    }

    // ---------- 玩法五 TNT羽毛球 ----------
    public int badmintonSlotSmall() {
        return cfg.getInt("tnt-badminton.paddle-slot", 0);
    }

    public int badmintonSlotBig() {
        return cfg.getInt("tnt-badminton.paddle-big-slot", 1);
    }

    public double badmintonSmallPower() {
        return cfg.getDouble("tnt-badminton.small-power", 0.9);
    }

    public double badmintonBigPower() {
        return cfg.getDouble("tnt-badminton.big-power", 1.6);
    }

    public double badmintonLaunchPitchOffset() {
        return cfg.getDouble("tnt-badminton.launch-pitch-offset", 20.0);
    }

    public int badmintonFuseTicks() {
        return cfg.getInt("tnt-badminton.fuse-ticks", 100);
    }

    public double badmintonExplosionRadius() {
        return cfg.getDouble("tnt-badminton.explosion-radius", 5.0);
    }

    // ---------- 统计 ----------
    public int statsAutosaveMinutes() {
        return cfg.getInt("stats.autosave-minutes", 5);
    }

    // ---------- 星级 ----------
    /** 各星区每升一星所需经验：<100, <500, <1000, >=1000 */
    public List<Integer> starXpPerStar() {
        List<Integer> list = new ArrayList<>();
        for (Object o : cfg.getList("stars.xp-per-star", List.of(50, 100, 200, 500))) {
            if (o instanceof Number n) {
                list.add(n.intValue());
            } else {
                try {
                    list.add(Integer.parseInt(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        while (list.size() < 4) {
            list.add(500);
        }
        return list.subList(0, 4);
    }

    public int starInitial() {
        return cfg.getInt("stars.initial-star", 1);
    }

    public String starSymbol() {
        return cfg.getString("stars.star-symbol", "⭐");
    }

    // ---------- 经验 ----------
    public double xpWinBase() {
        return cfg.getDouble("experience.win-base", 50.0);
    }

    public double xpWinBonus() {
        return cfg.getDouble("experience.win-bonus", 25.0);
    }

    public double xpLosePerRound() {
        return cfg.getDouble("experience.lose-per-round", 5.0);
    }

    public double xpAvgFloor() {
        return cfg.getDouble("experience.avg-time-floor", 1.0);
    }

    public boolean xpForfeitFullFormula() {
        return cfg.getBoolean("experience.forfeit-full-formula", true);
    }

    // ---------- 奖励 ----------
    public boolean rewardRunAsPlayer() {
        return "player".equalsIgnoreCase(cfg.getString("rewards.run-as", "console"));
    }

    public List<String> rewardWinCommands() {
        return cfg.getStringList("rewards.win");
    }

    public List<String> rewardLoseCommands() {
        return cfg.getStringList("rewards.lose");
    }

    // ---------- 记分板 ----------
    public boolean scoreboardEnabled() {
        return cfg.getBoolean("scoreboard.enabled", true);
    }

    public String scoreboardTitle() {
        return cfg.getString("scoreboard.title", "&6&l双军对峙");
    }

    public List<String> scoreboardLines() {
        return cfg.getStringList("scoreboard.lines");
    }
}
