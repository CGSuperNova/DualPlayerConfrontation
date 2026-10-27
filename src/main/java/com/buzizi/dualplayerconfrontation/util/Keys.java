package com.buzizi.dualplayerconfrontation.util;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 持久数据标签（PDC）统一入口：给实体/物品打标记，
 * 用于区分“本插件生成的火球/苦力怕/TNT/导弹/弓箭”及其归属玩家。
 */
public final class Keys {

    private Keys() {
    }

    private static NamespacedKey KIND;
    private static NamespacedKey OWNER;
    private static NamespacedKey POWER;

    // 实体类型
    public static final String E_FIREBALL = "fireball_entity";
    public static final String E_FIRE_TNT = "fireball_tnt";
    public static final String E_TNT = "tnt";
    public static final String E_RAIN_TNT = "rain_tnt";
    public static final String E_CREEPER = "creeper_entity";
    public static final String E_CHARGED = "charged_creeper_entity";
    public static final String E_BULLET = "bullet";
    public static final String E_ARROW = "duel_arrow";
    public static final String E_BADMINTON_TNT = "badminton_tnt";

    // 道具类型
    public static final String I_FIREBALL = "fireball_item";
    public static final String I_BOW = "bow";
    public static final String I_EGG = "creeper_egg";
    public static final String I_CHARGED_EGG = "charged_creeper_egg";
    public static final String I_POWER = "power_tool";
    public static final String I_LAUNCHER = "bullet_launcher";
    public static final String I_PADDLE_SMALL = "paddle_small";
    public static final String I_PADDLE_BIG = "paddle_big";

    public static void init(Plugin plugin) {
        if (KIND != null) {
            return;
        }
        KIND = new NamespacedKey(plugin, "kind");
        OWNER = new NamespacedKey(plugin, "owner");
        POWER = new NamespacedKey(plugin, "power");
    }

    public static NamespacedKey kind() {
        return KIND;
    }

    public static NamespacedKey owner() {
        return OWNER;
    }

    public static NamespacedKey power() {
        return POWER;
    }

    // ---- 实体/物品标记 ----

    public static boolean hasKind(PersistentDataContainer c, String kind) {
        if (c == null || !c.has(kind())) {
            return false;
        }
        String s = c.get(kind(), PersistentDataType.STRING);
        return kind.equals(s);
    }

    public static String getKind(PersistentDataContainer c) {
        if (c == null || !c.has(kind())) {
            return null;
        }
        return c.get(kind(), PersistentDataType.STRING);
    }

    public static void setKind(PersistentDataContainer c, String kind) {
        c.set(kind(), PersistentDataType.STRING, kind);
    }

    public static String getOwner(PersistentDataContainer c) {
        if (c == null || !c.has(owner())) {
            return null;
        }
        return c.get(owner(), PersistentDataType.STRING);
    }

    public static void setOwner(PersistentDataContainer c, java.util.UUID uuid) {
        c.set(owner(), PersistentDataType.STRING, uuid.toString());
    }

    public static int getPower(PersistentDataContainer c, int def) {
        if (c == null || !c.has(power())) {
            return def;
        }
        Integer v = c.get(power(), PersistentDataType.INTEGER);
        return v == null ? def : v;
    }

    public static void setPower(PersistentDataContainer c, int power) {
        c.set(power(), PersistentDataType.INTEGER, power);
    }
}
