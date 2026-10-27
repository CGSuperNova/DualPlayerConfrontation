package com.buzizi.dualplayerconfrontation.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;

/** 玩法抽取池：轮回制，四个玩法都玩过一遍之前不会重复已玩玩法 */
public final class ModePool {

    private final Deque<GameModeType> queue = new ArrayDeque<>();

    public ModePool() {
        reshuffle();
    }

    private void reshuffle() {
        ArrayList<GameModeType> all = new ArrayList<>(Arrays.asList(GameModeType.values()));
        Collections.shuffle(all);
        queue.clear();
        queue.addAll(all);
    }

    /** 取出下一个玩法；池空时重新洗牌再取 */
    public GameModeType next() {
        if (queue.isEmpty()) {
            reshuffle();
        }
        return queue.pollFirst();
    }

    public int remaining() {
        return queue.size();
    }
}
