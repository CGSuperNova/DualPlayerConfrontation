package com.buzizi.dualplayerconfrontation.game;

import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** 一场对局使用的竞技场实例（一个由模板世界拷贝出的独立世界） */
public final class ArenaInstance {

    private final MapDefinition map;
    private final World world;
    private final String instanceName;
    private final File folder;
    private final Location spawnA;
    private final Location spawnB;
    private final Location center;

    private final Set<UUID> persistentEntities = new HashSet<>();

    public ArenaInstance(MapDefinition map, World world, String instanceName, File folder,
                         Location spawnA, Location spawnB, Location center) {
        this.map = map;
        this.world = world;
        this.instanceName = instanceName;
        this.folder = folder;
        this.spawnA = spawnA;
        this.spawnB = spawnB;
        this.center = center;
    }

    public MapDefinition getMap() {
        return map;
    }

    public World getWorld() {
        return world;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public File getFolder() {
        return folder;
    }

    public Location getSpawnA() {
        return spawnA;
    }

    public Location getSpawnB() {
        return spawnB;
    }

    public Location getCenter() {
        return center;
    }

    public void track(Entity entity) {
        if (entity != null) {
            persistentEntities.add(entity.getUniqueId());
        }
    }

    public void untrack(Entity entity) {
        if (entity != null) {
            persistentEntities.remove(entity.getUniqueId());
        }
    }

    public Set<UUID> getTrackedEntities() {
        return persistentEntities;
    }

    /** 移除所有本局生成的实体 */
    public void clearTrackedEntities() {
        if (world == null) {
            persistentEntities.clear();
            return;
        }
        for (UUID id : persistentEntities) {
            Entity e = world.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        persistentEntities.clear();
    }
}
