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
public class AuthorizeSearchTool implements DmTool {

    private final LiveGameRoomService liveGameRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Data
    public static class Input {
        private String roomId;
        private String roleId;
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

        // 3. Find clues at location that role can search
        List<Clue> matchingClues = ctx.getScript().getClues() == null
                ? List.of()
                : ctx.getScript().getClues().stream()
                        .filter(clue -> clue.getLocationTag() != null && clue.getLocationTag().contains(location))
                        .filter(clue -> clue.getSearchableRoleIds() != null && clue.getSearchableRoleIds().contains(roleId))
                        .toList();

        // 4. Filter out already-found clues
        List<String> alreadyFoundClueIds = room.getClueInstances() == null
                ? List.of()
                : room.getClueInstances().stream()
                        .filter(GameClueInstance::isFound)
                        .map(ci -> ci.getClueId().toHexString())
                        .toList();

        List<String> foundTitles = new ArrayList<>();
        for (Clue clue : matchingClues) {
            if (clue.getId() != null && !alreadyFoundClueIds.contains(clue.getId().toHexString())) {
                GameClueInstance instance = GameClueInstance.builder()
                        .id(new ObjectId())
                        .clueId(clue.getId())
                        .ownerRoleIds(List.of(new ObjectId(roleId)))
                        .isPublic(false)
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

        // 7. Broadcast CLUE_FOUND signal
        if (!foundTitles.isEmpty()) {
            messagingTemplate.convertAndSend(
                    "/topic/room." + room.getRoomId(),
                    Map.of("type", "CLUE_FOUND", "roleId", roleId, "clues", foundTitles)
            );
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
