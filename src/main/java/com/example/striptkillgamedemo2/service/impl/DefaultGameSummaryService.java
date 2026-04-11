package com.example.striptkillgamedemo2.service.impl;

import com.example.striptkillgamedemo2.service.GameSummaryService;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 游戏总结服务的默认实现（占位）。
 *
 * <p>从 Redis 读取原始消息并直接返回作为"总结"。
 * 后续可替换为 AI 智能总结实现。</p>
 */
@Service
@RequiredArgsConstructor
public class DefaultGameSummaryService implements GameSummaryService {

    private final LiveGameRoomService liveGameRoomService;

    @Override
    public List<String> summarize(ObjectId roomId) {
        List<String> messages = liveGameRoomService.getMessages(roomId);
        if (messages.isEmpty()) {
            return List.of("本局游戏无聊天记录");
        }
        // TODO: replace with AI summarization (e.g., call LLM with messages as context)
        return messages;
    }
}
