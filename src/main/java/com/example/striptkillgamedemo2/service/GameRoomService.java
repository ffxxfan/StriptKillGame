package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.RoleDTO;
import com.example.striptkillgamedemo2.dto.RoomDetailDTO;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final ScriptRepository scriptRepository;

    /** Create room. Cleans up any existing active room for this user first. */
    public LiveGameRoom createRoom(ObjectId userId) {
        String existing = liveGameRoomService.getActiveRoomId(userId);
        if (existing != null) {
            log.info("User {} has existing room {}, cleaning up before creating new room", userId, existing);
            liveGameRoomService.evict(new ObjectId(existing));
            liveGameRoomService.unbindUserRoom(userId);
        }

        String roomId = new ObjectId().toHexString();
        Member creator = Member.builder()
                .userId(userId)
                .isAi(false)
                .isDm(false)
                .isOnline(true)
                .build();

        LiveGameRoom room = LiveGameRoom.builder()
                .roomId(roomId)
                .status(GameRoomStatus.WAITING)
                .currentStage(0)
                .members(new ArrayList<>(List.of(creator)))
                .build();

        liveGameRoomService.save(room);
        liveGameRoomService.bindUserRoom(userId, roomId);
        log.info("Room {} created by user {}", roomId, userId);
        return room;
    }

    public LiveGameRoom getRoom(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在或已结束: " + roomId);
        }
        return room;
    }

    public void validateMembership(LiveGameRoom room, ObjectId userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()));
        if (!isMember) {
            throw new IllegalStateException("你不在该房间中");
        }
    }

    /** Select a script and immediately load its full content into Redis. */
    public LiveGameRoom selectScript(String roomId, ObjectId scriptId, ObjectId userId) {
        LiveGameRoom room = getRoom(roomId);
        validateStatus(room, GameRoomStatus.WAITING);
        validateMembership(room, userId);

        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("剧本不存在: " + scriptId));
        scriptCacheService.cacheScript(script);

        room.setScriptId(scriptId.toHexString());
        liveGameRoomService.save(room);
        return room;
    }

    public List<RoleDTO> getRoles(String roomId) {
        LiveGameRoom room = getRoom(roomId);
        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        List<Role> roles = script.getRoles();

        List<String> takenRoleIds = room.getMembers().stream()
                .filter(m -> m.getRoleId() != null && !m.isAi())
                .map(m -> m.getRoleId().toHexString())
                .toList();

        return roles.stream().map(role -> RoleDTO.builder()
                .id(role.getId().toHexString())
                .name(role.getName())
                .avatar(role.getAvatar())
                .isNpc(role.isNpc())
                .isAvailable(!takenRoleIds.contains(role.getId().toHexString()))
                .build()
        ).toList();
    }

    public LiveGameRoom selectRole(String roomId, ObjectId roleId, ObjectId userId) {
        LiveGameRoom room = getRoom(roomId);
        validateStatus(room, GameRoomStatus.WAITING);

        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        List<Role> allRoles = script.getRoles();

        boolean roleExists = allRoles.stream().anyMatch(r -> Objects.equals(r.getId(), roleId));
        if (!roleExists) {
            throw new IllegalArgumentException("该角色不属于当前剧本");
        }

        Member playerMember = room.getMembers().stream()
                .filter(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中"));

        playerMember.setRoleId(roleId);

        List<ObjectId> humanRoleIds = room.getMembers().stream()
                .filter(m -> !m.isAi() && m.getRoleId() != null)
                .map(Member::getRoleId)
                .toList();

        room.getMembers().removeIf(Member::isAi);

        for (Role role : allRoles) {
            if (!humanRoleIds.contains(role.getId())) {
                room.getMembers().add(Member.builder()
                        .roleId(role.getId())
                        .isAi(true)
                        .isDm(role.isNpc())
                        .isOnline(true)
                        .build());
            }
        }

        liveGameRoomService.save(room);
        return room;
    }

    /**
     * Mark user as offline. If all humans gone, set idle TTL.
     * Actual Redis eviction happens in GameFlowService.endGame().
     */
    public void leaveRoom(String roomId, ObjectId userId) {
        LiveGameRoom room = getRoom(roomId);

        room.getMembers().stream()
                .filter(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()))
                .findFirst()
                .ifPresent(m -> m.setOnline(false));

        liveGameRoomService.unbindUserRoom(userId);

        boolean anyHumanOnline = room.getMembers().stream()
                .anyMatch(m -> !m.isAi() && m.isOnline());

        if (!anyHumanOnline && room.getStatus() == GameRoomStatus.PLAYING) {
            // Save offline state and set short idle TTL — room will self-expire
            liveGameRoomService.save(room);
            liveGameRoomService.setIdleTtl(new ObjectId(roomId));
            log.info("Room {} set to idle TTL after last human left", roomId);
        } else if (!anyHumanOnline && room.getStatus() == GameRoomStatus.WAITING) {
            // No one left in a waiting room — just evict immediately
            liveGameRoomService.evict(new ObjectId(roomId));
            log.info("Waiting room {} evicted after last human left", roomId);
        } else {
            liveGameRoomService.save(room);
        }
    }

    public ObjectId findRoleIdForUser(LiveGameRoom room, ObjectId userId) {
        return room.getMembers().stream()
                .filter(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()))
                .map(Member::getRoleId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中或未选择角色"));
    }

    public RoomDetailDTO toDetailDTO(LiveGameRoom room) {
        Script script = null;
        if (room.getScriptId() != null) {
            try {
                script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
            } catch (Exception ignored) {}
        }

        List<Role> roles = script != null ? script.getRoles() : List.of();
        String scriptTitle = script != null ? script.getTitle() : null;
        final Script finalScript = script;

        List<RoomDetailDTO.MemberDTO> memberDTOs = room.getMembers().stream().map(m -> {
            Role role = roles.stream()
                    .filter(r -> Objects.equals(r.getId(), m.getRoleId()))
                    .findFirst().orElse(null);
            return RoomDetailDTO.MemberDTO.builder()
                    .userId(m.getUserId() != null ? m.getUserId().toHexString() : null)
                    .roleId(m.getRoleId() != null ? m.getRoleId().toHexString() : null)
                    .roleName(role != null ? role.getName() : null)
                    .roleAvatar(role != null ? role.getAvatar() : null)
                    .isAi(m.isAi())
                    .isDm(m.isDm())
                    .isOnline(m.isOnline())
                    .build();
        }).toList();

        return RoomDetailDTO.builder()
                .roomId(room.getRoomId())
                .scriptId(room.getScriptId())
                .scriptTitle(scriptTitle)
                .status(room.getStatus())
                .currentStage(room.getCurrentStage())
                .members(memberDTOs)
                .build();
    }

    private void validateStatus(LiveGameRoom room, GameRoomStatus expected) {
        if (room.getStatus() != expected) {
            throw new IllegalStateException("房间状态不正确，当前: " + room.getStatus() + "，期望: " + expected);
        }
    }
}
