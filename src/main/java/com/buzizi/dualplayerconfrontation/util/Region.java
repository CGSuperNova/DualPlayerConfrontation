package com.buzizi.dualplayerconfrontation.util;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/** 不可变的整数立方体区域（两个对角角点，包含端点） */
public final class Region {

    private final Vector min;
    private final Vector max;

    public Region(Vector a, Vector b) {
        this.min = Vector.getMinimum(a, b).clone();
        this.max = Vector.getMaximum(a, b).clone();
    }

    public Region(int x1, int y1, int z1, int x2, int y2, int z2) {
        this(new Vector(x1, y1, z1), new Vector(x2, y2, z2));
    }

    public static Region parse(String text) throws IllegalArgumentException {
        String[] parts = text.trim().split("[,\\s]+");
        if (parts.length != 6) {
            throw new IllegalArgumentException("bad box");
        }
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            v[i] = Integer.parseInt(parts[i].trim());
        }
        return new Region(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    public Vector getMin() {
        return min.clone();
    }

    public Vector getMax() {
        return max.clone();
    }

    public boolean contains(int x, int y, int z) {
        return x >= min.getBlockX() && x <= max.getBlockX()
                && y >= min.getBlockY() && y <= max.getBlockY()
                && z >= min.getBlockZ() && z <= max.getBlockZ();
    }

    public boolean contains(Location loc) {
        return loc != null && contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int getMinX() {
        return min.getBlockX();
    }

    public int getMinY() {
        return min.getBlockY();
    }

    public int getMinZ() {
        return min.getBlockZ();
    }

    public int getMaxX() {
        return max.getBlockX();
    }

    public int getMaxY() {
        return max.getBlockY();
    }

    public int getMaxZ() {
        return max.getBlockZ();
    }

    /** 区域内某列最顶部的非空气方块；找不到返回 minY 处 */
    public Block topBlock(World world, int x, int z) {
        for (int y = max.getBlockY(); y >= min.getBlockY(); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (!b.getType().isAir()) {
                return b;
            }
        }
        return world.getBlockAt(x, min.getBlockY(), z);
    }

    @Override
    public String toString() {
        return min.getBlockX() + "," + min.getBlockY() + "," + min.getBlockZ()
                + " -> " + max.getBlockX() + "," + max.getBlockY() + "," + max.getBlockZ();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Region r)) {
            return false;
        }
        return min.equals(r.min) && max.equals(r.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }
}
