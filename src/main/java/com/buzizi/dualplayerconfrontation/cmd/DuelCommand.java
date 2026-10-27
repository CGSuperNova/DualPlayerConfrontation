package com.buzizi.dualplayerconfrontation.cmd;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.stats.StarManager;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /duel 玩家命令：join / leave / stats / help */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final DualPlayerConfrontation plugin;

    public DuelCommand(DualPlayerConfrontation plugin) {
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
            case "join", "queue", "play" -> join(player);
            case "leave", "quit" -> leave(player);
            case "stats" -> stats(player, args.length > 1 ? args[1] : null);
            case "quests", "task" -> quests(player);
            case "help" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void quests(Player player) {
        PlayerProfile profile = plugin.stats().get(player);
        for (String line : plugin.quests().listLines(profile)) {
            player.sendMessage(line);
        }
    }

    private void join(Player player) {
        if (!player.hasPermission("duel.play")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        DuelGame game = plugin.games().getGameByPlayer(player.getUniqueId());
        if (game != null) {
            plugin.messages().send(player, "queue.in-game");
            return;
        }
        boolean added = plugin.queue().join(player);
        if (added) {
            plugin.messages().send(player, "queue.joined-with-position", "pos", String.valueOf(plugin.queue().size()));
        } else {
            plugin.messages().send(player, "queue.already");
        }
    }

    private void leave(Player player) {
        boolean removed = plugin.queue().leave(player.getUniqueId());
        plugin.messages().send(player, removed ? "queue.left" : "queue.not-in-queue");
    }

    private void stats(Player player, String targetName) {
        OfflinePlayer target = targetName == null ? player : Bukkit.getPlayerExact(targetName);
        if (target == null && targetName != null) {
            target = Bukkit.getOfflinePlayer(targetName);
        }
        if (target == null || target.getUniqueId() == null) {
            plugin.messages().send(player, "stats.no-data");
            return;
        }
        PlayerProfile profile = plugin.stats().find(target.getUniqueId()).orElse(null);
        if (profile == null || profile.getGames() == 0) {
            plugin.messages().send(player, "stats.no-data");
            return;
        }
        Settings st = plugin.settings();
        int star = StarManager.starFromXp(st.starXpPerStar(), profile.getXp());
        double need = StarManager.xpRequiredForStar(st.starXpPerStar(), star);
        double into = StarManager.xpIntoStar(st.starXpPerStar(), profile.getXp());
        int pct = (int) Math.floor(into / Math.max(1, need) * 100);
        plugin.messages().sendNoPrefix(player, "stats.title");
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
                "progress", star + st.starSymbol() + " " + Text.num(into) + "/" + Text.num(need) + " (" + pct + "%)")) {
            player.sendMessage(line);
        }
    }

    private void help(Player player) {
        plugin.messages().sendList(player, "queue.help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("join", "leave", "stats", "quests", "help")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
        }
        return list;
    }
}
