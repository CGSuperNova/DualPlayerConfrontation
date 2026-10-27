package com.buzizi.dualplayerconfrontation.game;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import com.buzizi.dualplayerconfrontation.modes.CreeperMode;
import com.buzizi.dualplayerconfrontation.modes.FireballMode;
import com.buzizi.dualplayerconfrontation.modes.ShulkerMode;
import com.buzizi.dualplayerconfrontation.modes.TntBadmintonMode;
import com.buzizi.dualplayerconfrontation.modes.TntRainMode;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.stats.StarManager;
import com.buzizi.dualplayerconfrontation.util.BukkitCompat;
import com.buzizi.dualplayerconfrontation.util.Keys;
import com.buzizi.dualplayerconfrontation.util.Region;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * 一场双人对局：回合推进（准备→战斗→结算）、胜负判定、
 * 超时天降TNT、超时后岩浆逐格上升、方块回滚/实体清理、经验/星级/奖励/任务结算。
 */
public final class DuelGame {

    public enum Phase {
        PREPARE, FIGHTING, ROUND_END, ENDING
    }

    private static final Random RANDOM = new Random();

    private final DualPlayerConfrontation plugin;
    private final UUID id = UUID.randomUUID();
    private final ArenaInstance arena;
    private final MapDefinition map;
    private final UUID playerA;
    private final UUID playerB;
    private final EnumMap<PlayerSide, Integer> scores = new EnumMap<>(PlayerSide.class);
    private final ModePool modePool = new ModePool();
    private final BlockChangeLog changeLog = new BlockChangeLog();

    private Phase phase = Phase.PREPARE;
    private GameMode currentMode;
    private int roundNumber;
    private int prepareLeft;
    private int roundEndLeft;
    private int roundSeconds;
    private int tieDropCounter;
    private boolean gameOverFlag;
    private PlayerSide pendingWinner;
    private boolean pendingForfeit;

    // 岩浆上升状态（超过 lava-start 秒后，岩浆从塔底逐格上涨）
    private boolean lavaActive;
    private int lavaLevel;
    private int lavaRiseCounter;

    private int totalRoundSeconds;
    private int totalRounds;

    private BukkitTask ticker;
    private Scoreboard scoreboardA;
    private Scoreboard scoreboardB;
    private boolean finished;

    public DuelGame(DualPlayerConfrontation plugin, Player a, Player b, ArenaInstance arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.map = arena.getMap();
        this.playerA = a.getUniqueId();
        this.playerB = b.getUniqueId();
        scores.put(PlayerSide.A, 0);
        scores.put(PlayerSide.B, 0);
    }

    // ==================== 只读信息 ====================

    public UUID getId() {
        return id;
    }

    public World getWorld() {
        return arena.getWorld();
    }

    public ArenaInstance getArena() {
        return arena;
    }

    public MapDefinition getMap() {
        return map;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isFighting() {
        return phase == Phase.FIGHTING;
    }

    public boolean isActive() {
        return !finished && phase != Phase.ENDING;
    }

    public int getRound() {
        return roundNumber;
    }

    public GameMode currentMode() {
        return currentMode;
    }

    public boolean isPlayer(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    public List<UUID> getPlayerIds() {
        return List.of(playerA, playerB);
    }

    public Player playerOf(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    public Player playerBySide(PlayerSide side) {
        return Bukkit.getPlayer(side == PlayerSide.A ? playerA : playerB);
    }

    public PlayerSide sideOf(UUID uuid) {
        return playerA.equals(uuid) ? PlayerSide.A : PlayerSide.B;
    }

    public int score(PlayerSide side) {
        return scores.getOrDefault(side, 0);
    }

    public Location spawnOf(PlayerSide side) {
        return side == PlayerSide.A ? arena.getSpawnA() : arena.getSpawnB();
    }

    public Region towerOf(PlayerSide side) {
        return side == PlayerSide.A ? map.getTowerA() : map.getTowerB();
    }

    // ==================== 主流程 ====================

    public void begin() {
        startPrepare();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond, 0L, 20L);
    }

    /** 每回合开始前的准备阶段：清场、回滚、传送到塔顶并锁定 */
    private void startPrepare() {
        phase = Phase.PREPARE;
        prepareLeft = Math.max(1, plugin.settings().roundPrepareSeconds());
        if (currentMode != null) {
            currentMode.onRoundEnd(this);
            currentMode = null;
        }
        cleanupRoundEntitiesAndBlocks();
        for (Player p : onlinePlayers()) {
            if (p == null) {
                continue;
            }
            if (p.isDead()) {
                p.spigot().respawn();
            }
            Location spawn = spawnOf(sideOf(p.getUniqueId()));
            p.teleport(spawn);
            resetPlayer(p);
            if (plugin.settings().freezeInPrepare()) {
                p.setAllowFlight(true);
                p.setFlying(true);
            }
        }
        updateScoreboards();
    }

    private void tickSecond() {
        switch (phase) {
            case PREPARE -> tickPrepare();
            case FIGHTING -> tickFighting();
            case ROUND_END -> tickRoundEnd();
            default -> {
            }
        }
    }

    private void tickPrepare() {
        for (Player p : onlinePlayers()) {
            if (p == null) {
                continue;
            }
            p.sendTitle(Text.color(plugin.messages().raw("game.countdown", "d", String.valueOf(prepareLeft))),
                    Text.color(plugin.messages().raw("game.prepare", "seconds", String.valueOf(prepareLeft))),
                    5, 20, 5);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f,
                    prepareLeft <= 3 ? 1.8f : 1.0f);
            if (plugin.settings().freezeInPrepare() && !p.isFlying()) {
                p.setAllowFlight(true);
                p.setFlying(true);
            }
        }
        prepareLeft--;
        if (prepareLeft <= 0) {
            beginFight();
        }
    }

    private void beginFight() {
        phase = Phase.FIGHTING;
        roundNumber++;
        roundSeconds = 0;
        tieDropCounter = plugin.settings().tieTntInterval();
        gameOverFlag = false;
        lavaActive = false;
        lavaLevel = Integer.MIN_VALUE;
        lavaRiseCounter = 0;
        for (Player p : onlinePlayers()) {
            if (p == null) {
                continue;
            }
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setFlySpeed(0.1f);
            p.setWalkSpeed(0.2f);
            p.setFireTicks(0);
            p.teleport(spawnOf(sideOf(p.getUniqueId())));
        }
        GameModeType type = modePool.next();
        currentMode = createMode(type);
        announceMode(type);
        currentMode.onRoundStart(this);
        updateScoreboards();
        for (Player p : onlinePlayers()) {
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            }
        }
    }

    private void announceMode(GameModeType type) {
        String nameKey = switch (type) {
            case FIREBALL -> "game.mode-fireball";
            case TNT_RAIN -> "game.mode-tnt-rain";
            case CREEPER -> "game.mode-creeper";
            case SHULKER -> "game.mode-shulker";
            case TNT_BADMINTON -> "game.mode-tnt-badminton";
        };
        String modeLabel = switch (type) {
            case FIREBALL -> "火焰弹乒乓球";
            case TNT_RAIN -> "天降好运";
            case CREEPER -> "投掷苦力怕";
            case SHULKER -> "超级潜影贝导弹";
            case TNT_BADMINTON -> "TNT羽毛球";
        };
        for (Player p : onlinePlayers()) {
            if (p == null) {
                continue;
            }
            p.sendTitle(Text.color(plugin.messages().raw("game.mode-announce", "mode", modeLabel)),
                    Text.color(plugin.messages().raw(nameKey)),
                    10, 50, 10);
        }
    }

    private void tickFighting() {
        roundSeconds++;
        Settings st = plugin.settings();

        if (roundSeconds >= st.maxRoundTime()) {
            tieDropCounter--;
            if (tieDropCounter <= 0) {
                tieDropCounter = st.tieTntInterval();
                dropRandomTnt();
            }
        } else if (roundSeconds == st.maxRoundTime() - 5) {
            messageBoth("game.round-timeout", "seconds", String.valueOf(st.maxRoundTime()));
        }

        if (roundSeconds == st.lavaStart()) {
            lavaActive = true;
            lavaLevel = findLavaSurfaceY();
            lavaRiseCounter = 0;
            messageBoth("game.lava-start", "seconds", String.valueOf(st.lavaStart()));
        }
        if (lavaActive) {
            lavaRiseCounter++;
            if (lavaRiseCounter >= nextRiseInterval()) {
                lavaRiseCounter = 0;
                riseLava();
            }
        }
        if (currentMode != null) {
            currentMode.tick(this);
        }
        StringBuilder bar = new StringBuilder("第").append(roundNumber).append("回合 ")
                .append(Text.num(roundSeconds)).append("s");
        if (roundSeconds >= st.maxRoundTime()) {
            bar.append(" §c⏱天降TNT中");
        }
        if (lavaActive) {
            bar.append(" §4⚠岩浆上升中");
        }
        String text = bar.toString();
        for (Player p : onlinePlayers()) {
            if (p != null) {
                p.sendActionBar(Text.color(text));
            }
        }
    }

    private void tickRoundEnd() {
        roundEndLeft--;
        if (roundEndLeft <= 0) {
            if (gameOverFlag) {
                endGame(pendingWinner, pendingForfeit);
            } else {
                startPrepare();
            }
        }
    }

    // ==================== 回合结算 ====================

    public void onPlayerDeath(Player loser) {
        if (phase != Phase.FIGHTING && phase != Phase.PREPARE) {
            return;
        }
        resolveRound(sideOf(loser.getUniqueId()), false);
    }

    public void onPlayerQuit(Player quitter) {
        if (finished || phase == Phase.ENDING) {
            return;
        }
        PlayerSide loserSide = sideOf(quitter.getUniqueId());
        messageBoth("game-end.opponent-left");
        endGame(loserSide.opposite(), true);
    }

    private void resolveRound(PlayerSide loserSide, boolean admin) {
        if (phase != Phase.FIGHTING && phase != Phase.PREPARE) {
            return;
        }
        PlayerSide winnerSide = loserSide.opposite();
        int duration = roundSeconds;
        totalRoundSeconds += duration;
        totalRounds++;

        int newScore = scores.merge(winnerSide, 1, Integer::sum);
        pendingWinner = winnerSide;
        pendingForfeit = false;
        gameOverFlag = newScore >= plugin.settings().winScore();
        phase = Phase.ROUND_END;
        roundEndLeft = plugin.settings().roundEndSeconds();

        recordRoundTime(winnerSide, true, duration);
        recordRoundTime(loserSide, false, duration);

        Player winner = playerBySide(winnerSide);
        Player loser = playerBySide(loserSide);
        if (winner != null) {
            plugin.messages().sendNoPrefix(winner, "game.round-win",
                    "winner", winner.getName(),
                    "scoreA", String.valueOf(score(PlayerSide.A)),
                    "scoreB", String.valueOf(score(PlayerSide.B)));
        }
        if (loser != null && loser.isOnline()) {
            plugin.messages().sendNoPrefix(loser, "game.round-lose",
                    "winner", winner == null ? "?" : winner.getName());
        }
        if (admin) {
            messageBoth("admin.force-round-win", "player", loser == null ? "" : loser.getName());
        }
        // 音效：胜者/负者提示
        if (winner != null) {
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
        }
        if (loser != null && loser.isOnline()) {
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
        }

        if (currentMode != null) {
            currentMode.onRoundEnd(this);
            currentMode = null;
        }
        cleanupRoundEntitiesAndBlocks();
        updateScoreboards();
        scheduleRoundEndSpectators();
    }

    /** 回合结算期：双方进入旁观模式自由飞行，直到下一回合开始 */
    private void scheduleRoundEndSpectators() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (finished || phase != Phase.ROUND_END) {
                return;
            }
            for (Player p : onlinePlayers()) {
                if (p == null) {
                    continue;
                }
                if (p.isDead()) {
                    p.spigot().respawn(); // 死亡玩家先复活（重生点由事件设为塔顶）
                }
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
        }, 4L);
    }

    private void recordRoundTime(PlayerSide side, boolean won, int duration) {
        UUID uuid = side == PlayerSide.A ? playerA : playerB;
        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(uuid));
        profile.addRoundTime(won, duration);
    }

    // ==================== 整局结束 ====================

    /** @param winnerSide null 表示被强制取消（双方都不记胜负） */
    private void endGame(PlayerSide winnerSide, boolean forfeit) {
        if (finished) {
            return;
        }
        finished = true;
        phase = Phase.ENDING;
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (currentMode != null) {
            currentMode.onRoundEnd(this);
            currentMode = null;
        }
        cleanupRoundEntitiesAndBlocks();

        Player winner = winnerSide == null ? null : playerBySide(winnerSide);
        Player loser = winnerSide == null ? null : playerBySide(winnerSide.opposite());

        if (winner != null) {
            plugin.messages().sendNoPrefix(winner, "game-end.win",
                    "scoreA", String.valueOf(score(PlayerSide.A)),
                    "scoreB", String.valueOf(score(PlayerSide.B)));
        }
        if (loser != null && loser.isOnline()) {
            plugin.messages().sendNoPrefix(loser, "game-end.lose",
                    "scoreA", String.valueOf(score(PlayerSide.A)),
                    "scoreB", String.valueOf(score(PlayerSide.B)));
        }
        if (winner != null && loser != null && !forfeit) {
            Bukkit.broadcastMessage(plugin.messages().get("game-end.broadcast-win",
                    "winner", winner.getName(), "loser", loser.getName()));
        }
        if (winnerSide != null) {
            awardResult(winnerSide, forfeit);
        }
        // 结局音效
        if (winner != null) {
            winner.playSound(winner.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (winner.isOnline()) {
                    winner.playSound(winner.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
                }
            }, 10L);
        }
        if (loser != null && loser.isOnline()) {
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }

        Location exit = resolveExitLocation();
        for (Player p : onlinePlayers()) {
            if (p == null) {
                continue;
            }
            if (p.isDead()) {
                p.spigot().respawn();
            }
            p.teleport(exit == null ? p.getWorld().getSpawnLocation() : exit);
            resetPlayer(p);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        removeScoreboard(scoreboardA);
        removeScoreboard(scoreboardB);
        plugin.games().unregisterAndDispose(this);
    }

    private void awardResult(PlayerSide winnerSide, boolean forfeit) {
        PlayerSide loserSide = winnerSide.opposite();
        UUID winUuid = winnerSide == PlayerSide.A ? playerA : playerB;
        UUID loseUuid = loserSide == PlayerSide.A ? playerA : playerB;

        PlayerProfile winProfile = plugin.stats().get(Bukkit.getOfflinePlayer(winUuid));
        PlayerProfile loseProfile = plugin.stats().get(Bukkit.getOfflinePlayer(loseUuid));
        winProfile.recordGameEnd(true);
        loseProfile.recordGameEnd(false);
        // 任务进度推进（对局结束后结算）
        plugin.quests().onGameEnd(winUuid, true);
        plugin.quests().onGameEnd(loseUuid, false);

        Settings st = plugin.settings();
        double avg = totalRounds == 0 ? st.xpAvgFloor() : (double) totalRoundSeconds / totalRounds;
        int oppRounds = score(loserSide);

        // 胜利：base * (60/平均回合秒) * ((win-score - 对方回合数)/win-score) + bonus
        double xpWin = st.xpWinBase() * (60.0 / Math.max(avg, st.xpAvgFloor()))
                * ((st.winScore() - oppRounds) / (double) st.winScore()) + st.xpWinBonus();
        // 失败：5 * 自己赢下的回合数
        double xpLose = st.xpLosePerRound() * oppRounds;
        long winAmount = Math.max(0, Math.round(xpWin));
        long loseAmount = Math.max(0, Math.round(xpLose));

        grantXp(winProfile, winAmount);
        grantXp(loseProfile, loseAmount);

        Player winner = playerBySide(winnerSide);
        Player loser = playerBySide(loserSide);
        String winnerName = winner == null ? Bukkit.getOfflinePlayer(winUuid).getName() : winner.getName();
        String loserName = loser == null ? Bukkit.getOfflinePlayer(loseUuid).getName() : loser.getName();
        if (winnerName == null) {
            winnerName = winUuid.toString();
        }
        if (loserName == null) {
            loserName = loseUuid.toString();
        }
        if (winner != null) {
            plugin.messages().send(winner, "game-end.experience-win",
                    "xp", String.valueOf(winAmount), "progress", xpProgress(winProfile));
        }
        if (loser != null && loser.isOnline()) {
            plugin.messages().send(loser, "game-end.experience-lose",
                    "xp", String.valueOf(loseAmount), "progress", xpProgress(loseProfile));
        }
        runRewards(true, winner, winnerName, loserName);
        runRewards(false, loser != null && loser.isOnline() ? loser : null, winnerName, loserName);
        plugin.stats().save(winProfile);
        plugin.stats().save(loseProfile);
    }

    private void grantXp(PlayerProfile profile, long amount) {
        int starBefore = StarManager.starFromXp(plugin.settings().starXpPerStar(), profile.getXp());
        profile.addXp(amount);
        int starAfter = StarManager.starFromXp(plugin.settings().starXpPerStar(), profile.getXp());
        Player player = Bukkit.getPlayer(profile.getUuid());
        if (player != null && starAfter > starBefore) {
            plugin.messages().send(player, "game-end.star-up", "star", String.valueOf(starAfter));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else if (player != null && amount > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        }
    }

    private String xpProgress(PlayerProfile profile) {
        Settings st = plugin.settings();
        int star = StarManager.starFromXp(st.starXpPerStar(), profile.getXp());
        double need = StarManager.xpRequiredForStar(st.starXpPerStar(), star);
        double into = StarManager.xpIntoStar(st.starXpPerStar(), profile.getXp());
        int pct = (int) Math.floor(into / Math.max(1, need) * 100);
        return star + st.starSymbol() + " " + Text.num(into) + "/" + Text.num(need) + " (" + pct + "%)";
    }

    private void runRewards(boolean win, Player asPlayer, String winnerName, String loserName) {
        Settings st = plugin.settings();
        List<String> cmds = win ? st.rewardWinCommands() : st.rewardLoseCommands();
        if (cmds == null || cmds.isEmpty()) {
            return;
        }
        if (!win && asPlayer == null) {
            return; // 失败方离线则跳过其失败奖励
        }
        boolean asConsole = !st.rewardRunAsPlayer();
        org.bukkit.command.CommandSender executor = asConsole ? Bukkit.getConsoleSender() : asPlayer;
        if (executor == null) {
            return;
        }
        String target = win ? winnerName : loserName;
        for (String raw : cmds) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String cmd = Text.placeholders(raw,
                    "player", target,
                    "winner", winnerName,
                    "loser", loserName,
                    "score", String.valueOf(win ? score(winnerSideForName(winnerName)) : score(winnerSideForName(loserName))),
                    "rounds", String.valueOf(roundNumber));
            try {
                Bukkit.dispatchCommand(executor, cmd);
            } catch (Exception e) {
                plugin.getLogger().warning("执行奖励命令失败: " + cmd + " - " + e.getMessage());
            }
        }
    }

    private PlayerSide winnerSideForName(String name) {
        Player a = playerBySide(PlayerSide.A);
        Player b = playerBySide(PlayerSide.B);
        if (a != null && name != null && name.equals(a.getName())) {
            return PlayerSide.A;
        }
        if (b != null && name != null && name.equals(b.getName())) {
            return PlayerSide.B;
        }
        return PlayerSide.A;
    }

    private Location resolveExitLocation() {
        Location custom = plugin.settings().lobbyExitLocation();
        if (custom != null) {
            return custom;
        }
        World exitWorld = Bukkit.getWorld(plugin.settings().lobbyExitWorld());
        if (exitWorld != null) {
            return exitWorld.getSpawnLocation();
        }
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getSpawnLocation();
    }

    // ==================== 管理员强制操作 ====================

    public void forceRoundWin(UUID uuid) {
        if (phase != Phase.FIGHTING && phase != Phase.PREPARE) {
            return;
        }
        resolveRound(sideOf(uuid).opposite(), true);
    }

    public void forceRoundLose(UUID uuid) {
        if (phase != Phase.FIGHTING && phase != Phase.PREPARE) {
            return;
        }
        resolveRound(sideOf(uuid), true);
    }

    public void forceGameWin(UUID uuid) {
        if (finished || phase == Phase.ENDING) {
            return;
        }
        PlayerSide side = sideOf(uuid);
        scores.put(side, plugin.settings().winScore());
        endGame(side, false);
    }

    public void forceEnd() {
        if (finished || phase == Phase.ENDING) {
            return;
        }
        messageBoth("admin.game-ended");
        endGame(null, false);
    }

    /** 服务器关闭时：不结算直接移出玩家并回收世界 */
    public void abortShutdown() {
        finished = true;
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (currentMode != null) {
            try {
                currentMode.onRoundEnd(this);
            } catch (Exception ignored) {
            }
            currentMode = null;
        }
        try {
            cleanupRoundEntitiesAndBlocks();
        } catch (Exception ignored) {
        }
        Location exit = resolveExitLocation();
        for (Player p : onlinePlayers()) {
            try {
                if (p != null && exit != null) {
                    p.teleport(exit);
                }
            } catch (Exception ignored) {
            }
        }
        plugin.games().unregisterAndDispose(this);
    }

    // ==================== 方块与实体 ====================

    public BlockChangeLog changeLog() {
        return changeLog;
    }

    public void registerEntity(Entity e) {
        arena.track(e);
    }

    /** 记录并清除一个方块；返回是否计入“破坏方块数”（流体不算）。开启物理更新使沙子自然坍塌 */
    public boolean destroyBlock(Block block) {
        if (block == null || block.getType().isAir() || BukkitCompat.isUnyielding(block)) {
            return false;
        }
        boolean countable = !BukkitCompat.isLiquidType(block.getType());
        changeLog.record(block);
        block.setType(Material.AIR, true);
        return countable;
    }

    /** 边界收缩擦除（不计数） */
    public void eraseBlock(Block block) {
        if (block == null || block.getType().isAir() || BukkitCompat.isLiquidType(block.getType())
                || BukkitCompat.isUnyielding(block)) {
            return;
        }
        changeLog.record(block);
        block.setType(Material.AIR, true);
    }

    public void explodeSphere(Location center, double radius) {
        explodeSphere(center, radius, null);
    }

    /** 球形爆炸：只破坏方块（含记录），不产生伤害与冲击 */
    public void explodeSphere(Location center, double radius, UUID owner) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int r = (int) Math.ceil(radius);
        int broken = 0;
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();
        double maxR2 = radius * radius + 0.01;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > maxR2) {
                        continue;
                    }
                    Block b = world.getBlockAt(bx + x, by + y, bz + z);
                    if (destroyBlock(b)) {
                        broken++;
                    }
                }
            }
        }
        countBlocksTo(owner, broken);
        playExplosionFx(center);
    }

    public void destroySingleBlock(Block block, UUID owner) {
        if (block == null) {
            return;
        }
        boolean destroyed = destroyBlock(block);
        if (destroyed && owner != null) {
            PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(owner));
            profile.addBlocksDestroyed(1);
        }
        playExplosionFx(block.getLocation().add(0.5, 0.5, 0.5));
    }

    /** 将若干破坏数计入某玩家档案（用于爆炸事件批量结算） */
    public void countBlocksTo(UUID owner, int amount) {
        if (owner == null || amount <= 0) {
            return;
        }
        PlayerProfile profile = plugin.stats().get(Bukkit.getOfflinePlayer(owner));
        profile.addBlocksDestroyed(amount);
    }

    public void playExplosionFx(Location loc) {
        if (loc.getWorld() == null) {
            return;
        }
        BukkitCompat.playExplosion(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ());
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
    }

    /** 生成一颗“顺爆TNT”（火焰弹命中后触发）；其爆炸由规则监听统一转为只破坏方块 */
    public void spawnFuseTnt(Location loc, UUID owner) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Location spawnLoc = loc.clone().add(0, 0.15, 0);
        TNTPrimed tnt = world.spawn(spawnLoc, TNTPrimed.class);
        tnt.setFuseTicks(3); // 顺爆
        Keys.setKind(tnt.getPersistentDataContainer(), Keys.E_FIRE_TNT);
        if (owner != null) {
            Keys.setOwner(tnt.getPersistentDataContainer(), owner);
        }
        registerEntity(tnt);
        world.playSound(spawnLoc, Sound.ENTITY_TNT_PRIMED, 1.0f, 0.6f);
    }

    /** 回合/整局结束：移除竞技场内非玩家实体并回滚方块 */
    public void cleanupRoundEntitiesAndBlocks() {
        World world = getWorld();
        if (world == null) {
            return;
        }
        for (Entity e : new ArrayList<>(world.getEntities())) {
            if (e instanceof Player) {
                continue;
            }
            if (e instanceof Projectile || e instanceof TNTPrimed || e instanceof FallingBlock
                    || e instanceof Creeper
                    || e instanceof Item
                    || e instanceof ExperienceOrb) {
                e.remove();
            }
        }
        arena.clearTrackedEntities();
        changeLog.restoreAll();
    }

    // ==================== 岩浆上升 ====================

    /** 找出地图岩浆海“表面”高度（采样中心与两塔中心列的最高岩浆层），找不到则用塔底推断 */
    private int findLavaSurfaceY() {
        World world = getWorld();
        int best = -1;
        Region a = map.getTowerA();
        Region b = map.getTowerB();
        List<int[]> columns = new ArrayList<>();
        columns.add(new int[]{map.getCenter().getBlockX(), map.getCenter().getBlockZ()});
        if (a != null) {
            columns.add(new int[]{(a.getMinX() + a.getMaxX()) / 2, (a.getMinZ() + a.getMaxZ()) / 2});
        }
        if (b != null) {
            columns.add(new int[]{(b.getMinX() + b.getMaxX()) / 2, (b.getMinZ() + b.getMaxZ()) / 2});
        }
        for (int[] col : columns) {
            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                Material type = world.getBlockAt(col[0], y, col[1]).getType();
                if (BukkitCompat.isLiquidType(type)
                        && (type.name().equals("LAVA") || type.name().equals("FLOWING_LAVA"))) {
                    best = Math.max(best, y);
                }
            }
        }
        if (best < 0) {
            int lowest = Integer.MAX_VALUE;
            if (a != null) {
                lowest = Math.min(lowest, a.getMinY());
            }
            if (b != null) {
                lowest = Math.min(lowest, b.getMinY());
            }
            best = Math.max(world.getMinHeight(), (lowest == Integer.MAX_VALUE ? 40 : lowest) - 1);
        }
        return best;
    }

    /** 每次上涨的间隔（秒），在 min~max 之间随机（默认 3~4 秒） */
    private int nextRiseInterval() {
        Settings st = plugin.settings();
        int min = Math.max(1, st.lavaRiseIntervalMin());
        int max = Math.max(min, st.lavaRiseIntervalMax());
        return min + RANDOM.nextInt(max - min + 1);
    }

    /** 岩浆向上一格：把塔区上方的整层方块替换为岩浆源（记录回滚） */
    private void riseLava() {
        World world = getWorld();
        if (world == null) {
            return;
        }
        Region a = map.getTowerA();
        Region b = map.getTowerB();
        int topMax = Math.max(a == null ? 0 : a.getMaxY(), b == null ? 0 : b.getMaxY());
        int newLevel = lavaLevel + 1;
        if (newLevel > topMax + 2) {
            return; // 已高过塔顶，再升无意义（玩家早已无路可退）
        }
        int pad = Math.max(0, plugin.settings().lavaFillPadding());
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        if (a != null && b != null) {
            minX = Math.min(a.getMinX(), b.getMinX()) - pad;
            maxX = Math.max(a.getMaxX(), b.getMaxX()) + pad;
            minZ = Math.min(a.getMinZ(), b.getMinZ()) - pad;
            maxZ = Math.max(a.getMaxZ(), b.getMaxZ()) + pad;
        } else {
            int cx = map.getCenter().getBlockX();
            int cz = map.getCenter().getBlockZ();
            minX = cx - 20;
            maxX = cx + 20;
            minZ = cz - 20;
            maxZ = cz + 20;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, newLevel, z);
                if (BukkitCompat.isUnyielding(block)) {
                    continue;
                }
                Material type = block.getType();
                if (type.name().equals("LAVA") || type.name().equals("FLOWING_LAVA")) {
                    continue;
                }
                changeLog().record(block);
                block.setType(Material.LAVA, true);
            }
        }
        lavaLevel = newLevel;
        for (Player p : onlinePlayers()) {
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_LAVA_POP, 0.8f, 0.8f);
            }
        }
    }

    private void dropRandomTnt() {
        World world = getWorld();
        Region a = map.getTowerA();
        Region b = map.getTowerB();
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        if (a != null && b != null) {
            minX = Math.min(a.getMinX(), b.getMinX()) - 5;
            maxX = Math.max(a.getMaxX(), b.getMaxX()) + 5;
            minZ = Math.min(a.getMinZ(), b.getMinZ()) - 5;
            maxZ = Math.max(a.getMaxZ(), b.getMaxZ()) + 5;
        } else {
            minX = map.getCenter().getBlockX() - 10;
            maxX = map.getCenter().getBlockX() + 10;
            minZ = map.getCenter().getBlockZ() - 10;
            maxZ = map.getCenter().getBlockZ() + 10;
        }
        int x = minX + RANDOM.nextInt(Math.max(1, maxX - minX + 1));
        int z = minZ + RANDOM.nextInt(Math.max(1, maxZ - minZ + 1));
        int top = (a != null ? Math.max(a.getMaxY(), b == null ? 0 : b.getMaxY()) : 0) + 30;
        Location loc = new Location(world, x + 0.5, top, z + 0.5);
        TNTPrimed tnt = world.spawn(loc, TNTPrimed.class);
        tnt.setFuseTicks(plugin.settings().tntFuseTicks() + 30);
        registerEntity(tnt);
    }

    // ==================== 记分板 ====================

    public void updateScoreboards() {
        if (!plugin.settings().scoreboardEnabled()) {
            return;
        }
        for (Player p : onlinePlayers()) {
            if (p != null) {
                applyScoreboard(p);
            }
        }
    }

    private void applyScoreboard(Player p) {
        PlayerSide side = sideOf(p.getUniqueId());
        PlayerSide enemySide = side.opposite();
        String you = p.getName();
        Player enemyPlayer = playerBySide(enemySide);
        String enemy = enemyPlayer == null ? "?" : enemyPlayer.getName();
        String modeName = currentMode == null ? "-" : switch (currentMode.type()) {
            case FIREBALL -> "火焰弹乒乓球";
            case TNT_RAIN -> "天降好运";
            case CREEPER -> "投掷苦力怕";
            case SHULKER -> "潜影贝导弹";
            case TNT_BADMINTON -> "TNT羽毛球";
        };
        List<String> template = plugin.settings().scoreboardLines();
        List<String> lines = new ArrayList<>(template.size());
        Set<String> used = new HashSet<>();
        int guard = 0;
        for (String rawLine : template) {
            String line = Text.color(Text.placeholders(rawLine,
                    "you", you,
                    "enemy", enemy,
                    "opponent", enemy,
                    "score", String.valueOf(score(side)),
                    "enemy_score", String.valueOf(score(enemySide)),
                    "win_score", String.valueOf(plugin.settings().winScore()),
                    "round", String.valueOf(roundNumber),
                    "mode", modeName));
            String unique = line;
            while (used.contains(unique)) {
                unique = line + " ".repeat(Math.min(30, ++guard));
            }
            used.add(unique);
            lines.add(unique);
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("dpconf", "dummy",
                Text.color(plugin.settings().scoreboardTitle()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int value = lines.size();
        for (String line : lines) {
            obj.getScore(line).setScore(value--);
        }
        if (side == PlayerSide.A) {
            removeScoreboard(scoreboardA);
            scoreboardA = board;
        } else {
            removeScoreboard(scoreboardB);
            scoreboardB = board;
        }
        p.setScoreboard(board);
    }

    private void removeScoreboard(Scoreboard board) {
        if (board == null) {
            return;
        }
        try {
            Objective obj = board.getObjective(DisplaySlot.SIDEBAR);
            if (obj != null) {
                obj.unregister();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== 小工具 ====================

    private void resetPlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFlySpeed(0.1f);
        p.setWalkSpeed(0.2f);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
    }

    private List<Player> onlinePlayers() {
        List<Player> list = new ArrayList<>(2);
        Player a = playerBySide(PlayerSide.A);
        Player b = playerBySide(PlayerSide.B);
        if (a != null) {
            list.add(a);
        }
        if (b != null) {
            list.add(b);
        }
        return list;
    }

    private void messageBoth(String key, String... kv) {
        for (Player p : onlinePlayers()) {
            if (p != null) {
                plugin.messages().send(p, key, kv);
            }
        }
    }

    private GameMode createMode(GameModeType type) {
        return switch (type) {
            case FIREBALL -> new FireballMode(plugin, this);
            case TNT_RAIN -> new TntRainMode(plugin, this);
            case CREEPER -> new CreeperMode(plugin, this);
            case SHULKER -> new ShulkerMode(plugin, this);
            case TNT_BADMINTON -> new TntBadmintonMode(plugin, this);
        };
    }
}
