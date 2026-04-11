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
/**
 * 剧本服务。
 *
 * <p>提供剧本相关的业务操作，如随机推荐剧本列表等。</p>
 */
public class ScriptService {

    private final ScriptRepository scriptRepository;

    /**
     * 随机获取指定数量的剧本摘要。
     *
     * @param count 需要获取的剧本数量
     * @return 剧本摘要 DTO 列表
     */
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
