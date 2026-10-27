package com.buzizi.dualplayerconfrontation.game;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * 方块改动日志：记录回合内每个方块“最初被改动前的状态”，
 * 回合结束时一次性恢复，从而把竞技场还原到回合开始时的样貌。
 * 只记录第一次改动，保证回滚到最原始状态。
 */
public final class BlockChangeLog {

    private final Map<Location, BlockData> changes = new HashMap<>();

    public void record(Block block) {
        Location loc = block.getLocation();
        if (!changes.containsKey(loc)) {
            changes.put(loc, block.getBlockData().clone());
        }
    }

    public void record(Location loc) {
        if (loc.getWorld() == null) {
            return;
        }
        if (!changes.containsKey(loc)) {
            Block block = loc.getBlock();
            changes.put(loc, block.getBlockData().clone());
        }
    }

    public boolean isTracked(Location loc) {
        return changes.containsKey(loc);
    }

    public int size() {
        return changes.size();
    }

    public void clear() {
        changes.clear();
    }

    /** 还原全部方块改动（不触发物理更新，避免连锁沙落） */
    public void restoreAll() {
        for (Map.Entry<Location, BlockData> e : changes.entrySet()) {
            Location loc = e.getKey();
            if (loc.getWorld() == null) {
                continue;
            }
            Block b = loc.getBlock();
            if (!b.getType().equals(e.getValue().getMaterial()) || !b.getBlockData().equals(e.getValue())) {
                b.setBlockData(e.getValue(), false);
            }
        }
        changes.clear();
    }
}
