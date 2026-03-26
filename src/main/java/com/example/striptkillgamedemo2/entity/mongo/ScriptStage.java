package com.example.striptkillgamedemo2.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import java.util.Map;

/**
 * ScriptStage represents a single stage within a script.
 * Embedded within Script for optimal query performance.
 *
 * ContentMap maps roleId (ObjectId) to content revealed to that role at this stage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptStage {
    private int stageNumber;
    private String stageTitle;
    private Map<ObjectId, String> contentMap;
    private String audioUrl;
}
