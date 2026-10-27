package com.buzizi.dualplayerconfrontation.manager;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import com.buzizi.dualplayerconfrontation.game.ArenaInstance;
import com.buzizi.dualplayerconfrontation.util.BukkitCompat;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * 竞技场世界管理器。
 *
 * 副本世界使用【固定的槽位世界名】：&lt;prefix&gt;-1 … &lt;prefix&gt;-&lt;slots&gt;（默认 duel-1 … duel-8）。
 * 对局开始前把“地图模板世界”文件夹复制到槽位并加载为独立世界，结束后卸载并删除。
 * 固定世界名的好处：DeluxeHub 等插件可把这些世界名加入自己的“黑名单/不管理世界”列表，
 * 从而保证对局期间不被大厅类插件的保护/行为管理干涉。
 */
public final class ArenaWorldManager {

    private final DualPlayerConfrontation plugin;
    private final File worldRoot;
    private final String prefix;
    private final int slots;
    private final java.util.Set<Integer> usedSlots = new java.util.HashSet<>();

    public ArenaWorldManager(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
        this.worldRoot = plugin.getServer().getWorldContainer();
        this.prefix = plugin.settings().arenaNamePrefix();
        this.slots = plugin.settings().arenaSlots();
    }

    private String slotName(int slot) {
        return prefix + "-" + slot;
    }

    /** 分配一个未使用的槽位（主线程调用）；没有空闲返回 0 */
    private int allocateSlot() {
        for (int i = 1; i <= slots; i++) {
            String name = slotName(i);
            if (!usedSlots.contains(i) && Bukkit.getWorld(name) == null) {
                usedSlots.add(i);
                return i;
            }
        }
        return 0;
    }

    private void releaseSlot(int slot) {
        usedSlots.remove(slot);
    }

    /** 服务器启动后清理上次遗留的副本世界文件夹 */
    public void purgeStaleArenas() {
        File[] children = worldRoot.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            String name = child.getName();
            if (!name.startsWith(prefix + "-")) {
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(name.substring(prefix.length() + 1));
            } catch (NumberFormatException e) {
                continue; // 非本插件的数字槽位世界，不处理
            }
            if (slot < 1 || slot > slots) {
                continue;
            }
            plugin.getLogger().info("清理遗留竞技场槽位副本: " + name);
            deleteRecursively(child.toPath());
        }
    }

    /**
     * 准备竞技场（主线程调用）。
     * 成功: callback.accept(instance, null)；失败: callback.accept(null, 错误)
     */
    public void prepare(MapDefinition map, BiConsumer<ArenaInstance, String> callback) {
        final int slot = allocateSlot();
        if (slot == 0) {
            callback.accept(null, "没有空闲竞技场槽位（" + prefix + "-1 ~ " + prefix + "-" + slots + " 均在用）");
            return;
        }
        final String instanceName = slotName(slot);
        File source = new File(map.getFolder());
        if (!source.isDirectory()) {
            releaseSlot(slot);
            callback.accept(null, "地图模板文件夹不存在: " + map.getFolder());
            return;
        }
        // 模板若已加载，先落盘保证复制内容最新
        World srcWorld = Bukkit.getWorld(map.getWorldName());
        if (srcWorld != null) {
            try {
                srcWorld.save();
            } catch (Exception ignored) {
            }
        }
        final File dst = new File(worldRoot, instanceName);
        deleteRecursively(dst.toPath());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean copied = copyWorldFolder(source.toPath(), dst.toPath());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!copied) {
                    releaseSlot(slot);
                    callback.accept(null, "复制地图世界失败: " + map.getWorldName());
                    return;
                }
                World world = loadInstance(map, instanceName);
                if (world == null) {
                    releaseSlot(slot);
                    callback.accept(null, "地图世界加载失败: " + map.getWorldName());
                    return;
                }
                Location center = new Location(world,
                        map.getCenter().getX() + 0.5, map.getCenter().getY(), map.getCenter().getZ() + 0.5);
                Location spawnA = map.resolveSpawn(world, map.getTowerA(), map.getSpawnA(), true);
                Location spawnB = map.resolveSpawn(world, map.getTowerB(), map.getSpawnB(), false);
                ArenaInstance arena = new ArenaInstance(map, world, instanceName, dst, spawnA, spawnB, center);
                callback.accept(arena, null);
            });
        });
    }

    private World loadInstance(MapDefinition map, String name) {
        WorldCreator creator = WorldCreator.name(name);
        try {
            World.Environment env = World.Environment.valueOf(map.getEnvironment().toUpperCase(Locale.ROOT));
            creator.environment(env);
        } catch (IllegalArgumentException ignored) {
            // 沿用 level.dat 中的环境
        }
        World world;
        try {
            world = creator.createWorld();
        } catch (Exception e) {
            plugin.getLogger().warning("加载竞技场世界失败 " + name + ": " + e.getMessage());
            return null;
        }
        if (world == null) {
            return null;
        }
        configureWorld(world);
        return world;
    }

    /** 对副本世界做统一设置（对局内规则由事件监听处理） */
    private void configureWorld(World world) {
        world.setAutoSave(false);
        BukkitCompat.rule(world, GameRule.DO_MOB_SPAWNING, false);
        BukkitCompat.rule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        BukkitCompat.rule(world, GameRule.DO_WEATHER_CYCLE, false);
        BukkitCompat.rule(world, GameRule.DO_FIRE_TICK, false);
        BukkitCompat.rule(world, GameRule.MOB_GRIEFING, true);
        BukkitCompat.rule(world, GameRule.ANNOUNCE_ADVANCEMENTS, false);
        BukkitCompat.rule(world, GameRule.KEEP_INVENTORY, true);
        BukkitCompat.rule(world, GameRule.DO_MOB_LOOT, false);
        BukkitCompat.rule(world, GameRule.DO_TILE_DROPS, false);
        BukkitCompat.rule(world, GameRule.DO_ENTITY_DROPS, false);
        BukkitCompat.rule(world, GameRule.RANDOM_TICK_SPEED, 0);
        try {
            BukkitCompat.rule(world, GameRule.SPAWN_RADIUS, 0);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            BukkitCompat.rule(world, GameRule.DO_INSOMNIA, false);
        } catch (IllegalArgumentException ignored) {
        }
        world.setMonsterSpawnLimit(0);
        world.setAnimalSpawnLimit(0);
        world.setAmbientSpawnLimit(0);
        world.setWaterAnimalSpawnLimit(0);
        world.setPVP(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(1000);
        world.setDifficulty(Difficulty.NORMAL);
    }

    /** 主线程：卸载并删除竞技场副本，并释放槽位 */
    public void dispose(ArenaInstance arena) {
        if (arena == null) {
            return;
        }
        releaseSlot(slotOf(arena.getInstanceName()));
        World world = arena.getWorld();
        if (world != null) {
            try {
                world.setAutoSave(false);
                Bukkit.unloadWorld(world, false);
            } catch (Exception e) {
                plugin.getLogger().warning("卸载竞技场世界失败: " + e.getMessage());
            }
        }
        File folder = arena.getFolder();
        if (folder != null && folder.exists()) {
            Path path = folder.toPath();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteRecursively(path));
        }
    }

    private int slotOf(String instanceName) {
        if (instanceName == null || !instanceName.startsWith(prefix + "-")) {
            return 0;
        }
        try {
            int slot = Integer.parseInt(instanceName.substring(prefix.length() + 1));
            return slot >= 1 && slot <= slots ? slot : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean copyWorldFolder(Path source, Path target) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.equals("session.lock") || name.equals("uid.dat")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path targetDir = target.resolve(source.relativize(dir).toString());
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName() == null ? "" : file.getFileName().toString();
                    if (name.equals("session.lock") || name.equals("uid.dat")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path dest = target.resolve(source.relativize(file).toString());
                    try {
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (IOException e) {
                        plugin.getLogger().warning("复制世界文件失败(跳过): " + file + " - " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("复制世界失败: " + e.getMessage());
            return false;
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // 文件可能被占用，稍后由下次启动清理
        }
    }
}
