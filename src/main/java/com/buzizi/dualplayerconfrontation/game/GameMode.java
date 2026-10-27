package com.buzizi.dualplayerconfrontation.game;

/** 玩法接口：每个玩法在回合开始时装载道具/实体，结束时清理 */
public interface GameMode {

    GameModeType type();

    /** 回合开始（进入战斗阶段前调用），用于发放道具、生成实体 */
    default void onRoundStart(DuelGame game) {
    }

    /** 回合结束，清理该玩法的道具、任务与实体（方块回滚由对局统一执行） */
    default void onRoundEnd(DuelGame game) {
    }

    /** 战斗阶段每秒回调 */
    default void tick(DuelGame game) {
    }
}
