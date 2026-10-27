package com.buzizi.dualplayerconfrontation.papi;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.config.Settings;
import com.buzizi.dualplayerconfrontation.game.DuelGame;
import com.buzizi.dualplayerconfrontation.game.PlayerSide;
import com.buzizi.dualplayerconfrontation.stats.PlayerProfile;
import com.buzizi.dualplayerconfrontation.stats.StarManager;
import com.buzizi.dualplayerconfrontation.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI 支持：%duel_...%
 *
 * 数据类：
 *  duel_games / duel_wins / duel_losses / duel_winrate
 *  duel_blocks_destroyed / duel_fireballs_shot / duel_fireballs_hit
 *  duel_tnt_activated / duel_creepers_thrown / duel_charged_creepers_thrown
 *  duel_bullets_shot / duel_bullets_hit
 *  duel_avg_round_won / duel_avg_round_lost / duel_avg_round_all
 *  duel_star / duel_stars / duel_xp / duel_xp_to_next / duel_xp_into_star / duel_xp_progress
 *  duel_prefix（形如 [1⭐] ）
 *
 * 实时类（需玩家在线且在对局/队列中）：
 *  duel_in_queue / duel_in_game / duel_game_round / duel_game_mode /
 *  duel_game_score / duel_game_opponent_score / duel_game_opponent
 */
public final class DuelExpansion extends PlaceholderExpansion {

    private final DualPlayerConfrontation plugin;

    public DuelExpansion(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "duel";
    }

    @Override
    public String getAuthor() {
        return "buzizi";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String identifier) {
        if (offline == null) {
            return "";
        }
        String id = identifier.toLowerCase();
        Settings st = plugin.settings();

        // ---------- 实时 ----------
        if (id.startsWith("game_") || id.equals("in_queue") || id.equals("in_game")
                || id.equals("prefix")) {
            DuelGame game = plugin.games().getGameByPlayer(offline.getUniqueId());
            switch (id) {
                case "in_queue" -> {
                    return plugin.queue().isQueued(offline.getUniqueId()) ? "yes" : "no";
                }
                case "in_game" -> {
                    return game != null ? "yes" : "no";
                }
                case "game_round" -> {
                    return game == null ? "0" : String.valueOf(game.getRound());
                }
                case "game_mode" -> {
                    return game == null || game.currentMode() == null ? "-" : game.currentMode().type().name();
                }
                case "game_score" -> {
                    if (game == null) {
                        return "0";
                    }
                    return String.valueOf(game.score(game.sideOf(offline.getUniqueId())));
                }
                case "game_opponent_score" -> {
                    if (game == null) {
                        return "0";
                    }
                    return String.valueOf(game.score(game.sideOf(offline.getUniqueId()).opposite()));
                }
                case "game_opponent" -> {
                    if (game == null) {
                        return "-";
                    }
                    var p = game.playerBySide(game.sideOf(offline.getUniqueId()).opposite());
                    return p == null ? "?" : p.getName();
                }
                case "prefix" -> {
                    PlayerProfile prof = plugin.stats().find(offline.getUniqueId()).orElse(null);
                    int star = prof == null ? st.starInitial()
                            : StarManager.starFromXp(st.starXpPerStar(), prof.getXp());
                    return "[" + star + st.starSymbol() + "] ";
                }
                default -> {
                }
            }
        }

        // ---------- 数据 ----------
        PlayerProfile prof = plugin.stats().find(offline.getUniqueId()).orElse(null);
        if (prof == null) {
            switch (id) {
                case "star", "stars" -> {
                    return String.valueOf(st.starInitial());
                }
                case "xp" -> {
                    return "0";
                }
                case "xp_to_next" -> {
                    return String.valueOf(st.starXpPerStar().get(0));
                }
                case "xp_into_star", "xp_progress" -> {
                    return "0";
                }
                case "prefix" -> {
                    return "[" + st.starInitial() + st.starSymbol() + "] ";
                }
                default -> {
                    return statsZero(id);
                }
            }
        }
        switch (id) {
            case "star", "stars" -> {
                return String.valueOf(StarManager.starFromXp(st.starXpPerStar(), prof.getXp()));
            }
            case "xp" -> {
                return String.valueOf(prof.getXp());
            }
            case "xp_to_next" -> {
                int star = StarManager.starFromXp(st.starXpPerStar(), prof.getXp());
                return Text.num(StarManager.xpRequiredForStar(st.starXpPerStar(), star));
            }
            case "xp_into_star" -> {
                return Text.num(StarManager.xpIntoStar(st.starXpPerStar(), prof.getXp()));
            }
            case "xp_progress" -> {
                int star = StarManager.starFromXp(st.starXpPerStar(), prof.getXp());
                double need = StarManager.xpRequiredForStar(st.starXpPerStar(), star);
                double into = StarManager.xpIntoStar(st.starXpPerStar(), prof.getXp());
                return String.valueOf((int) Math.floor(into / Math.max(1, need) * 100));
            }
            case "games" -> {
                return String.valueOf(prof.getGames());
            }
            case "wins" -> {
                return String.valueOf(prof.getWins());
            }
            case "losses" -> {
                return String.valueOf(prof.getLosses());
            }
            case "winrate" -> {
                return Text.num(prof.winRate());
            }
            case "blocks_destroyed" -> {
                return String.valueOf(prof.getBlocksDestroyed());
            }
            case "fireballs_shot" -> {
                return String.valueOf(prof.getFireballsShot());
            }
            case "fireballs_hit" -> {
                return String.valueOf(prof.getFireballsHit());
            }
            case "tnt_activated" -> {
                return String.valueOf(prof.getTntActivated());
            }
            case "creepers_thrown" -> {
                return String.valueOf(prof.getCreepersThrown());
            }
            case "charged_creepers_thrown" -> {
                return String.valueOf(prof.getChargedCreepersThrown());
            }
            case "bullets_shot" -> {
                return String.valueOf(prof.getBulletsShot());
            }
            case "bullets_hit" -> {
                return String.valueOf(prof.getBulletsHit());
            }
            case "avg_round_won" -> {
                return Text.num(prof.avgRoundTimeWon());
            }
            case "avg_round_lost" -> {
                return Text.num(prof.avgRoundTimeLost());
            }
            case "avg_round_all" -> {
                return Text.num(prof.avgRoundTimeAll());
            }
            default -> {
                return null;
            }
        }
    }

    private String statsZero(String id) {
        switch (id) {
            case "games":
            case "wins":
            case "losses":
            case "blocks_destroyed":
            case "fireballs_shot":
            case "fireballs_hit":
            case "tnt_activated":
            case "creepers_thrown":
            case "charged_creepers_thrown":
            case "bullets_shot":
            case "bullets_hit":
                return "0";
            case "winrate":
            case "avg_round_won":
            case "avg_round_lost":
            case "avg_round_all":
                return "0.0";
            default:
                return "0";
        }
    }
}
