package com.example.striptkillgamedemo2.ai.tool;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;

import java.util.List;

@Data
@Builder
public class DmToolContext {
    private LiveGameRoom room;
    private Script script;
    private String currentPhaseId;
    private PhaseType currentPhaseType;
    private ObjectId triggerRoleId;

    /**
     * Set to true when a tool (e.g. selectRespondents) delegates speech to AI agents.
     * DmExecutor checks this flag to suppress DM's own text output.
     */
    @Builder.Default
    private boolean agentDelegated = false;

    /**
     * Deferred round-robin: triggerRoundRobinSpeech stores the request here
     * instead of executing immediately, so DmExecutor can run agents AFTER
     * streaming DM's own text to the frontend.
     */
    private String pendingRoundRobinInstruction;
    private List<ObjectId> pendingRoundRobinRoleIds;
}
