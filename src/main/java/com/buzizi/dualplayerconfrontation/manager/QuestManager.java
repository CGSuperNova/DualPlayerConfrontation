package com.buzizi.dualplayerconfrontation.manager;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * 任务系统：每日任务 + 每周任务。
 * 任务进度基于玩家“累计统计值”与周期起点快照的差值计算（对局结束、查看任务时结算），
 * 完成后奖励经验并可执行配置的命令。
 */
public final class QuestManager {

    /** 可被任务追踪的统计项（与 PlayerProfile 字段一一对应） */
    public enum QuestType {
        WIN_GAMES, PLAY_GAMES, BLOCKS_DESTROYED,
        FIREBALLS_SHOT, FIREBALLS_HIT, TNTS_ACTIVATED,
        CREEPERS_THROWN, CHARGED_CREEPERS_THROWN, BULLETS_SHOT, BULLETS_HIT
    }

    /** 单个任务的静态定义 */
    public static final class QuestDef {
        public final String id;
        public final String display;
        public final QuestType type;
        public final int target;
        public final long rewardXp;
        public final List<String> commands;
        public final boolean daily;

        public QuestDef(String id, String display, QuestType type, int target,
                        long rewardXp, List<String> commands, boolean daily) {
            this.id = id;
            this.display = display;
            this.type = type;
            this.target = Math.max(1, target);
            this.rewardXp = Math.max(0, rewardXp);
            this.commands = commands == null ? List.of() : List.copyOf(commands);
            this.daily = daily;
        }
    }

    private final DualPlayerConfrontation plugin;
    private List<QuestDef> daily = new ArrayList<>();
    private List<QuestDef> weekly = new ArrayList<>();
    private boolean rewardRunAsPlayer;

    public QuestManager(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        daily = new ArrayList<>();
        weekly = new ArrayList<>();
        rewardRunAsPlayer = "player".equalsIgnoreCase(plugin.getConfig().getString("quests.run-as", "console"));
        daily = parseSection(plugin.getConfig().getConfigurationSection("quests.daily"), true);
        weekly = parseSection(plugin.getConfig().getConfigurationSection("quests.weekly"), false);
    }

    private List<QuestDef> parseSection(ConfigurationSection sec, boolean daily) {
        List<QuestDef> out = new ArrayList<>();
        if (sec == null) {
            return out;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            String typeStr = s.getString("type", "");
            QuestType type;
            try {
                type = QuestType.valueOf(typeStr.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("任务 " + key + " 的类型无效: " + typeStr);
                continue;
            }
            out.add(new QuestDef(
                    key,
                    s.getString("display", key),
                    type,
                    s.getInt("target", 1),
                    s.getLong("reward-xp", 0),
                    s.getStringList("commands"),
                    daily));
        }
        return out;
    }

    public List<QuestDef> daily() {
        return daily;
    }

    public List<QuestDef> weekly() {
        return weekly;
    }

    // ---------- 周期 ----------

    private static long dayEpoch() {
        return LocalDate.now().toEpochDay();
    }

    private static long weekEpoch() {
        return LocalDate.now().with(DayOfWeek.MONDAY).toEpochDay();
    }

    // ---------- 统计取值 ----------

    public long counterOf(PlayerProfile profile, QuestType type) {
        switch (type) {
            case WIN_GAMES:
                return profile.getWins();
            case PLAY_GAMES:
                return profile.getGames();
            case BLOCKS_DESTROYED:
                return profile.getBlocksDestroyed();
            case FIREBALLS_SHOT:
                return profile.getFireballsShot();
            case FIREBALLS_HIT:
                return profile.getFireballsHit();
            case TNTS_ACTIVATED:
                return profile.getTntActivated();
            case CREEPERS_THROWN:
                return profile.getCreepersThrown();
            case CHARGED_CREEPERS_THROWN:
                return profile.getChargedCreepersThrown();
            case BULLETS_SHOT:
                return profile.getBulletsShot();
            case BULLETS_HIT:
                return profile.getBulletsHit();
            default:
                return 0;
        }
    }

    /** 对局结束：为玩家结算每日/每周任务（胜利与否都会推进对应统计） */
    public void onGameEnd(UUID uuid, boolean won) {
        PlayerProfile profile = plugin.stats().find(uuid).orElse(null);
        if (profile == null) {
            return;
        }
        boolean changed = false;
        for (QuestDef def : daily) {
            changed |= evaluate(def, profile);
        }
        for (QuestDef def : weekly) {
            changed |= evaluate(def, profile);
        }
        if (changed) {
            plugin.stats().save(profile);
        }
    }

    /** 结算单个任务，达成则发奖励 */
    private boolean evaluate(QuestDef def, PlayerProfile profile) {
        long period = def.daily ? dayEpoch() : weekEpoch();
        if (periodEquals(profile, def.id, period)) {
            return false; // 本周期已完成
        }
        Long basePeriod = profile.questBaselinePeriod(def.id);
        long total = counterOf(profile, def.type);
        if (basePeriod == null || basePeriod != period) {
            // 进入新周期：以当前累计值作为起点
            profile.questBaseline(def.id, period, total);
            return false;
        }
        long baseline = profile.questBaselineValue(def.id, total);
        if (total - baseline >= def.target) {
            profile.markQuestCompleted(def.id, period);
            reward(def, profile);
            return true;
        }
        return false;
    }

    private boolean periodEquals(PlayerProfile profile, String id, long period) {
        Long done = profile.questCompleted(id);
        return done != null && done == period;
    }

    private void reward(QuestDef def, PlayerProfile profile) {
        if (def.rewardXp > 0) {
            profile.addXp(def.rewardXp);
        }
        Player player = Bukkit.getPlayer(profile.getUuid());
        if (player != null) {
            plugin.messages().send(player, "quest.completed-notify",
                    "name", def.display,
                    "xp", String.valueOf(def.rewardXp));
        }
        if (!def.commands.isEmpty()) {
            org.bukkit.command.CommandSender executor = rewardRunAsPlayer && player != null
                    ? player : Bukkit.getConsoleSender();
            if (executor == null) {
                executor = Bukkit.getConsoleSender();
            }
            String name = player != null ? player.getName() : profile.getName();
            if (name == null || name.isEmpty()) {
                name = profile.getUuid().toString();
            }
            for (String raw : def.commands) {
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                try {
                    Bukkit.dispatchCommand(executor, Text.placeholders(raw,
                            "player", name,
                            "quest", def.id,
                            "xp", String.valueOf(def.rewardXp)));
                } catch (Exception e) {
                    plugin.getLogger().warning("任务奖励命令执行失败: " + raw + " - " + e.getMessage());
                }
            }
        }
    }

    /** 供 /duel quests 查看：返回可打印的任务行 */
    public List<String> listLines(PlayerProfile profile) {
        List<String> out = new ArrayList<>();
        out.add(Text.color(plugin.messages().raw("quest.title")));
        out.add(Text.color(plugin.messages().raw("quest.daily-title")));
        addQuestLines(out, profile, daily);
        out.add(Text.color(plugin.messages().raw("quest.weekly-title")));
        addQuestLines(out, profile, weekly);
        return out;
    }

    private void addQuestLines(List<String> out, PlayerProfile profile, List<QuestDef> defs) {
        if (defs.isEmpty()) {
            out.add(Text.color(plugin.messages().raw("quest.no-quests")));
            return;
        }
        for (QuestDef def : defs) {
            long period = def.daily ? dayEpoch() : weekEpoch();
            boolean done = periodEquals(profile, def.id, period);
            long total = counterOf(profile, def.type);
            long progress;
            if (done) {
                progress = def.target;
            } else {
                Long basePeriod = profile.questBaselinePeriod(def.id);
                if (basePeriod == null || basePeriod != period) {
                    progress = 0;
                } else {
                    long baseline = profile.questBaselineValue(def.id, total);
                    progress = Math.max(0, Math.min(def.target, total - baseline));
                }
            }
            out.add(Text.color(Text.placeholders(plugin.messages().raw("quest.line"),
                    "name", def.display,
                    "progress", String.valueOf(progress),
                    "target", String.valueOf(def.target),
                    "xp", String.valueOf(def.rewardXp),
                    "status", done ? plugin.messages().raw("quest.done")
                            : plugin.messages().raw("quest.pending"))));
        }
    }
}
