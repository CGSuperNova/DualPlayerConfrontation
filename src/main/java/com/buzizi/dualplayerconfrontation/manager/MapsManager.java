package com.buzizi.dualplayerconfrontation.manager;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 地图注册表：maps.yml 读写、选取空闲地图 */
public final class MapsManager {

    private final DualPlayerConfrontation plugin;
    private final File file;
    private YamlConfiguration cfg;
    private final Map<String, MapDefinition> maps = new LinkedHashMap<>();

    public MapsManager(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maps.yml");
        load();
    }

    public void load() {
        maps.clear();
        cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("maps");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                MapDefinition def = MapDefinition.loadFrom(id, sec.getConfigurationSection(id));
                maps.put(id.toLowerCase(Locale.ROOT), def);
            }
        }
    }

    public void save() {
        cfg.set("maps", null);
        ConfigurationSection sec = cfg.createSection("maps");
        for (MapDefinition def : maps.values()) {
            def.saveTo(sec.createSection(def.getId()));
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 maps.yml 失败: " + e.getMessage());
        }
    }

    public MapDefinition create(String id, World world) {
        String key = id.toLowerCase(Locale.ROOT);
        if (maps.containsKey(key)) {
            return null;
        }
        MapDefinition def = new MapDefinition(key);
        def.setDisplayName(id);
        def.setWorldName(world.getName());
        def.setFolder(world.getWorldFolder().getAbsolutePath());
        def.setEnvironment(world.getEnvironment().name());
        maps.put(key, def);
        save();
        return def;
    }

    public MapDefinition get(String id) {
        return maps.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean remove(String id) {
        MapDefinition removed = maps.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<MapDefinition> all() {
        return maps.values();
    }

    public List<MapDefinition> enabledAndComplete() {
        return maps.values().stream()
                .filter(MapDefinition::isComplete)
                .filter(MapDefinition::isEnabled)
                .collect(Collectors.toList());
    }

    /** 选一张最久没被用过的已启用地图；无可用返回 null */
    public MapDefinition pick() {
        List<MapDefinition> available = new ArrayList<>(enabledAndComplete());
        if (available.isEmpty()) {
            return null;
        }
        available.sort(Comparator.comparingLong(MapDefinition::getLastUsed));
        MapDefinition chosen = available.get(0);
        chosen.markUsed();
        save();
        return chosen;
    }
}
