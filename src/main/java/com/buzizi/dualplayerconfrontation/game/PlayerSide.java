package com.buzizi.dualplayerconfrontation.game;

/** 玩家阵营：A 塔 / B 塔 */
public enum PlayerSide {
    A, B;

    public PlayerSide opposite() {
        return this == A ? B : A;
    }
}
