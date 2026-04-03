package com.example.striptkillgamedemo2.ai.tool;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;

import java.util.Set;

public interface DmTool {
    String name();
    String description();
    Class<?> inputType();
    Object execute(Object input, DmToolContext ctx);

    /**
     * Which phases this tool is allowed to run in.
     * Empty set means always allowed (e.g. readFullScript).
     */
    default Set<PhaseType> allowedPhases() {
        return Set.of();
    }
}
