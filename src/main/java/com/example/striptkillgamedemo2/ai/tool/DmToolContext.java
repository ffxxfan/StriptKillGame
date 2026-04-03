package com.example.striptkillgamedemo2.ai.tool;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
@Builder
public class DmToolContext {
    private LiveGameRoom room;
    private Script script;
    private String currentPhaseId;
    private PhaseType currentPhaseType;
    private ObjectId triggerRoleId;
}
