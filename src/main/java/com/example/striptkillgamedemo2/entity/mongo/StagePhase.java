package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagePhase {
    private String phaseId;
    private PhaseType type;
    private List<String> speakOrder;
    private int timeLimitSeconds;
    private String dmInstruction;
}
