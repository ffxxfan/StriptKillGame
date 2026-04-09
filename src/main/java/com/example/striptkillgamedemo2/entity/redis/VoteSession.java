package com.example.striptkillgamedemo2.entity.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 投票会话。
 * <p>
 * 内嵌于 {@link LiveGameRoom#getActiveVote()}，描述当前房间的活跃投票。投票完成或超时后
 * 由 {@code VoteService} 关闭并推动流程。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSession {
    /** 投票 ID。 */
    private String voteId;
    /** 投票标题或问题描述。 */
    private String title;
    /** 投票可选项列表。 */
    private List<String> options;
    /** 投票结果：Key 为投票者角色 ID，Value 为所选选项。 */
    @Builder.Default
    private Map<String, String> results = new HashMap<>();
    /** 截止时间。 */
    private LocalDateTime deadline;
}
