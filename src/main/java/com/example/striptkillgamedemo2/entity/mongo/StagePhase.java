package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 阶段子阶段定义。
 * <p>
 * 内嵌于 {@link ScriptStage#getPhases()}，描述一幕剧本中的子阶段（如轮流发言、自由讨论、
 * 投票等）的类型、发言顺序与时长限制，供 DM 与前端统一驱动。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagePhase {
    /** 子阶段 ID。 */
    private String phaseId;
    /** 子阶段类型。 */
    private PhaseType type;
    /** 发言顺序（角色 ID 列表），仅 {@link PhaseType#TURN_BASED} 等需要顺序的阶段使用。 */
    private List<String> speakOrder;
    /** 子阶段时长限制（秒），超时由 {@code PhaseTimerService} 自动推进。 */
    private int timeLimitSeconds;
    /** 本子阶段开始时传给 DM 的额外指令。 */
    private String dmInstruction;
}
