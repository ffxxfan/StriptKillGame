package com.example.striptkillgamedemo2.ai.tool;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;

import java.util.Set;

/**
 * DM（主持人）可调用工具的接口定义。
 *
 * <p>每个实现类代表一个 DM 在游戏中可执行的操作（如推进阶段、发起投票、授权搜证等）。
 * {@link DmToolRegistry} 会将所有 {@code DmTool} 实现转换为 Spring AI 的 {@link org.springframework.ai.tool.ToolCallback}，
 * 供 LLM 在 DM 对话中按名称调用。</p>
 *
 * @see DmToolRegistry
 * @see DmToolContext
 */
public interface DmTool {

    /**
     * 工具名称，LLM 通过此名称调用工具。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 工具描述，嵌入到 LLM 提示词中帮助模型理解何时使用此工具。
     *
     * @return 工具描述
     */
    String description();

    /**
     * 工具输入参数的 Java 类型，Spring AI 会自动将 JSON 反序列化为该类型。
     *
     * @return 输入参数类
     */
    Class<?> inputType();

    /**
     * 执行工具逻辑。
     *
     * @param input 反序列化后的输入参数对象
     * @param ctx   当前 DM 执行上下文（包含房间、剧本、阶段等信息）
     * @return 执行结果，将被序列化为 JSON 返回给 LLM
     */
    Object execute(Object input, DmToolContext ctx);

    /**
     * 工具允许执行的游戏阶段集合。
     *
     * <p>返回空集合表示在所有阶段都可执行（如 {@code readFullScript}）。
     * 非空集合将由 {@link DmToolRegistry} 进行阶段守卫检查，
     * 当前阶段不在允许列表中时拒绝执行。</p>
     *
     * @return 允许执行的阶段集合，空集合表示不限制
     */
    default Set<PhaseType> allowedPhases() {
        return Set.of();
    }
}
