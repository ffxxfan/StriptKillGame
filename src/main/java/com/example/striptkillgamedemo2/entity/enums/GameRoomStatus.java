package com.example.striptkillgamedemo2.entity.enums;

/**
 * 游戏房间状态枚举。
 * <p>
 * 标识一个 {@code GameRoom} 当前所处的生命周期阶段，驱动房间列表展示及后续可执行的操作。
 * </p>
 */
public enum GameRoomStatus {
    /** 等待中：房间已创建但尚未开始游戏，玩家可自由加入/离开。 */
    WAITING,
    /** 游戏进行中：剧本已启动，玩家正在进行流程化的游戏阶段。 */
    PLAYING,
    /** 当前阶段中：进入到某一具体阶段（如讨论、投票等子阶段）。 */
    CURRENT_STAGE,
    /** 已结束：游戏流程结束，不再接受操作，仅可查看回放。 */
    FINISHED
}
