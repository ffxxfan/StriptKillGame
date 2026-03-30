package com.example.striptkillgamedemo2.ai.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DmToolRegistry {

    private final List<DmTool> tools;

    /**
     * Build Spring AI ToolCallback instances bound to a specific DmToolContext.
     * Each DmTool becomes a FunctionToolCallback the LLM can invoke by name.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<ToolCallback> buildCallbacks(DmToolContext ctx) {
        return tools.stream()
                .map(tool -> (ToolCallback) FunctionToolCallback
                        .builder(tool.name(), (Object input) -> tool.execute(input, ctx))
                        .description(tool.description())
                        .inputType((Class) tool.inputType())
                        .build())
                .toList();
    }

    public List<String> getToolNames() {
        return tools.stream().map(DmTool::name).toList();
    }
}
