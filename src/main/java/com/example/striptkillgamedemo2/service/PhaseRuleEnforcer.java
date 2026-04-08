package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PhaseRuleEnforcer {

    public enum SpeakCheck {
        ALLOWED,
        BLOCKED_SILENT,
        BLOCKED_DM_REMIND
    }

    /**
     * Check whether a role is allowed to speak in the current phase.
     *
     * @param room       current game room state
     * @param phase      current phase type
     * @param roleIdHex  hex string of the speaking role
     * @param isAi       true if speaker is an AI agent
     * @return SpeakCheck indicating whether to allow, silently drop, or remind via DM
     */
    public SpeakCheck checkCanSpeak(LiveGameRoom room, PhaseType phase,
                                     String roleIdHex, boolean isAi) {
        // Eliminated AI: always silent block
        if (isAi && room.getEliminatedRoleIds().contains(roleIdHex)) {
            log.debug("[PhaseRule] BLOCKED_SILENT: eliminated AI {}", roleIdHex);
            return SpeakCheck.BLOCKED_SILENT;
        }

        // Eliminated human: DM remind
        if (!isAi && room.getEliminatedRoleIds().contains(roleIdHex)) {
            log.debug("[PhaseRule] BLOCKED_DM_REMIND: eliminated player {}", roleIdHex);
            return SpeakCheck.BLOCKED_DM_REMIND;
        }

        return switch (phase) {
            case SCRIPT_READING -> isAi ? SpeakCheck.BLOCKED_SILENT : SpeakCheck.BLOCKED_DM_REMIND;

            case TURN_BASED -> {
                if (isAi && room.getSpokenRoleIds().contains(roleIdHex)) {
                    yield SpeakCheck.BLOCKED_SILENT;
                }
                yield SpeakCheck.ALLOWED;
            }

            case FREE_CHAT -> SpeakCheck.ALLOWED;

            case INVESTIGATION -> SpeakCheck.ALLOWED;

            case VOTE -> {
                if (isAi) {
                    // AI can only speak during STATEMENT sub-phase and if not already spoken
                    String subPhase = room.getVoteSubPhase();
                    if (!"STATEMENT".equals(subPhase)) {
                        yield SpeakCheck.BLOCKED_SILENT;
                    }
                    if (room.getSpokenRoleIds().contains(roleIdHex)) {
                        yield SpeakCheck.BLOCKED_SILENT;
                    }
                }
                yield SpeakCheck.ALLOWED;
            }
        };
    }

    /**
     * Check if a player is @mentioning an eliminated AI role.
     */
    public boolean isTargetingEliminatedAi(LiveGameRoom room, String targetRoleIdHex) {
        return room.getEliminatedRoleIds().contains(targetRoleIdHex);
    }

    /**
     * Mark a role as having spoken in the current phase (for TURN_BASED enforcement).
     */
    public void markSpoken(LiveGameRoom room, String roleIdHex) {
        room.getSpokenRoleIds().add(roleIdHex);
    }

    /**
     * Clear phase-scoped state on phase transition.
     */
    public void resetPhaseState(LiveGameRoom room) {
        room.getSpokenRoleIds().clear();
        room.setPhaseOvertime(false);
        room.setInvestigationMode(null);
        room.setVoteSubPhase(null);
    }
}
