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
/**
 * DM 工具注册中心。
 *
 * <p>管理所有 {@link DmTool} 实现，并为每次 DM LLM 调用构建绑定了特定
 * {@link DmToolContext} 的 Spring AI {@link ToolCallback} 列表。</p>
 *
 * <p>提供基于游戏阶段的访问控制守卫：如果工具声明了 {@code allowedPhases}
 * 且当前阶段不在允许列表中，则拒绝执行并通过 WebSocket 广播拒绝原因。</p>
 *
 * @see DmTool
 * @see DmToolContext
 */
public class DmToolRegistry {

    /** 所有已注册的 DM 工具 */
    private final List<DmTool> tools;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 构建绑定到指定上下文的 Spring AI ToolCallback 列表。
     *
     * <p>每个 {@link DmTool} 被转换为一个 {@link FunctionToolCallback}，
     * LLM 可通过工具名称调用。执行前会进行阶段守卫检查。</p>
     *
     * @param ctx 当前 DM 执行上下文
     * @return 可供 LLM 调用的 ToolCallback 列表
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

    /**
     * 带阶段守卫的工具执行。若当前阶段不在工具允许列表中，拒绝执行并广播提示。
     *
     * @param tool  要执行的工具
     * @param input 工具输入参数
     * @param ctx   DM 执行上下文
     * @return 工具执行结果或错误信息
     */
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

    /**
     * 获取所有已注册工具的名称列表。
     *
     * @return 工具名称列表
     */
    public List<String> getToolNames() {
        return tools.stream().map(DmTool::name).toList();
    }
}
