package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.RoleDTO;
import com.example.striptkillgamedemo2.dto.RoomDetailDTO;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
import com.example.striptkillgamedemo2.entity.mongo.Member;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.repository.GameRoomRepository;
import com.example.striptkillgamedemo2.repository.RoleRepository;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final ScriptRepository scriptRepository;
    private final RoleRepository roleRepository;

    public GameRoom createRoom(ObjectId userId) {
        Member creator = Member.builder()
                .userId(userId)
                .isAi(false)
                .isDm(false)
                .isOnline(true)
                .build();

        GameRoom room = GameRoom.builder()
                .status(GameRoomStatus.WAITING)
                .currentStage(0)
                .members(new ArrayList<>(List.of(creator)))
                .build();

        return gameRoomRepository.save(room);
    }

    public GameRoom getRoom(ObjectId roomId) {
        return gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));
    }

    public GameRoom selectScript(ObjectId roomId, ObjectId scriptId) {
        GameRoom room = getRoom(roomId);
        validateRoomStatus(room, GameRoomStatus.WAITING);

        scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("剧本不存在: " + scriptId));

        room.setScriptId(scriptId);
        return gameRoomRepository.save(room);
    }

    public List<RoleDTO> getRoles(ObjectId roomId) {
        GameRoom room = getRoom(roomId);
        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        List<Role> roles = roleRepository.findByScriptId(room.getScriptId());
        List<ObjectId> takenRoleIds = room.getMembers().stream()
                .filter(m -> m.getRoleId() != null && !m.isAi())
                .map(Member::getRoleId)
                .toList();

        return roles.stream().map(role -> RoleDTO.builder()
                .id(role.getId().toHexString())
                .name(role.getName())
                .avatar(role.getAvatar())
                .isNpc(role.isNpc())
                .isAvailable(!takenRoleIds.contains(role.getId()))
                .build()
        ).toList();
    }

    public GameRoom selectRole(ObjectId roomId, ObjectId roleId, ObjectId userId) {
        GameRoom room = getRoom(roomId);
        validateRoomStatus(room, GameRoomStatus.WAITING);

        if (room.getScriptId() == null) {
            throw new IllegalStateException("请先选择剧本");
        }

        Role selectedRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleId));

        if (!selectedRole.getScriptId().equals(room.getScriptId())) {
            throw new IllegalArgumentException("该角色不属于当前剧本");
        }

        // Assign role to the human player
        Member playerMember = room.getMembers().stream()
                .filter(m -> m.getUserId() != null && m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中"));

        playerMember.setRoleId(roleId);

        // Auto-create AI members for all other unoccupied roles
        List<Role> allRoles = roleRepository.findByScriptId(room.getScriptId());
        List<ObjectId> humanRoleIds = room.getMembers().stream()
                .filter(m -> !m.isAi() && m.getRoleId() != null)
                .map(Member::getRoleId)
                .toList();

        // Remove existing AI members (in case of re-selection)
        room.getMembers().removeIf(Member::isAi);

        for (Role role : allRoles) {
            if (!humanRoleIds.contains(role.getId())) {
                Member aiMember = Member.builder()
                        .roleId(role.getId())
                        .isAi(true)
                        .isDm(role.isNpc())
                        .isOnline(true)
                        .build();
                room.getMembers().add(aiMember);
            }
        }

        return gameRoomRepository.save(room);
    }

    public GameRoom leaveRoom(ObjectId roomId, ObjectId userId) {
        GameRoom room = getRoom(roomId);

        Member member = room.getMembers().stream()
                .filter(m -> m.getUserId() != null && m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("你不在该房间中"));

        member.setOnline(false);

        boolean anyHumanOnline = room.getMembers().stream()
                .anyMatch(m -> !m.isAi() && m.isOnline());

        if (!anyHumanOnline) {
            room.setStatus(GameRoomStatus.FINISHED);
            log.info("Room {} has no human players online, setting to FINISHED", roomId);
        }

        return gameRoomRepository.save(room);
    }

    public void validateMembership(GameRoom room, ObjectId userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getUserId() != null && m.getUserId().equals(userId));
        if (!isMember) {
            throw new IllegalStateException("你不在该房间中");
        }
    }

    private void validateRoomStatus(GameRoom room, GameRoomStatus expected) {
        if (room.getStatus() != expected) {
            throw new IllegalStateException("房间状态不正确，当前: " + room.getStatus() + "，期望: " + expected);
        }
    }

    public RoomDetailDTO toDetailDTO(GameRoom room) {
        String scriptTitle = null;
        if (room.getScriptId() != null) {
            scriptTitle = scriptRepository.findById(room.getScriptId())
                    .map(Script::getTitle)
                    .orElse(null);
        }

        List<Role> roles = room.getScriptId() != null
                ? roleRepository.findByScriptId(room.getScriptId())
                : List.of();

        List<RoomDetailDTO.MemberDTO> memberDTOs = room.getMembers().stream().map(m -> {
            Role role = roles.stream()
                    .filter(r -> r.getId().equals(m.getRoleId()))
                    .findFirst()
                    .orElse(null);

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
                .roomId(room.getRoomId().toHexString())
                .scriptId(room.getScriptId() != null ? room.getScriptId().toHexString() : null)
                .scriptTitle(scriptTitle)
                .status(room.getStatus())
                .currentStage(room.getCurrentStage())
                .members(memberDTOs)
                .build();
    }
}
