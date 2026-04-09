package com.example.striptkillgamedemo2.entity.enums;

/**
 * 阶段类型枚举。
 * <p>
 * 描述剧本某一阶段的交互形态，用于由 DM 驱动的游戏流程调度；不同类型决定了发言方式、
 * 规则校验器以及前端 UI 展示。
 * </p>
 */
public enum PhaseType {
    /** 读本阶段：玩家阅读剧本人物故事，通常无需强制发言。 */
    SCRIPT_READING(false),
    /** 轮流发言阶段：按顺序轮转发言，非必答。 */
    TURN_BASED(false),
    /** 自由讨论阶段：玩家可自由发言，非必答。 */
    FREE_CHAT(false),
    /** 调查取证阶段：可搜证、获取线索，非必答。 */
    INVESTIGATION(false),
    /** 投票阶段：玩家必须完成投票才能推进。 */
    VOTE(true);

    private final boolean defaultRequired;

    PhaseType(boolean defaultRequired) {
        this.defaultRequired = defaultRequired;
    }

    /**
     * 该阶段是否默认要求玩家必须完成对应交互。
     *
     * @return {@code true} 表示该阶段默认需要强制完成（如投票），否则为 {@code false}
     */
    public boolean isDefaultRequired() {
        return defaultRequired;
    }
}
