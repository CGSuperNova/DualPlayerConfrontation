package com.buzizi.dualplayerconfrontation.stats;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** 玩家统计数据仓库：YAML 每玩家一文件，内存缓存 + 异步落盘 */
public final class StatsManager {

    private final DualPlayerConfrontation plugin;
    private final Settings settings;
    private final File dataFolder;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public StatsManager(DualPlayerConfrontation plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    private File fileOf(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }

    /** 获取档案（离线玩家也可），不存在返回 Optional.empty（不创建） */
    public Optional<PlayerProfile> find(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        return load(uuid);
    }

    /** 获取档案；不存在则新建空档案（不落盘，直到 save） */
    public PlayerProfile get(OfflinePlayer player) {
        PlayerProfile cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        Optional<PlayerProfile> loaded = load(player.getUniqueId());
        PlayerProfile profile = loaded.orElseGet(() -> new PlayerProfile(player.getUniqueId(), player.getName()));
        if (player.getName() != null) {
            profile.setName(player.getName());
        }
        cache.put(player.getUniqueId(), profile);
        return profile;
    }

    private Optional<PlayerProfile> load(UUID uuid) {
        File file = fileOf(uuid);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            PlayerProfile profile = new PlayerProfile(uuid, yml.getString("name", uuid.toString()));
            profile.load(yml);
            cache.put(uuid, profile);
            return Optional.of(profile);
        } catch (Exception e) {
            plugin.getLogger().warning("读取玩家数据失败 " + uuid + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** 异步保存单个档案 */
    public void save(PlayerProfile profile) {
        final UUID uuid = profile.getUuid();
        YamlConfiguration yml = new YamlConfiguration();
        profile.saveTo(yml);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                yml.save(fileOf(uuid));
            } catch (IOException e) {
                plugin.getLogger().warning("保存玩家数据失败 " + uuid + ": " + e.getMessage());
            }
        });
    }

    /** 保存所有内存中已加载的档案（异步） */
    public void saveAll() {
        for (PlayerProfile profile : cache.values()) {
            save(profile);
        }
    }

    /** 同步保存全部档案（插件卸载时用，避免异步任务被服务器终止） */
    public void saveAllSync() {
        for (PlayerProfile profile : cache.values()) {
            try {
                YamlConfiguration yml = new YamlConfiguration();
                profile.saveTo(yml);
                yml.save(fileOf(profile.getUuid()));
            } catch (IOException e) {
                plugin.getLogger().warning("保存玩家数据失败 " + profile.getUuid() + ": " + e.getMessage());
            }
        }
    }

    /** 启动定期自动保存 */
    public void startAutosave() {
        int minutes = settings.statsAutosaveMinutes();
        if (minutes <= 0) {
            return;
        }
        long ticks = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, ticks, ticks);
    }

    /** 玩家加入时预热缓存 */
    public void onPlayerJoin(Player player) {
        get(player);
    }

    /** 玩家退出时立即保存并从缓存移除 */
    public void onPlayerQuit(Player player) {
        PlayerProfile profile = cache.remove(player.getUniqueId());
        if (profile != null) {
            save(profile);
        }
    }
}
