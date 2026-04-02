package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.ScriptSummaryDTO;
import com.example.striptkillgamedemo2.dto.StageContentDTO;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.service.GameRoomService;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import com.example.striptkillgamedemo2.service.ScriptCacheService;
import com.example.striptkillgamedemo2.service.ScriptService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final GameRoomService gameRoomService;

    @Value("${game.script.generate.number:8}")
    private Integer scriptGenerateNumber;

    @GetMapping("/random")
    public ResponseEntity<List<ScriptSummaryDTO>> getRandomScripts() {
        List<ScriptSummaryDTO> scripts = scriptService.getRandomScripts(scriptGenerateNumber);
        return ResponseEntity.ok(scripts);
    }

    /**
     * Returns all unlocked stage content for the current user.
     * Only stages with index <= currentStage are returned (no future content).
     */
    @GetMapping("/my-content")
    public ResponseEntity<List<StageContentDTO>> getMyContent(Authentication authentication) {
        ObjectId userId = extractUserId(authentication);

        // Find the user's active room
        String roomId = liveGameRoomService.getActiveRoomId(userId);
        if (roomId == null) {
            return ResponseEntity.ok(List.of());
        }

        LiveGameRoom room = liveGameRoomService.get(roomId);
        if (room == null || room.getScriptId() == null) {
            return ResponseEntity.ok(List.of());
        }

        Script script = scriptCacheService.getScript(new ObjectId(room.getScriptId()));
        List<ScriptStage> stages = script.getStages();
        if (stages == null || stages.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Resolve this user's roleId
        ObjectId roleId = gameRoomService.findRoleIdForUser(room, userId);
        String roleIdHex = roleId != null ? roleId.toHexString() : null;

        // Build unlocked stages: index <= currentStage only
        int currentStage = room.getCurrentStage();
        List<StageContentDTO> result = new ArrayList<>();

        for (int i = 0; i <= currentStage && i < stages.size(); i++) {
            ScriptStage stage = stages.get(i);
            String content = null;
            if (roleIdHex != null && stage.getContentMap() != null) {
                content = stage.getContentMap().get(roleIdHex);
            }

            result.add(StageContentDTO.builder()
                    .stageNumber(stage.getStageNumber())
                    .stageTitle(stage.getStageTitle())
                    .content(content)
                    .audioUrl(stage.getAudioUrl())
                    .totalStages(stages.size())
                    .isLastStage(i >= stages.size() - 1)
                    .build());
        }

        return ResponseEntity.ok(result);
    }

    private ObjectId extractUserId(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        return new ObjectId(claims.getSubject());
    }
}
