package com.example.striptkillgamedemo2.controller;

import com.example.striptkillgamedemo2.dto.ScriptSummaryDTO;
import com.example.striptkillgamedemo2.service.ScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping("/random")
    public ResponseEntity<List<ScriptSummaryDTO>> getRandomScripts() {
        List<ScriptSummaryDTO> scripts = scriptService.getRandomScripts(8);
        return ResponseEntity.ok(scripts);
    }
}
