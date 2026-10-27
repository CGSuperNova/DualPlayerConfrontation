package com.buzizi.dualplayerconfrontation.stats;

import java.util.List;

/** 星级计算（纯函数）。经验累计不清零，初始 1 星。 */
public final class StarManager {

    private StarManager() {
    }

    /** 各星区边界：[100, 500, 1000, 无限] */
    private static final double[] BOUNDS = {100, 500, 1000, Double.MAX_VALUE};

    /**
     * 当前星级下每升一星所需经验。
     * @param xpPerStar 配置：[星数<100, 星数<500, 星数<1000, 其余]
     */
    public static double xpRequiredForStar(List<Integer> xpPerStar, int currentStar) {
        double cur = currentStar;
        if (cur < BOUNDS[0]) {
            return idx(xpPerStar, 0);
        }
        if (cur < BOUNDS[1]) {
            return idx(xpPerStar, 1);
        }
        if (cur < BOUNDS[2]) {
            return idx(xpPerStar, 2);
        }
        return idx(xpPerStar, 3);
    }

    private static double idx(List<Integer> list, int i) {
        if (list == null || list.isEmpty()) {
            return 500;
        }
        return list.get(Math.min(i, list.size() - 1));
    }

    /** 由累计经验计算当前星级（1 星起步） */
    public static int starFromXp(List<Integer> xpPerStar, long xp) {
        int star = 1;
        double remaining = xp;
        while (remaining >= xpRequiredForStar(xpPerStar, star)) {
            remaining -= xpRequiredForStar(xpPerStar, star);
            star++;
            if (star > 10_000_000) {
                break;
            }
        }
        return star;
    }

    /** 在当前星级内已获得的经验（用于进度） */
    public static double xpIntoStar(List<Integer> xpPerStar, long xp) {
        int star = 1;
        double remaining = xp;
        while (remaining >= xpRequiredForStar(xpPerStar, star)) {
            remaining -= xpRequiredForStar(xpPerStar, star);
            star++;
            if (star > 10_000_000) {
                break;
            }
        }
        return remaining;
    }
}
