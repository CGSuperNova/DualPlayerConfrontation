package com.buzizi.dualplayerconfrontation.util;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

/** 跨版本兼容的小工具：粒子枚举值在不同MC版本名称会变化，这里做运行时探测 */
public final class BukkitCompat {

    private static Particle explosionParticle;
    private static Particle flashParticle;

    private BukkitCompat() {
    }

    private static Particle find(String primary, String fallback) {
        try {
            return Particle.valueOf(primary.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            try {
                return Particle.valueOf(fallback.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored2) {
                return null;
            }
        }
    }

    /** 大爆炸粒子（1.21 后更名为 EXPLOSION） */
    public static Particle explosionParticle() {
        if (explosionParticle == null) {
            explosionParticle = find("EXPLOSION_LARGE", "EXPLOSION");
        }
        return explosionParticle;
    }

    /** 爆炸闪光粒子 */
    public static Particle flashParticle() {
        if (flashParticle == null) {
            flashParticle = find("EXPLOSION_NORMAL", "SMALL_EXPLOSION");
        }
        return flashParticle;
    }

    public static void playExplosion(World world, double x, double y, double z) {
        Particle p = explosionParticle();
        if (p != null) {
            world.spawnParticle(p, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Particle f = flashParticle();
        if (f != null) {
            world.spawnParticle(f, x, y, z, 8, 1.0, 1.0, 1.0, 0.0);
        }
    }

    /** 设置游戏规则 */
    public static <T> void rule(World world, GameRule<T> gamerule, T value) {
        world.setGameRule(gamerule, value);
    }

    /**
     * 解析位置字符串 world:x,y,z[,yaw,pitch]
     * 返回 null 表示失败
     */
    public static Location parseLocation(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] head = text.split(":", 2);
        if (head.length != 2) {
            return null;
        }
        World world = Bukkit.getWorld(head[0]);
        if (world == null) {
            return null;
        }
        String[] parts = head[1].split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = parts.length > 3 ? Float.parseFloat(parts[3]) : 0f;
            float pitch = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 方块是否为无法被游戏破坏的类型 */
    public static boolean isUnyielding(Block block) {
        switch (block.getType()) {
            case BEDROCK:
            case BARRIER:
            case COMMAND_BLOCK:
            case CHAIN_COMMAND_BLOCK:
            case REPEATING_COMMAND_BLOCK:
            case STRUCTURE_BLOCK:
            case STRUCTURE_VOID:
            case END_PORTAL:
            case END_PORTAL_FRAME:
            case END_GATEWAY:
            case LIGHT:
            case JIGSAW:
                return true;
            default:
                return false;
        }
    }

    /** 是否为流体类方块（用名称判断以兼容不同版本的材料枚举） */
    public static boolean isLiquidType(Material material) {
        if (material == null) {
            return false;
        }
        String n = material.name();
        return n.equals("WATER") || n.equals("LAVA")
                || n.equals("BUBBLE_COLUMN")
                || n.equals("FLOWING_WATER") || n.equals("FLOWING_LAVA");
    }
}
