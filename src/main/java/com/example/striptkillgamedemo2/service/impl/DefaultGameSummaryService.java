package com.example.striptkillgamedemo2.service.impl;

import com.example.striptkillgamedemo2.service.GameSummaryService;
import com.example.striptkillgamedemo2.service.LiveGameRoomService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stub implementation of GameSummaryService.
 * Reads raw messages from Redis and returns them as the "summary".
 * Replace with AI summarization when ready.
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
