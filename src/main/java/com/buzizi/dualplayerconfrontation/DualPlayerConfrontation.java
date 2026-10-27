package com.buzizi.dualplayerconfrontation;

import com.buzizi.dualplayerconfrontation.cmd.DuelAdminCommand;
import com.buzizi.dualplayerconfrontation.cmd.DuelCommand;
import com.buzizi.dualplayerconfrontation.cmd.DuelMapCommand;
import com.buzizi.dualplayerconfrontation.config.Messages;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.listeners.GameRulesListener;
import com.buzizi.dualplayerconfrontation.listeners.PlayerLifecycleListener;
import com.buzizi.dualplayerconfrontation.manager.ArenaWorldManager;
import com.buzizi.dualplayerconfrontation.manager.GameManager;
import com.buzizi.dualplayerconfrontation.manager.MapsManager;
import com.buzizi.dualplayerconfrontation.manager.QuestManager;
import com.buzizi.dualplayerconfrontation.manager.QueueManager;
import com.buzizi.dualplayerconfrontation.papi.DuelExpansion;
import com.buzizi.dualplayerconfrontation.stats.StatsManager;
import com.buzizi.dualplayerconfrontation.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DualPlayerConfrontation（双军对峙）主类
 */
public final class DualPlayerConfrontation extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private StatsManager stats;
    private MapsManager maps;
    private ArenaWorldManager arenas;
    private QueueManager queue;
    private GameManager games;
    private QuestManager quests;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        saveDefaultConfig();
        Keys.init(this);

        settings = new Settings(this);
        messages = new Messages(this);
        stats = new StatsManager(this, settings);
        arenas = new ArenaWorldManager(this);
        arenas.purgeStaleArenas();
        maps = new MapsManager(this);
        games = new GameManager(this);
        queue = new QueueManager(this);
        quests = new QuestManager(this);

        Bukkit.getPluginManager().registerEvents(new GameRulesListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);

        registerCommand("duel", new DuelCommand(this));
        registerCommand("duelmap", new DuelMapCommand(this));
        registerCommand("dueladmin", new DuelAdminCommand(this));

        queue.startTicking();
        stats.startAutosave();
        startItemCleanup();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new DuelExpansion(this).register();
                getLogger().info("已注册 PlaceholderAPI 扩展: %duel_...%");
            } catch (Throwable e) {
                getLogger().warning("PlaceholderAPI 扩展注册失败: " + e.getMessage());
            }
        }

        getLogger().info("DualPlayerConfrontation 已启用 (" + (System.currentTimeMillis() - start) + "ms)");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdownAll();
        }
        if (queue != null) {
            queue.stop();
        }
        if (stats != null) {
            stats.saveAllSync();
        }
        getLogger().info("DualPlayerConfrontation 已禁用");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor((org.bukkit.command.CommandExecutor) executor);
            if (executor instanceof org.bukkit.command.TabCompleter completer) {
                command.setTabCompleter(completer);
            }
        } else {
            getLogger().warning("命令未在 plugin.yml 中声明: /" + name);
        }
    }

    /** 定时清除竞技场内掉落的物品实体 */
    private void startItemCleanup() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var game : games.allGames()) {
                var world = game.getWorld();
                if (world == null) {
                    continue;
                }
                for (Entity e : world.getEntities()) {
                    if (e instanceof Item) {
                        e.remove();
                    }
                }
            }
        }, 20L, Math.max(10L, settings.itemCleanupTicks()));
    }

    // ---------- 管理器访问 ----------

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public StatsManager stats() {
        return stats;
    }

    public MapsManager maps() {
        return maps;
    }

    public ArenaWorldManager arenas() {
        return arenas;
    }

    public QueueManager queue() {
        return queue;
    }

    public GameManager games() {
        return games;
    }

    public QuestManager quests() {
        return quests;
    }
}
