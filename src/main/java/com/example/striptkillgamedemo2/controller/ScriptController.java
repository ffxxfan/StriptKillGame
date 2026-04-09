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

/**
 * 剧本相关接口控制器。
 * <p>
 * 提供首页随机剧本推荐、以及当前用户在所参与房间内可见的剧本阶段内容查询。
 * 所有接口统一挂载在 {@code /api/scripts} 路径下。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;
    private final LiveGameRoomService liveGameRoomService;
    private final ScriptCacheService scriptCacheService;
    private final GameRoomService gameRoomService;

    /** 首页随机剧本推荐的条数，可由 {@code game.script.generate.number} 配置覆盖。 */
    @Value("${game.script.generate.number:8}")
    private Integer scriptGenerateNumber;

    /**
     * 获取随机剧本列表（首页推荐）。
     *
     * @return 剧本摘要 DTO 列表
     */
    @GetMapping("/random")
    public ResponseEntity<List<ScriptSummaryDTO>> getRandomScripts() {
        List<ScriptSummaryDTO> scripts = scriptService.getRandomScripts(scriptGenerateNumber);
        return ResponseEntity.ok(scripts);
    }

    /**
     * 获取当前用户在其活跃房间中已解锁的阶段内容。
     * <p>
     * 仅返回阶段索引 {@code <= currentStage} 的内容，防止玩家提前查看未解锁剧情。
     * 若用户当前不在任何活跃房间或房间无剧本，则返回空列表。
     * </p>
     *
     * @param authentication 当前用户认证信息
     * @return 当前角色可见的各幕阶段内容列表
     */
    @GetMapping("/my-content")
    public ResponseEntity<List<StageContentDTO>> getMyContent(Authentication authentication) {
        ObjectId userId = extractUserId(authentication);

        // 查找用户当前所在的活跃房间
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

        // 解析当前用户在房间中扮演的角色 ID
        ObjectId roleId = gameRoomService.findRoleIdForUser(room, userId);
        String roleIdHex = roleId != null ? roleId.toHexString() : null;

        // 构建已解锁阶段列表：索引 <= currentStage 的部分
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

    /**
     * 从 {@link Authentication} 中提取用户 ID（ObjectId）。
     *
     * @param authentication 当前登录态
     * @return 用户 ID
     */
    private ObjectId extractUserId(Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        return new ObjectId(claims.getSubject());
    }
}
