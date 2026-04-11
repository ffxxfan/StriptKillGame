package com.example.striptkillgamedemo2.ai.tool;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;

import java.util.List;

/**
 * DM 工具执行上下文。
 *
 * <p>在一次 DM LLM 调用期间共享的可变上下文对象，包含当前房间状态、剧本信息、
 * 阶段类型等。DM 工具通过此上下文访问游戏状态并设置执行标志。</p>
 *
 * <p>关键标志：</p>
 * <ul>
 *   <li>{@code agentDelegated} — 工具（如 {@code selectRespondents}）委托 AI 代理发言时设为 {@code true}，
 *       DmExecutor 据此抑制 DM 自身的文本输出</li>
 *   <li>{@code pendingRoundRobinInstruction} — 延迟执行的轮流发言指令，
 *       DmExecutor 在 DM 文本流式传输完成后再执行</li>
 * </ul>
 *
 * @see DmTool
 * @see com.example.striptkillgamedemo2.ai.executor.DmExecutor
 */
@Data
@Builder
public class DmToolContext {
    /** 当前游戏房间的运行时状态 */
    private LiveGameRoom room;
    /** 当前使用的剧本 */
    private Script script;
    /** 当前阶段 ID */
    private String currentPhaseId;
    /** 当前阶段类型 */
    private PhaseType currentPhaseType;
    /** 触发本次 DM 调用的角色 ID */
    private ObjectId triggerRoleId;

    /**
     * 代理委托标志。当工具（如 selectRespondents）委托 AI 代理发言时设为 {@code true}，
     * DmExecutor 检查此标志以抑制 DM 自身的文本输出。
     */
    @Builder.Default
    private boolean agentDelegated = false;

    /**
     * 延迟执行的轮流发言指令。{@code triggerRoundRobinSpeech} 工具将请求暂存于此，
     * 而非立即执行，以便 DmExecutor 在 DM 文本流式传输完成后再依次触发各 AI 代理发言。
     */
    private String pendingRoundRobinInstruction;
    /** 延迟执行轮流发言的角色 ID 列表 */
    private List<ObjectId> pendingRoundRobinRoleIds;
}
