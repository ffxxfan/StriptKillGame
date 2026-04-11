package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
/**
 * 游戏阶段规则执行器。
 *
 * <p>根据当前游戏阶段类型和角色状态，判断角色是否有发言权限。
 * 提供三种检查结果：允许、静默阻断、DM 提醒。</p>
 *
 * <p>同时管理阶段级别的状态（如已发言角色集合、超时标志等）。</p>
 */
public class PhaseRuleEnforcer {

    /**
     * 发言权限检查结果枚举。
     */
    public enum SpeakCheck {
        /** 允许发言 */
        ALLOWED,
        /** 静默阻断（不通知发送者） */
        BLOCKED_SILENT,
        /** 阻断并通过 DM 提醒发送者 */
        BLOCKED_DM_REMIND
    }

    /**
     * 检查角色在当前阶段是否允许发言。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>已出局 AI — 始终静默阻断</li>
     *   <li>已出局人类 — DM 提醒</li>
     *   <li>阅读剧本阶段 — AI 静默阻断，人类 DM 提醒</li>
     *   <li>轮流发言阶段 — 已发言的 AI 静默阻断</li>
     *   <li>投票阶段 — AI 仅在陈述子阶段且未发言时可发言</li>
     * </ul>
     *
     * @param room      游戏房间运行时状态
     * @param phase     当前阶段类型
     * @param roleIdHex 发言者角色 ID（十六进制字符串）
     * @param isAi      发言者是否为 AI 代理
     * @return 发言权限检查结果
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
     * 检查目标角色是否已出局（用于判断玩家是否 @了已出局的 AI 角色）。
     *
     * @param room            游戏房间运行时状态
     * @param targetRoleIdHex 目标角色 ID（十六进制字符串）
     * @return 是否已出局
     */
    public boolean isTargetingEliminatedAi(LiveGameRoom room, String targetRoleIdHex) {
        return room.getEliminatedRoleIds().contains(targetRoleIdHex);
    }

    /**
     * 标记角色在当前阶段已发言（用于轮流发言和投票陈述的重复发言检查）。
     *
     * @param room      游戏房间运行时状态
     * @param roleIdHex 角色 ID（十六进制字符串）
     */
    public void markSpoken(LiveGameRoom room, String roleIdHex) {
        room.getSpokenRoleIds().add(roleIdHex);
    }

    /**
     * 在阶段切换时清除阶段级别的状态（已发言角色、超时标志、搜证模式、投票子阶段）。
     *
     * @param room 游戏房间运行时状态
     */
    public void resetPhaseState(LiveGameRoom room) {
        room.getSpokenRoleIds().clear();
        room.setPhaseOvertime(false);
        room.setInvestigationMode(null);
        room.setVoteSubPhase(null);
    }
}
