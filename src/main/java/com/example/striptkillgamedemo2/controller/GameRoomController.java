package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.RoleDTO;
import com.example.striptkillgamedemo2.dto.RoomDetailDTO;
import com.example.striptkillgamedemo2.dto.StageContentDTO;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.GameFlowService;
import com.example.striptkillgamedemo2.service.GameRoomService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 游戏房间控制器。
 * <p>
 * 负责房间的创建、查询、选剧本、选角色、开始游戏、阶段推进、离开房间等接口。
 * 所有接口统一挂载在 {@code /api/rooms} 路径下，需要登录鉴权。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;
    private final GameFlowService gameFlowService;

    /**
     * 创建房间。
     *
     * @param authentication 当前用户认证信息
     * @return 新建房间的详情
     */
    @PostMapping
    public ResponseEntity<RoomDetailDTO> createRoom(Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.createRoom(userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    /**
     * 获取房间详情；仅房间成员可访问。
     *
     * @param roomId         房间 ID
     * @param authentication 当前用户认证信息
     * @return 房间详情 DTO
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDetailDTO> getRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    /**
     * 为房间选择剧本。
     *
     * @param roomId         房间 ID
     * @param body           请求体，需包含 {@code scriptId} 字段
     * @param authentication 当前用户认证信息
     * @return 更新后的房间详情
     */
    @PutMapping("/{roomId}/script")
    public ResponseEntity<RoomDetailDTO> selectScript(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        ObjectId scriptId = new ObjectId(body.get("scriptId"));
        LiveGameRoom room = gameRoomService.selectScript(roomId, scriptId, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    /**
     * 获取当前房间可选角色列表。
     *
     * @param roomId         房间 ID
     * @param authentication 当前用户认证信息
     * @return 角色列表（含占用状态）
     */
    @GetMapping("/{roomId}/roles")
    public ResponseEntity<List<RoleDTO>> getRoles(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.getRoles(roomId));
    }

    /**
     * 当前用户选择角色。
     *
     * @param roomId         房间 ID
     * @param body           请求体，需包含 {@code roleId} 字段
     * @param authentication 当前用户认证信息
     * @return 更新后的房间详情
     */
    @PutMapping("/{roomId}/role")
    public ResponseEntity<RoomDetailDTO> selectRole(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        ObjectId roleId = new ObjectId(body.get("roleId"));
        LiveGameRoom room = gameRoomService.selectRole(roomId, roleId, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    /**
     * 开始游戏：由房主触发，将房间状态切换为 PLAYING 并进入首个阶段。
     *
     * @param roomId         房间 ID
     * @param authentication 当前用户认证信息
     * @return 开始后的房间详情
     */
    @PostMapping("/{roomId}/start")
    public ResponseEntity<RoomDetailDTO> startGame(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        LiveGameRoom updated = gameFlowService.startGame(roomId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    /**
     * 获取当前用户在当前阶段可见的剧情内容。
     *
     * @param roomId         房间 ID
     * @param authentication 当前用户认证信息
     * @return 阶段内容 DTO
     */
    @GetMapping("/{roomId}/stage")
    public ResponseEntity<StageContentDTO> getStageContent(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameFlowService.getStageContent(roomId, userId));
    }

    /**
     * 推进到下一个阶段。若已是最后一个阶段，则游戏进入 FINISHED 状态并返回空壳 DTO。
     *
     * @param roomId         房间 ID
     * @param authentication 当前用户认证信息
     * @return 更新后的房间详情；若游戏结束则返回仅含 {@code FINISHED} 状态的 DTO
     */
    @PostMapping("/{roomId}/stage/advance")
    public ResponseEntity<RoomDetailDTO> advanceStage(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        LiveGameRoom updated = gameFlowService.advanceStage(roomId);
        if (updated == null) {
            return ResponseEntity.ok(RoomDetailDTO.builder().status(com.example.striptkillgamedemo2.entity.enums.GameRoomStatus.FINISHED).build());
        }
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    /**
     * 离开房间。
     *
     * @param roomId         房间 ID
     * @param authentication 当前用户认证信息
     * @return 成功提示
     */
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        gameRoomService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(Map.of("message", "已离开房间"));
    }

    /**
     * 从 {@link Authentication} 中提取用户 ID。
     */
    private ObjectId extractUserId(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        return new ObjectId(claims.getSubject());
    }
}
