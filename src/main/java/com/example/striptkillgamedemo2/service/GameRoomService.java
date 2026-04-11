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
/**
 * 游戏房间管理服务。
 *
 * <p>提供房间生命周期管理（创建、加入、离开）、剧本选择、角色分配等功能。
 * 自动为未被玩家选择的角色分配 AI 代理，NPC 角色设为 DM。</p>
 *
 * @see LiveGameRoomService
 */
public class GameRoomService {

    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final ScriptRepository scriptRepository;

    /**
     * 创建新房间。如果用户已有活跃房间，先清理旧房间。
     *
     * @param userId 创建者用户 ID
     * @return 新创建的游戏房间
     */
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

    /**
     * 获取房间，不存在时抛出异常。
     *
     * @param roomId 房间 ID
     * @return 游戏房间
     * @throws IllegalArgumentException 房间不存在或已结束
     */
    public LiveGameRoom getRoom(String roomId) {
        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在或已结束: " + roomId);
        }
        return room;
    }

    /**
     * 验证用户是否为房间成员。
     *
     * @param room   游戏房间
     * @param userId 用户 ID
     * @throws IllegalStateException 用户不在房间中
     */
    public void validateMembership(LiveGameRoom room, ObjectId userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()));
        if (!isMember) {
            throw new IllegalStateException("你不在该房间中");
        }
    }

    /**
     * 选择剧本并将其完整内容加载到 Redis 缓存。
     *
     * @param roomId   房间 ID
     * @param scriptId 剧本 ID
     * @param userId   操作者用户 ID
     * @return 更新后的游戏房间
     */
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

    /**
     * 获取房间可选的角色列表，标记已被选择的角色为不可用。
     *
     * @param roomId 房间 ID
     * @return 角色 DTO 列表
     */
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

    /**
     * 为用户选择角色。未被选择的角色自动分配 AI 代理，NPC 角色设为 DM。
     *
     * @param roomId 房间 ID
     * @param roleId 角色 ID
     * @param userId 用户 ID
     * @return 更新后的游戏房间
     */
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
     * 用户离开房间（标记为离线）。
     *
     * <p>如果所有真人玩家都已离线：游戏中的房间设置空闲 TTL 等待自动过期，
     * 等待中的房间直接移除。实际的 Redis 清理在 {@link GameFlowService#endGame} 中执行。</p>
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
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

    /**
     * 查找用户在房间中的角色 ID。
     *
     * @param room   游戏房间
     * @param userId 用户 ID
     * @return 角色 ID
     * @throws IllegalStateException 用户不在房间中或未选择角色
     */
    public ObjectId findRoleIdForUser(LiveGameRoom room, ObjectId userId) {
        return room.getMembers().stream()
                .filter(m -> m.getUserId() != null
                        && userId.toHexString().equals(m.getUserId().toHexString()))
                .map(Member::getRoleId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中或未选择角色"));
    }

    /**
     * 将游戏房间运行时对象转换为房间详情 DTO（含角色名、头像等信息）。
     *
     * @param room 游戏房间
     * @return 房间详情 DTO
     */
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
