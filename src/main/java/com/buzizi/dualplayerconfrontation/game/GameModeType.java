package com.buzizi.dualplayerconfrontation.game;

/** 五个玩法（轮回抽取，全部玩过一遍前不重复） */
public enum GameModeType {
    FIREBALL("fireball"),
    TNT_RAIN("tnt_rain"),
    CREEPER("creeper"),
    SHULKER("shulker"),
    TNT_BADMINTON("tnt_badminton");

    private final String key;

    GameModeType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
