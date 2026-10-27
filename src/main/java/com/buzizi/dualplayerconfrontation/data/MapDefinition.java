package com.buzizi.dualplayerconfrontation.data;

import com.buzizi.dualplayerconfrontation.util.Region;
import com.buzizi.dualplayerconfrontation.util.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

/**
 * 一张对战地图的定义：模板世界、中心点、双方塔区域与出生点。
 * 塔区域用世界坐标下的两个对角点表示（例如 -10,40,-10 20,60,20）。
 */
public final class MapDefinition {

    public static final class Spawn {
        public final double x, y, z, yaw, pitch;

        public Spawn(double x, double y, double z, double yaw, double pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Location toLocation(World world) {
            return new Location(world, x, y, z, (float) yaw, (float) pitch);
        }
    }

    private final String id;
    private String displayName;
    private String worldName;
    private String folder;
    private String environment;
    private Vector center;
    private Region towerA;
    private Region towerB;
    private Spawn spawnA;
    private Spawn spawnB;
    private boolean enabled = true;
    private long lastUsed = 0L;

    public MapDefinition(String id) {
        this.id = id.toLowerCase();
        this.displayName = id;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getEnvironment() {
        return environment == null ? "NORMAL" : environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Vector getCenter() {
        return center == null ? null : center.clone();
    }

    public void setCenter(Vector center) {
        this.center = center == null ? null : center.clone();
    }

    public Region getTowerA() {
        return towerA;
    }

    public void setTowerA(Region towerA) {
        this.towerA = towerA;
    }

    public Region getTowerB() {
        return towerB;
    }

    public void setTowerB(Region towerB) {
        this.towerB = towerB;
    }

    public Spawn getSpawnA() {
        return spawnA;
    }

    public void setSpawnA(Spawn spawnA) {
        this.spawnA = spawnA;
    }

    public Spawn getSpawnB() {
        return spawnB;
    }

    public void setSpawnB(Spawn spawnB) {
        this.spawnB = spawnB;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void markUsed() {
        this.lastUsed = System.currentTimeMillis();
    }

    /** 是否配置完整可用 */
    public boolean isComplete() {
        return worldName != null && folder != null && center != null && towerA != null && towerB != null;
    }

    /** 塔顶出生点：优先已配置出生点，否则在塔区域中心列顶部找实心方块上方 */
    public Location resolveSpawn(World world, Region tower, Spawn fallback, boolean sideA) {
        if (fallback != null) {
            return fallback.toLocation(world);
        }
        int cx = (tower.getMinX() + tower.getMaxX()) / 2;
        int cz = (tower.getMinZ() + tower.getMaxZ()) / 2;
        int topY = tower.topBlock(world, cx, cz).getY();
        Location loc = new Location(world, cx + 0.5, topY + 1.0, cz + 0.5);
        loc.setYaw(sideA ? 0f : 180f);
        return loc;
    }

    public void saveTo(ConfigurationSection sec) {
        sec.set("display-name", displayName);
        sec.set("world", worldName);
        sec.set("folder", folder);
        sec.set("environment", environment);
        sec.set("enabled", enabled);
        sec.set("last-used", lastUsed);
        if (center != null) {
            sec.set("center", center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());
        }
        if (towerA != null) {
            sec.set("tower-a", regionString(towerA));
        }
        if (towerB != null) {
            sec.set("tower-b", regionString(towerB));
        }
        if (spawnA != null) {
            sec.set("spawn-a", spawnString(spawnA));
        }
        if (spawnB != null) {
            sec.set("spawn-b", spawnString(spawnB));
        }
    }

    public static MapDefinition loadFrom(String id, ConfigurationSection sec) {
        MapDefinition def = new MapDefinition(id);
        def.displayName = sec.getString("display-name", id);
        def.worldName = sec.getString("world");
        def.folder = sec.getString("folder");
        def.environment = sec.getString("environment", "NORMAL");
        def.enabled = sec.getBoolean("enabled", true);
        def.lastUsed = sec.getLong("last-used", 0L);
        def.center = parseVector(sec.getString("center"));
        def.towerA = parseRegion(sec.getString("tower-a"));
        def.towerB = parseRegion(sec.getString("tower-b"));
        def.spawnA = parseSpawn(sec.getString("spawn-a"));
        def.spawnB = parseSpawn(sec.getString("spawn-b"));
        return def;
    }

    private static String regionString(Region r) {
        return r.getMinX() + "," + r.getMinY() + "," + r.getMinZ()
                + ";" + r.getMaxX() + "," + r.getMaxY() + "," + r.getMaxZ();
    }

    private static Region parseRegion(String s) {
        if (s == null) {
            return null;
        }
        String[] halves = s.split(";");
        if (halves.length != 2) {
            return null;
        }
        try {
            String[] a = halves[0].split(",");
            String[] b = halves[1].split(",");
            return new Region(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                    Integer.parseInt(b[0]), Integer.parseInt(b[1]), Integer.parseInt(b[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Vector parseVector(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length != 3) {
            return null;
        }
        try {
            return new Vector(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String spawnString(Spawn s) {
        return s.x + "," + s.y + "," + s.z + "," + s.yaw + "," + s.pitch;
    }

    private static Spawn parseSpawn(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length != 5) {
            return null;
        }
        try {
            return new Spawn(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String describe() {
        return Text.color("&e" + displayName + " &7- 世界:&f" + worldName
                + " &7启用:&f" + (enabled ? "&a是" : "&c否")
                + (isComplete() ? "" : " &c(配置未完成)"));
    }
}
