package com.example.striptkillgamedemo2.ai.tool;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DmToolRegistry {

    private final List<DmTool> tools;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Build Spring AI ToolCallback instances bound to a specific DmToolContext.
     * Each DmTool becomes a FunctionToolCallback the LLM can invoke by name.
     * Tools are guarded by phase-based access control — if the tool declares
     * allowedPhases and the current phase doesn't match, execution is rejected.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<ToolCallback> buildCallbacks(DmToolContext ctx) {
        return tools.stream()
                .map(tool -> (ToolCallback) FunctionToolCallback
                        .builder(tool.name(), (Object input) -> executeWithGuard(tool, input, ctx))
                        .description(tool.description())
                        .inputType((Class) tool.inputType())
                        .build())
                .toList();
    }

    private Object executeWithGuard(DmTool tool, Object input, DmToolContext ctx) {
        Set<PhaseType> allowed = tool.allowedPhases();
        PhaseType current = ctx.getCurrentPhaseType();
        if (!allowed.isEmpty() && current != null && !allowed.contains(current)) {
            String reason = "当前环节（" + current + "）不允许执行「" + tool.name() + "」操作";
            log.warn("[ToolGuard] Blocked {} — current phase {} not in allowed {}",
                    tool.name(), current, allowed);

            // Broadcast rejection to chat so the player sees it too
            String roomId = ctx.getRoom().getRoomId();
            messagingTemplate.convertAndSend("/topic/room." + roomId,
                    Map.of("type", "SYSTEM", "content", reason));

            // Return error to DM agent so it knows the tool was blocked
            return Map.of("error", reason);
        }
        return tool.execute(input, ctx);
    }

    public List<String> getToolNames() {
        return tools.stream().map(DmTool::name).toList();
    }
}
