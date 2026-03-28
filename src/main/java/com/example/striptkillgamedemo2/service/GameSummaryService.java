package com.example.striptkillgamedemo2.service;

import org.bson.types.ObjectId;

import java.util.List;

/**
 * Game summary service interface.
 * Summarizes game chat into key information for GameRecord.fullChatLog.
 * Current implementation returns a placeholder; replace with AI summarization later.
 */
public interface GameSummaryService {

    /**
     * Summarize the game session.
     *
     * @param roomId the game room ID
     * @return list of key information strings for fullChatLog
     */
    List<String> summarize(ObjectId roomId);
}
