package com.example.striptkillgamedemo2.entity.enums;

/**
 * 剧本难度枚举。
 * <p>
 * 用于描述剧本的难度等级，数值越大代表难度越高，可供前端筛选或匹配玩家能力。
 * </p>
 */
public enum ScriptDifficulty {
    /** 简单，适合新手玩家。 */
    EASY(1),
    /** 普通，常规难度。 */
    NORMAL(2),
    /** 困难，线索推理较复杂。 */
    HARD(3),
    /** 专家，需要较强推理与协作能力。 */
    EXPERT(4);

    private final int level;

    ScriptDifficulty(int level) {
        this.level = level;
    }

    /**
     * 获取难度数值等级。
     *
     * @return 难度等级（1~4，数值越大难度越高）
     */
    public int getLevel() {
        return level;
    }
}
