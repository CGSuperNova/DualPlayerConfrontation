package com.buzizi.dualplayerconfrontation.config;

import com.buzizi.dualplayerconfrontation.DualPlayerConfrontation;
import com.buzizi.dualplayerconfrontation.util.Text;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** messages.yml 的加载与发送 */
public final class Messages {

    private final DualPlayerConfrontation plugin;
    private FileConfiguration msg;

    public Messages(DualPlayerConfrontation plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        msg = YamlConfiguration.loadConfiguration(file);
        try (InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                msg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法读取默认 messages.yml: " + e.getMessage());
        }
    }

    /** 取原始（已配色）消息，未加前缀 */
    public String raw(String key, String... replacements) {
        String s = msg.getString(key, key);
        return Text.color(Text.placeholders(s, replacements));
    }

    /** 取多行消息列表（每行独立配色） */
    public List<String> rawList(String key, String... replacements) {
        List<String> lines = msg.getStringList(key);
        if (lines.isEmpty() && !msg.contains(key)) {
            lines = List.of(msg.getString(key, key));
        }
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(Text.color(Text.placeholders(line, replacements)));
        }
        return out;
    }

    /** 发送多行消息列表 */
    public void sendList(CommandSender sender, String key, String... replacements) {
        for (String line : rawList(key, replacements)) {
            sender.sendMessage(line);
        }
    }

    /** 带前缀的消息 */
    public String get(String key, String... replacements) {
        return Text.color(msg.getString("prefix", "&8[&6双军对峙&8] &7"))
                + raw(key, replacements);
    }

    public void send(CommandSender sender, String key, String... replacements) {
        if (sender == null) {
            return;
        }
        for (String line : get(key, replacements).split("\n")) {
            sender.sendMessage(line);
        }
    }

    public void sendNoPrefix(CommandSender sender, String key, String... replacements) {
        if (sender == null) {
            return;
        }
        for (String line : raw(key, replacements).split("\n")) {
            sender.sendMessage(line);
        }
    }
}
