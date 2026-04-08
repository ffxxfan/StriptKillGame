package com.example.striptkillgamedemo2.entity.enums;

public enum PhaseType {
    SCRIPT_READING(false),
    TURN_BASED(false),
    FREE_CHAT(false),
    INVESTIGATION(false),
    VOTE(true);

    private final boolean defaultRequired;

    PhaseType(boolean defaultRequired) {
        this.defaultRequired = defaultRequired;
    }

    public boolean isDefaultRequired() {
        return defaultRequired;
    }
}
