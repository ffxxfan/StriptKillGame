package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.RoleDTO;
import com.example.striptkillgamedemo2.dto.RoomDetailDTO;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import com.example.striptkillgamedemo2.entity.mongo.GameRoom;
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
        GameRoom room = gameRoomService.createRoom(userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDetailDTO> getRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(room));
    }

    @PutMapping("/{roomId}/script")
    public ResponseEntity<RoomDetailDTO> selectScript(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);

        ObjectId scriptId = new ObjectId(body.get("scriptId"));
        GameRoom updated = gameRoomService.selectScript(rid, scriptId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    @GetMapping("/{roomId}/roles")
    public ResponseEntity<List<RoleDTO>> getRoles(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);
        return ResponseEntity.ok(gameRoomService.getRoles(rid));
    }

    @PutMapping("/{roomId}/role")
    public ResponseEntity<RoomDetailDTO> selectRole(
            @PathVariable String roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);

        ObjectId roleId = new ObjectId(body.get("roleId"));
        GameRoom updated = gameRoomService.selectRole(rid, roleId, userId);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<RoomDetailDTO> startGame(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);
        GameRoom room = gameRoomService.getRoom(rid);
        gameRoomService.validateMembership(room, userId);

        GameRoom updated = gameFlowService.startGame(rid);
        return ResponseEntity.ok(gameRoomService.toDetailDTO(updated));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        ObjectId rid = new ObjectId(roomId);
        ObjectId userId = extractUserId(authentication);

        GameRoom room = gameRoomService.leaveRoom(rid, userId);

        if (room.getStatus() == GameRoomStatus.FINISHED) {
            gameFlowService.endGame(rid);
        }

        return ResponseEntity.ok(Map.of("message", "已离开房间"));
    }

    private ObjectId extractUserId(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        return new ObjectId(claims.getSubject());
    }
}
