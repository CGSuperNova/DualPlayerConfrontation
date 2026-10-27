package com.buzizi.dualplayerconfrontation.stats;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** 单个玩家的统计数据与经验（内存态），由 StatsManager 负责持久化 */
public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private long xp;

    private int games;
    private int wins;
    private int losses;

    private long blocksDestroyed;
    private long fireballsShot;
    private long fireballsHit;
    private long tntActivated;
    private long creepersThrown;
    private long chargedCreepersThrown;
    private long bulletsShot;
    private long bulletsHit;

    private long roundTimeWonSum;
    private int roundCountWon;
    private long roundTimeLostSum;
    private int roundCountLost;

    // 任务进度（由 QuestManager 使用）：id -> 完成所在周期 / 周期起点快照
    private final java.util.Map<String, Long> questCompleted = new java.util.HashMap<>();
    private final java.util.Map<String, Long> questBaselinePeriod = new java.util.HashMap<>();
    private final java.util.Map<String, Long> questBaselineValue = new java.util.HashMap<>();

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    // ---- 任务 ----
    public Long questCompleted(String id) {
        return questCompleted.get(id);
    }

    public void markQuestCompleted(String id, long period) {
        questCompleted.put(id, period);
    }

    public Long questBaselinePeriod(String id) {
        return questBaselinePeriod.get(id);
    }

    public Long questBaselineValue(String id, long fallback) {
        Long v = questBaselineValue.get(id);
        return v == null ? fallback : v;
    }

    public void questBaseline(String id, long period, long value) {
        questBaselinePeriod.put(id, period);
        questBaselineValue.put(id, value);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getXp() {
        return xp;
    }

    public void addXp(long amount) {
        this.xp = Math.max(0, this.xp + amount);
    }

    // ---- 战绩 ----
    public int getGames() {
        return games;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public void recordGameEnd(boolean won) {
        games++;
        if (won) {
            wins++;
        } else {
            losses++;
        }
    }

    // ---- 行为 ----
    public long getBlocksDestroyed() {
        return blocksDestroyed;
    }

    public void addBlocksDestroyed(int amount) {
        this.blocksDestroyed += Math.max(0, amount);
    }

    public long getFireballsShot() {
        return fireballsShot;
    }

    public void addFireballsShot(int n) {
        this.fireballsShot += n;
    }

    public long getFireballsHit() {
        return fireballsHit;
    }

    public void addFireballsHit(int n) {
        this.fireballsHit += n;
    }

    public long getTntActivated() {
        return tntActivated;
    }

    public void addTntActivated(int n) {
        this.tntActivated += n;
    }

    public long getCreepersThrown() {
        return creepersThrown;
    }

    public void addCreepersThrown(int n) {
        this.creepersThrown += n;
    }

    public long getChargedCreepersThrown() {
        return chargedCreepersThrown;
    }

    public void addChargedCreepersThrown(int n) {
        this.chargedCreepersThrown += n;
    }

    public long getBulletsShot() {
        return bulletsShot;
    }

    public void addBulletsShot(int n) {
        this.bulletsShot += n;
    }

    public long getBulletsHit() {
        return bulletsHit;
    }

    public void addBulletsHit(int n) {
        this.bulletsHit += n;
    }

    // ---- 回合耗时 ----
    public void addRoundTime(boolean won, int seconds) {
        if (won) {
            roundTimeWonSum += seconds;
            roundCountWon++;
        } else {
            roundTimeLostSum += seconds;
            roundCountLost++;
        }
    }

    public double avgRoundTimeWon() {
        return roundCountWon == 0 ? 0 : (double) roundTimeWonSum / roundCountWon;
    }

    public double avgRoundTimeLost() {
        return roundCountLost == 0 ? 0 : (double) roundTimeLostSum / roundCountLost;
    }

    public double avgRoundTimeAll() {
        int n = roundCountWon + roundCountLost;
        return n == 0 ? 0 : (double) (roundTimeWonSum + roundTimeLostSum) / n;
    }

    // ---- 序列化 ----
    public void load(ConfigurationSection sec) {
        if (sec == null) {
            return;
        }
        name = sec.getString("name", name);
        xp = sec.getLong("xp", 0);
        games = sec.getInt("games", 0);
        wins = sec.getInt("wins", 0);
        losses = sec.getInt("losses", 0);
        blocksDestroyed = sec.getLong("blocksDestroyed", 0);
        fireballsShot = sec.getLong("fireballsShot", 0);
        fireballsHit = sec.getLong("fireballsHit", 0);
        tntActivated = sec.getLong("tntActivated", 0);
        creepersThrown = sec.getLong("creepersThrown", 0);
        chargedCreepersThrown = sec.getLong("chargedCreepersThrown", 0);
        bulletsShot = sec.getLong("bulletsShot", 0);
        bulletsHit = sec.getLong("bulletsHit", 0);
        roundTimeWonSum = sec.getLong("roundTimeWonSum", 0);
        roundCountWon = sec.getInt("roundCountWon", 0);
        roundTimeLostSum = sec.getLong("roundTimeLostSum", 0);
        roundCountLost = sec.getInt("roundCountLost", 0);
        // 任务
        ConfigurationSection quests = sec.getConfigurationSection("quests");
        if (quests != null) {
            loadQuestMap(questCompleted, quests.getConfigurationSection("completed"));
            loadQuestMap(questBaselinePeriod, quests.getConfigurationSection("baseline-period"));
            loadQuestMap(questBaselineValue, quests.getConfigurationSection("baseline-value"));
        }
    }

    private void loadQuestMap(java.util.Map<String, Long> target, ConfigurationSection sec) {
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            target.put(key, sec.getLong(key, 0));
        }
    }

    public void saveTo(ConfigurationSection sec) {
        sec.set("name", name);
        sec.set("xp", xp);
        sec.set("games", games);
        sec.set("wins", wins);
        sec.set("losses", losses);
        sec.set("blocksDestroyed", blocksDestroyed);
        sec.set("fireballsShot", fireballsShot);
        sec.set("fireballsHit", fireballsHit);
        sec.set("tntActivated", tntActivated);
        sec.set("creepersThrown", creepersThrown);
        sec.set("chargedCreepersThrown", chargedCreepersThrown);
        sec.set("bulletsShot", bulletsShot);
        sec.set("bulletsHit", bulletsHit);
        sec.set("roundTimeWonSum", roundTimeWonSum);
        sec.set("roundCountWon", roundCountWon);
        sec.set("roundTimeLostSum", roundTimeLostSum);
        sec.set("roundCountLost", roundCountLost);
        // 任务
        ConfigurationSection quests = sec.createSection("quests");
        saveQuestMap(questCompleted, quests.createSection("completed"));
        saveQuestMap(questBaselinePeriod, quests.createSection("baseline-period"));
        saveQuestMap(questBaselineValue, quests.createSection("baseline-value"));
    }

    private void saveQuestMap(java.util.Map<String, Long> map, ConfigurationSection sec) {
        for (java.util.Map.Entry<String, Long> e : map.entrySet()) {
            sec.set(e.getKey(), e.getValue());
        }
    }

    public double winRate() {
        return games == 0 ? 0 : wins * 100.0 / games;
    }
}
