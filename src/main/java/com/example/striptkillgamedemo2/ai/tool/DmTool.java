package com.example.striptkillgamedemo2.ai.tool;

public interface DmTool {
    String name();
    String description();
    Class<?> inputType();
    Object execute(Object input, DmToolContext ctx);
}
