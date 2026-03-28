package com.example.striptkillgamedemo2.service.impl;

import com.example.striptkillgamedemo2.service.GameSummaryService;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DefaultGameSummaryService implements GameSummaryService {

    @Override
    public List<String> summarize(ObjectId roomId) {
        return List.of("Game ended at " + LocalDateTime.now());
    }
}
