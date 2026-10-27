package com.buzizi.dualplayerconfrontation.cmd;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import com.buzizi.dualplayerconfrontation.util.Region;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * /duelmap 地图编辑命令：
 * create / remove / list / info / tp /
 * center <id> [x y z] / tower <id> <a|b> [两角点] / spawn <id> <a|b> [x y z] / enable / disable
 */
public final class DuelMapCommand implements CommandExecutor, TabCompleter {

    private static final class TowerEdit {
        String map;
        String side;
        Vector first;

        boolean matches(String m, String s) {
            return map != null && map.equals(m) && side != null && side.equals(s);
        }
    }

    private final DualPlayerConfrontation plugin;
    private final Map<UUID, TowerEdit> edits = new HashMap<>();

    public DuelMapCommand(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(player, args);
            case "remove" -> remove(player, args);
            case "list" -> list(player);
            case "info" -> info(player, args);
            case "tp" -> tp(player, args);
            case "center" -> setCenter(player, args);
            case "tower" -> setTower(player, args);
            case "spawn" -> setSpawn(player, args);
            case "enable" -> setEnabled(player, args, true);
            case "disable" -> setEnabled(player, args, false);
            default -> help(player);
        }
        return true;
    }

    // ---------- create ----------

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(usage("create <id> [world]"));
            return;
        }
        String id = args[1];
        if (plugin.maps().get(id) != null) {
            plugin.messages().send(player, "map.exists", "map", id);
            return;
        }
        World world;
        if (args.length >= 3) {
            world = Bukkit.getWorld(args[2]);
            if (world == null) {
                plugin.messages().send(player, "map.world-not-loaded", "world", args[2]);
                return;
            }
        } else {
            world = player.getWorld();
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        String prefix = plugin.settings().arenaNamePrefix().toLowerCase(Locale.ROOT);
        if (worldName.startsWith(prefix + "-") || worldName.startsWith("duel_")) {
            plugin.messages().send(player, "map.world-not-editable");
            return;
        }
        MapDefinition def = plugin.maps().create(id, world);
        if (def == null) {
            plugin.messages().send(player, "map.exists", "map", id);
            return;
        }
        plugin.messages().send(player, "map.created", "map", def.getDisplayName(), "world", world.getName());
        plugin.messages().send(player, "map.select-world-hint");
    }

    private void remove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(usage("remove <id>"));
            return;
        }
        boolean ok = plugin.maps().remove(args[1]);
        plugin.messages().send(player, ok ? "map.removed" : "map.not-found", "map", args[1]);
    }

    private void list(Player player) {
        plugin.messages().send(player, "map.list-header");
        for (MapDefinition def : plugin.maps().all()) {
            plugin.messages().send(player, "map.list-entry",
                    "map", def.getDisplayName(),
                    "world", def.getWorldName() == null ? "?" : def.getWorldName(),
                    "enabled", def.isEnabled() ? "是" : "否");
        }
    }

    private void info(Player player, String[] args) {
        MapDefinition def = resolveMap(player, args);
        if (def == null) {
            return;
        }
        player.sendMessage(Text.color("&6" + def.getDisplayName() + " &7(" + def.getId() + ")"));
        player.sendMessage(Text.color("&7世界: &f" + def.getWorldName() + " &7环境: &f" + def.getEnvironment()
                + " &7启用: &f" + def.isEnabled()));
        if (def.getCenter() != null) {
            player.sendMessage(Text.color("&7中心: &f" + vec(def.getCenter())));
        } else {
            player.sendMessage(Text.color("&c中心未设置"));
        }
        player.sendMessage(Text.color("&7A塔: &f" + (def.getTowerA() == null ? "未设置" : def.getTowerA().toString())));
        player.sendMessage(Text.color("&7B塔: &f" + (def.getTowerB() == null ? "未设置" : def.getTowerB().toString())));
        player.sendMessage(Text.color("&7A出生点: &f" + (def.getSpawnA() == null ? "自动(塔顶)" : spawn(def.getSpawnA()))));
        player.sendMessage(Text.color("&7B出生点: &f" + (def.getSpawnB() == null ? "自动(塔顶)" : spawn(def.getSpawnB()))));
        player.sendMessage(Text.color(def.isComplete() ? "&a该地图配置完整，可以用于对局" : "&c该地图配置未完成"));
    }

    private void tp(Player player, String[] args) {
        MapDefinition def = resolveMap(player, args);
        if (def == null) {
            return;
        }
        World world = Bukkit.getWorld(def.getWorldName());
        if (world == null) {
            plugin.messages().send(player, "map.world-not-loaded", "world", def.getWorldName());
            return;
        }
        player.teleport(world.getSpawnLocation());
        plugin.messages().send(player, "map.tp", "map", def.getDisplayName());
    }

    // ---------- center / tower / spawn ----------

    private void setCenter(Player player, String[] args) {
        MapDefinition def = resolveMap(player, args);
        if (def == null) {
            return;
        }
        Vector v = parseVec(player, args, 2);
        def.setCenter(v);
        save(player, def);
        plugin.messages().send(player, "map.center-set",
                "x", String.valueOf(v.getBlockX()),
                "y", String.valueOf(v.getBlockY()),
                "z", String.valueOf(v.getBlockZ()));
    }

    private void setTower(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(usage("tower <id> <a|b> [x1 y1 z1 x2 y2 z2]"));
            return;
        }
        MapDefinition def = resolveMapById(player, args[1]);
        if (def == null) {
            return;
        }
        String side = args[2].toLowerCase(Locale.ROOT);
        if (!side.equals("a") && !side.equals("b") && !side.equals("1") && !side.equals("2")) {
            player.sendMessage(usage("tower <id> <a|b>"));
            return;
        }
        boolean sideA = side.equals("a") || side.equals("1");
        String sideName = sideA ? "A" : "B";

        if (args.length >= 9) {
            Vector corner1 = tryParseVec(args, 3);
            Vector corner2 = tryParseVec(args, 6);
            if (corner1 == null || corner2 == null) {
                plugin.messages().send(player, "map.bad-box");
                return;
            }
            setTowerRegion(def, sideA, corner1, corner2);
            save(player, def);
            plugin.messages().send(player, "map.tower-set", "side", sideName,
                    "min", vec(corner1), "max", vec(corner2));
            return;
        }
        Vector loc = player.getLocation().toVector();
        TowerEdit edit = edits.computeIfAbsent(player.getUniqueId(), k -> new TowerEdit());
        if (!edit.matches(def.getId(), side)) {
            edit.map = def.getId();
            edit.side = side;
            edit.first = null;
        }
        if (edit.first == null) {
            edit.first = loc;
            plugin.messages().send(player, "map.tower-pos1", "side", sideName,
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ()));
        } else {
            Vector a = edit.first;
            Vector b = loc;
            setTowerRegion(def, sideA, a, b);
            edit.first = null;
            save(player, def);
            plugin.messages().send(player, "map.tower-set", "side", sideName,
                    "min", vec(a), "max", vec(b));
        }
    }

    private void setTowerRegion(MapDefinition def, boolean sideA, Vector c1, Vector c2) {
        Region region = new Region(c1, c2);
        if (sideA) {
            def.setTowerA(region);
        } else {
            def.setTowerB(region);
        }
    }

    private void setSpawn(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(usage("spawn <id> <a|b> [x y z]"));
            return;
        }
        MapDefinition def = resolveMapById(player, args[1]);
        if (def == null) {
            return;
        }
        String side = args[2].toLowerCase(Locale.ROOT);
        boolean sideA = side.equals("a") || side.equals("1");
        String sideName = sideA ? "A" : "B";
        Location loc;
        if (args.length >= 6) {
            try {
                double x = Double.parseDouble(args[3]);
                double y = Double.parseDouble(args[4]);
                double z = Double.parseDouble(args[5]);
                loc = new Location(player.getWorld(), x, y, z,
                        player.getLocation().getYaw(), player.getLocation().getPitch());
            } catch (NumberFormatException e) {
                plugin.messages().send(player, "map.bad-box");
                return;
            }
        } else {
            loc = player.getLocation();
        }
        MapDefinition.Spawn spawn = new MapDefinition.Spawn(
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        if (sideA) {
            def.setSpawnA(spawn);
        } else {
            def.setSpawnB(spawn);
        }
        save(player, def);
        plugin.messages().send(player, "map.spawn-set", "side", sideName);
    }

    private void setEnabled(Player player, String[] args, boolean enabled) {
        MapDefinition def = resolveMap(player, args);
        if (def == null) {
            return;
        }
        def.setEnabled(enabled);
        plugin.maps().save();
        plugin.messages().send(player, enabled ? "map.enabled" : "map.disabled", "map", def.getDisplayName());
    }

    // ---------- 工具 ----------

    private void save(Player player, MapDefinition def) {
        plugin.maps().save();
        if (def.isComplete()) {
            plugin.messages().send(player, "map.saved");
        } else {
            player.sendMessage(Text.color("&7提示: 仍需设置地图中心与两座塔才能开始使用该地图。"));
        }
    }

    private MapDefinition resolveMap(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(usage("操作需要指定地图 id"));
            return null;
        }
        return resolveMapById(player, args[1]);
    }

    private MapDefinition resolveMapById(Player player, String id) {
        MapDefinition def = plugin.maps().get(id);
        if (def == null) {
            plugin.messages().send(player, "map.not-found", "map", id);
        }
        return def;
    }

    private Vector parseVec(Player player, String[] args, int from) {
        if (args.length >= from + 3) {
            Vector v = tryParseVec(args, from);
            if (v != null) {
                return v;
            }
        }
        return player.getLocation().toVector();
    }

    private Vector tryParseVec(String[] args, int from) {
        try {
            return new Vector(Integer.parseInt(args[from]), Integer.parseInt(args[from + 1]),
                    Integer.parseInt(args[from + 2]));
        } catch (Exception e) {
            return null;
        }
    }

    private String vec(Vector v) {
        return v.getBlockX() + "," + v.getBlockY() + "," + v.getBlockZ();
    }

    private String spawn(MapDefinition.Spawn s) {
        return (int) s.x + "," + (int) s.y + "," + (int) s.z;
    }

    private String usage(String sub) {
        return Text.color("&c用法: /duelmap " + sub);
    }

    private void help(Player player) {
        plugin.messages().send(player, "map.help-title");
        player.sendMessage(Text.color("&e/duelmap create <id> [world] &7- 以当前/指定世界为模板创建地图"));
        player.sendMessage(Text.color("&e/duelmap center <id> [x y z] &7- 站在中心点设置地图中心"));
        player.sendMessage(Text.color("&e/duelmap tower <id> <a|b> &7- 传送塔区并两次执行记录两个对角点"));
        player.sendMessage(Text.color("&e/duelmap tower <id> <a|b> <x1 y1 z1 x2 y2 z2> &7- 直接输入塔区域"));
        player.sendMessage(Text.color("&e/duelmap spawn <id> <a|b> [x y z] &7- 站在塔顶设置玩家出生点"));
        player.sendMessage(Text.color("&e/duelmap list / info <id> / tp <id> / remove <id> / enable|disable <id>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("create", "remove", "list", "info", "tp", "center", "tower", "spawn", "enable", "disable", "help")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            for (MapDefinition def : plugin.maps().all()) {
                if (def.getId().startsWith(args[1].toLowerCase())) {
                    list.add(def.getId());
                }
            }
        }
        return list;
    }
}
