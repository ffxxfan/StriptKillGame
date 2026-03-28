package com.example.striptkillgamedemo2.service;

import com.example.striptkillgamedemo2.dto.ScriptSummaryDTO;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;

    public List<ScriptSummaryDTO> getRandomScripts(int count) {
        List<Script> scripts = scriptRepository.findRandomScripts(count);
        return scripts.stream().map(this::toSummaryDTO).toList();
    }

    private ScriptSummaryDTO toSummaryDTO(Script script) {
        return ScriptSummaryDTO.builder()
                .id(script.getId().toHexString())
                .title(script.getTitle())
                .description(script.getDescription())
                .difficulty(script.getDifficulty())
                .playerCount(script.getPlayerCount())
                .coverImage(script.getCoverImage())
                .build();
    }
}
