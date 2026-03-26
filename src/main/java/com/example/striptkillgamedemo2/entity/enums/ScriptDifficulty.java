package com.example.striptkillgamedemo2.entity.enums;

public enum ScriptDifficulty {
    EASY(1),
    NORMAL(2),
    HARD(3),
    EXPERT(4);

    private final int level;

    ScriptDifficulty(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
