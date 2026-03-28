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

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;
    private final GameFlowService gameFlowService;

    @PostMapping
    public ResponseEntity<RoomDetailDTO> createRoom(Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.createRoom(userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDetailDTO> getRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

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

    @GetMapping("/{roomId}/roles")
    public ResponseEntity<List<RoleDTO>> getRoles(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.getRoles(roomId));
    }

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

    @GetMapping("/{roomId}/stage")
    public ResponseEntity<StageContentDTO> getStageContent(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        LiveGameRoom room = gameRoomService.getRoom(roomId);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameFlowService.getStageContent(roomId, userId));
    }

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

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId userId = extractUserId(authentication);
        gameRoomService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(Map.of("message", "已离开房间"));
    }

    private ObjectId extractUserId(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        return new ObjectId(claims.getSubject());
    }
}
