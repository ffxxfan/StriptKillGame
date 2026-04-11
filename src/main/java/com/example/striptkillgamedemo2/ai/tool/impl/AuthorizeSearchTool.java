package com.example.striptkillgamedemo2.ai.tool.impl;

import com.example.striptkillgamedemo2.ai.tool.DmTool;
import com.example.striptkillgamedemo2.ai.tool.DmToolContext;
import com.example.striptkillgamedemo2.entity.mongo.Clue;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.redis.GameClueInstance;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.striptkillgamedemo2.entity.enums.PhaseType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * DM 工具：授权搜证。
 *
 * <p>授权指定角色在指定地点进行搜证。执行流程：</p>
 * <ol>
 *   <li>校验角色存在性和剩余搜证次数</li>
 *   <li>根据地点标签、可搜证角色和当前幕次过滤匹配的线索</li>
 *   <li>排除已发现的线索，创建新的线索实例</li>
 *   <li>扣除搜证次数并保存房间状态</li>
 *   <li>根据搜证模式（PUBLIC/PRIVATE）广播或私发线索</li>
 * </ol>
 *
 * <p>仅在 {@link PhaseType#INVESTIGATION} 阶段可用。</p>
 */
public class AuthorizeSearchTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 工具输入参数。
     */
    @Data
    public static class Input {
        /** 房间 ID */
        private String roomId;
        /** 搜证角色 ID */
        private String roleId;
        /** 搜证地点 */
        private String location;
    }

    @Override
    public Set<PhaseType> allowedPhases() {
        return Set.of(PhaseType.INVESTIGATION);
    }

    @Override
    public String name() {
        return "authorizeSearch";
    }

    @Override
    public String description() {
        return "授权角色在指定地点搜证。校验搜证次数和地点合法性，成功后扣除次数并分发线索。";
    }

    @Override
    public Class<?> inputType() {
        return Input.class;
    }

    @Override
    public Object execute(Object rawInput, DmToolContext ctx) {
        Input input = (Input) rawInput;
        String roleId = input.getRoleId();
        String location = input.getLocation();

        // 1. Find role in script
        Role role = ctx.getScript().getRoles().stream()
                .filter(r -> r.getId() != null && r.getId().toHexString().equals(roleId))
                .findFirst()
                .orElse(null);

        if (role == null) {
            return Map.of("success", false, "error", "角色不存在: " + roleId);
        }

        // 2. Check search power
        if (role.getSearchPower() <= 0) {
            return Map.of("success", false, "error", "该角色搜证次数已用完");
        }

        LiveGameRoom room = ctx.getRoom();

        // 3. Find clues at location that role can search (with stage filter)
        int currentStage = ctx.getRoom().getCurrentStage();

        List<Clue> matchingClues = ctx.getScript().getClues() == null
                ? List.of()
                : ctx.getScript().getClues().stream()
                        .filter(clue -> clue.getLocationTag() != null && clue.getLocationTag().contains(location))
                        .filter(clue -> clue.getSearchableRoleIds() != null && clue.getSearchableRoleIds().contains(roleId))
                        .filter(clue -> clue.getStages() == null || clue.getStages().isEmpty()
                                || clue.getStages().contains(currentStage))
                        .toList();

        // 4. Filter out already-found clues
        List<String> alreadyFoundClueIds = room.getClueInstances() == null
                ? List.of()
                : room.getClueInstances().stream()
                        .filter(GameClueInstance::isFound)
                        .map(ci -> ci.getClueId().toHexString())
                        .toList();

        String investigationMode = room.getInvestigationMode();
        boolean isPrivate = "PRIVATE".equals(investigationMode);

        List<String> foundTitles = new ArrayList<>();
        for (Clue clue : matchingClues) {
            if (clue.getId() != null && !alreadyFoundClueIds.contains(clue.getId().toHexString())) {
                GameClueInstance instance = GameClueInstance.builder()
                        .id(new ObjectId())
                        .clueId(clue.getId())
                        .ownerRoleIds(List.of(new ObjectId(roleId)))
                        .isPublic(!isPrivate)
                        .isFound(true)
                        .discoveredAt(LocalDateTime.now())
                        .build();
                room.getClueInstances().add(instance);
                foundTitles.add(clue.getTitle());
            }
        }

        // 5. Deduct search power (mutate script role — used for in-session tracking)
        role.setSearchPower(role.getSearchPower() - 1);

        // 6. Save room
        liveGameRoomService.save(room);

        // 7. Broadcast or privately deliver CLUE_FOUND based on investigation mode
        boolean isAiSearcher = room.getMembers().stream()
                .anyMatch(m -> m.isAi() && m.getRoleId() != null
                        && m.getRoleId().toHexString().equals(roleId));

        if (!foundTitles.isEmpty()) {
            if (!isPrivate) {
                // PUBLIC mode: broadcast to all
                messagingTemplate.convertAndSend(
                        "/topic/room." + room.getRoomId(),
                        Map.of("type", "CLUE_FOUND", "roleId", roleId, "clues", foundTitles));
            } else if (!isAiSearcher) {
                // PRIVATE mode + human searcher: send via private channel
                String userId = room.getMembers().stream()
                        .filter(m -> !m.isAi() && m.getRoleId() != null
                                && m.getRoleId().toHexString().equals(roleId)
                                && m.getUserId() != null)
                        .map(m -> m.getUserId().toHexString())
                        .findFirst().orElse(null);
                if (userId != null) {
                    messagingTemplate.convertAndSendToUser(userId,
                            "/queue/room." + room.getRoomId() + ".private",
                            Map.of("type", "PRIVATE_CLUE", "clues", foundTitles,
                                    "label", "仅你可见"));
                }
            }
            // PRIVATE mode + AI searcher: no broadcast, clues already added to clueInstances
        }

        log.info("[authorizeSearch] roomId={}, roleId={}, location={}, found={}, remainingPower={}",
                room.getRoomId(), roleId, location, foundTitles, role.getSearchPower());

        return Map.of(
                "success", true,
                "foundClues", foundTitles,
                "remainingSearchPower", role.getSearchPower()
        );
    }
}
