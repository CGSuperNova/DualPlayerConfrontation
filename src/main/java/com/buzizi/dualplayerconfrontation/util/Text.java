package com.buzizi.dualplayerconfrontation.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

/** 文本与占位符工具 */
public final class Text {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Text() {
    }

    /** 转换 & 颜色代码以及 &#RRGGBB 十六进制颜色（1.16+ 支持） */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher m = HEX_COLOR.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00a7').append(c);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** 依次替换 key1, value1, key2, value2, ... 占位符 */
    public static String replace(String input, String... replacements) {
        String out = input;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            out = out.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return out;
    }

    /**
     * 同时支持 %key% 与 {key} 两种占位符风格的替换。
     */
    public static String placeholders(String input, String... replacements) {
        String out = input;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String key = replacements[i];
            String val = replacements[i + 1] == null ? "" : replacements[i + 1];
            out = out.replace("%" + key + "%", val).replace("{" + key + "}", val);
        }
        return out;
    }

    /** 数字格式化：整数部分保留，一位小数 */
    public static String num(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }

    /** 秒数格式化为 mm:ss */
    public static String time(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
