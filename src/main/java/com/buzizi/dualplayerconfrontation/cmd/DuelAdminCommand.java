package com.buzizi.dualplayerconfrontation.cmd;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.data.MapDefinition;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.game.PlayerSide;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.stats.StarManager;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /dueladmin 管理命令：
 * force <p1> <p2> [map] / roundwin|roundlose|gamewin|gamelose <player> /
 * end [player] / games / stats <player> / reload / debug <player>
 */
public final class DuelAdminCommand implements CommandExecutor, TabCompleter {

    private final DualPlayerConfrontation plugin;

    public DuelAdminCommand(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "force" -> force(sender, args);
            case "roundwin" -> gameAction(sender, args, 1);
            case "roundlose" -> gameAction(sender, args, 2);
            case "gamewin" -> gameAction(sender, args, 3);
            case "gamelose" -> gameAction(sender, args, 4);
            case "end" -> end(sender, args);
            case "games" -> games(sender);
            case "stats" -> stats(sender, args);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void force(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(usage("force <玩家1> <玩家2> [map]"));
            return;
        }
        Player a = Bukkit.getPlayerExact(args[1]);
        Player b = Bukkit.getPlayerExact(args[2]);
        if (a == null || b == null) {
            plugin.messages().send(sender, "admin.player-not-found", "player",
                    a == null ? args[1] : args[2]);
            return;
        }
        MapDefinition map = null;
        if (args.length >= 4) {
            map = plugin.maps().get(args[3]);
            if (map == null) {
                plugin.messages().send(sender, "map.not-found", "map", args[3]);
                return;
            }
        }
        plugin.games().launchAdminMatch(a, b, map, sender);
        plugin.messages().send(sender, "admin.force-game-start", "p1", a.getName(), "p2", b.getName());
    }

    /** action: 1=roundwin 2=roundlose 3=gamewin 4=gamelose */
    private void gameAction(CommandSender sender, String[] args, int action) {
        if (args.length < 2) {
            String name = switch (action) {
                case 1 -> "roundwin";
                case 2 -> "roundlose";
                case 3 -> "gamewin";
                default -> "gamelose";
            };
            sender.sendMessage(usage(name + " <玩家>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "admin.player-not-found", "player", args[1]);
            return;
        }
        DuelGame game = plugin.games().getGameByPlayer(target.getUniqueId());
        if (game == null) {
            plugin.messages().send(sender, "admin.player-not-in-game", "player", target.getName());
            return;
        }
        switch (action) {
            case 1 -> game.forceRoundWin(target.getUniqueId());
            case 2 -> game.forceRoundLose(target.getUniqueId());
            case 3 -> game.forceGameWin(target.getUniqueId());
            case 4 -> {
                PlayerSide other = game.sideOf(target.getUniqueId()).opposite();
                UUID winnerUuid = game.getPlayerIds().get(other == PlayerSide.A ? 0 : 1);
                game.forceGameWin(winnerUuid);
            }
            default -> {
            }
        }
    }

    private void end(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean any = false;
            for (DuelGame game : plugin.games().allGames()) {
                game.forceEnd();
                any = true;
            }
            if (!any) {
                plugin.messages().send(sender, "admin.no-game");
            } else {
                plugin.messages().send(sender, "admin.game-ended");
            }
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "admin.player-not-found", "player", args[1]);
            return;
        }
        DuelGame game = plugin.games().getGameByPlayer(target.getUniqueId());
        if (game == null) {
            plugin.messages().send(sender, "admin.player-not-in-game", "player", target.getName());
            return;
        }
        game.forceEnd();
    }

    private void games(CommandSender sender) {
        var all = plugin.games().allGames();
        if (all.isEmpty()) {
            plugin.messages().send(sender, "admin.no-game");
            return;
        }
        plugin.messages().send(sender, "admin.games-header");
        for (DuelGame game : all) {
            Player a = game.playerBySide(PlayerSide.A);
            Player b = game.playerBySide(PlayerSide.B);
            plugin.messages().send(sender, "admin.games-entry",
                    "p1", a == null ? "?" : a.getName(),
                    "p2", b == null ? "?" : b.getName(),
                    "map", game.getMap().getDisplayName(),
                    "round", String.valueOf(game.getRound()),
                    "scoreA", String.valueOf(game.score(PlayerSide.A)),
                    "scoreB", String.valueOf(game.score(PlayerSide.B)));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(usage("stats <玩家>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerProfile profile = plugin.stats().find(target.getUniqueId()).orElse(null);
        if (profile == null || profile.getGames() == 0) {
            plugin.messages().send(sender, "stats.no-data");
            return;
        }
        Settings st = plugin.settings();
        int star = StarManager.starFromXp(st.starXpPerStar(), profile.getXp());
        plugin.messages().send(sender, "stats.title");
        for (String line : plugin.messages().rawList("stats.format",
                "games", String.valueOf(profile.getGames()),
                "wins", String.valueOf(profile.getWins()),
                "losses", String.valueOf(profile.getLosses()),
                "winrate", Text.num(profile.winRate()),
                "blocks", String.valueOf(profile.getBlocksDestroyed()),
                "avg_won", Text.num(profile.avgRoundTimeWon()),
                "avg_lost", Text.num(profile.avgRoundTimeLost()),
                "avg_all", Text.num(profile.avgRoundTimeAll()),
                "fireballs_shot", String.valueOf(profile.getFireballsShot()),
                "fireballs_hit", String.valueOf(profile.getFireballsHit()),
                "tnt_activated", String.valueOf(profile.getTntActivated()),
                "creepers", String.valueOf(profile.getCreepersThrown()),
                "charged", String.valueOf(profile.getChargedCreepersThrown()),
                "bullets_shot", String.valueOf(profile.getBulletsShot()),
                "bullets_hit", String.valueOf(profile.getBulletsHit()),
                "star", String.valueOf(star),
                "symbol", st.starSymbol(),
                "xp", String.valueOf(profile.getXp()),
                "progress", "")) {
            sender.sendMessage(line);
        }
    }

    private void reload(CommandSender sender) {
        plugin.settings().reload();
        plugin.messages().reload();
        plugin.maps().load();
        plugin.quests().reload();
        plugin.messages().send(sender, "admin.reloaded");
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(usage("debug <玩家>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "admin.player-not-found", "player", args[1]);
            return;
        }
        DuelGame game = plugin.games().getGameByPlayer(target.getUniqueId());
        if (game == null) {
            plugin.messages().send(sender, "admin.player-not-in-game", "player", target.getName());
            return;
        }
        sender.sendMessage(Text.color("&6对局调试: 玩家 &e" + target.getName()
                + " &7回合 &f" + game.getRound()
                + " &7阶段 &f" + game.getPhase()
                + " &7模式 &f" + (game.currentMode() == null ? "-" : game.currentMode().type())
                + " &7比分 &f" + game.score(PlayerSide.A) + ":" + game.score(PlayerSide.B)));
    }

    private String usage(String sub) {
        return Text.color("&c用法: /dueladmin " + sub);
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.color("&6&l双军对峙 管理命令"));
        sender.sendMessage(Text.color("&e/dueladmin force <p1> <p2> [map] &7- 强制两人开局"));
        sender.sendMessage(Text.color("&e/dueladmin roundwin|roundlose <player> &7- 强制某人赢/输本回合"));
        sender.sendMessage(Text.color("&e/dueladmin gamewin|gamelose <player> &7- 强制某人赢/输整局"));
        sender.sendMessage(Text.color("&e/dueladmin end [player] &7- 结束对局(不结算)"));
        sender.sendMessage(Text.color("&e/dueladmin games / stats <player> / reload / debug <player>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("force", "roundwin", "roundlose", "gamewin", "gamelose", "end", "games", "stats", "reload", "debug", "help")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(p.getName());
                }
            }
        }
        return list;
    }
}
