package com.example.striptkillgamedemo2.ai.prompt;

import com.example.striptkillgamedemo2.config.AiEngineProperties;
import com.example.striptkillgamedemo2.entity.enums.PhaseType;
import com.example.striptkillgamedemo2.entity.mongo.*;
import com.example.striptkillgamedemo2.entity.redis.GameClueInstance;
import com.example.striptkillgamedemo2.entity.redis.GameMessage;
import com.example.striptkillgamedemo2.entity.redis.LiveGameRoom;
import com.example.striptkillgamedemo2.entity.enums.GameRoomStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;
    private Script script;
    private LiveGameRoom room;
    private Role roleA;
    private Role roleB;

    @BeforeEach
    void setUp() {
        AiEngineProperties props = new AiEngineProperties();
        promptBuilder = new PromptBuilder(props);

        roleA = Role.builder()
                .id(new ObjectId())
                .name("林默")
                .isNpc(false)
                .prompt("你是一名侦探，冷静理性。")
                .secret("你目睹了凶手逃跑的身影。")
                .searchPower(3)
                .build();

        roleB = Role.builder()
                .id(new ObjectId())
                .name("苏婉")
                .isNpc(false)
                .prompt("你是受害者的妹妹，情绪化。")
                .secret("你偷了受害者的遗嘱。")
                .searchPower(2)
                .build();

        Clue clue1 = Clue.builder()
                .id(new ObjectId())
                .title("血迹报告")
                .content("花园发现O型血迹")
                .build();

        StagePhase phase = StagePhase.builder()
                .phaseId("p1")
                .type(PhaseType.TURN_BASED)
                .speakOrder(List.of(roleA.getId().toHexString(), roleB.getId().toHexString()))
                .timeLimitSeconds(60)
                .dmInstruction("让角色依次自我介绍")
                .build();

        ScriptStage stage = ScriptStage.builder()
                .stageNumber(0)
                .stageTitle("第一幕：相遇")
                .phases(List.of(phase))
                .build();

        script = Script.builder()
                .id(new ObjectId())
                .title("暗夜庄园")
                .roles(List.of(roleA, roleB))
                .clues(List.of(clue1))
                .stages(List.of(stage))
                .build();

        room = LiveGameRoom.builder()
                .roomId(new ObjectId().toHexString())
                .scriptId(script.getId().toHexString())
                .status(GameRoomStatus.PLAYING)
                .currentStage(0)
                .currentPhaseIndex(0)
                .build();
    }

    @Test
    void buildDmPrompt_containsScriptTruth() {
        String prompt = promptBuilder.buildDmPrompt(room, script, List.of(), List.of());

        assertTrue(prompt.contains("暗夜庄园"));
        assertTrue(prompt.contains("林默"));
        assertTrue(prompt.contains("苏婉"));
        // DM can see all secrets
        assertTrue(prompt.contains("你目睹了凶手逃跑的身影"));
        assertTrue(prompt.contains("你偷了受害者的遗嘱"));
    }

    @Test
    void buildAgentPrompt_sandboxesSecrets() {
        String promptA = promptBuilder.buildAgentPrompt(
                room, script, roleA, List.of(), List.of(), List.of());

        // Agent A sees own secret
        assertTrue(promptA.contains("你目睹了凶手逃跑的身影"));
        // Agent A does NOT see roleB's secret
        assertFalse(promptA.contains("你偷了受害者的遗嘱"));
    }

    @Test
    void buildAgentPrompt_filtersClues() {
        ObjectId clueId = script.getClues().get(0).getId();

        // Clue owned by roleA only
        GameClueInstance ownedClue = GameClueInstance.builder()
                .id(new ObjectId())
                .clueId(clueId)
                .isFound(true)
                .isPublic(false)
                .ownerRoleIds(List.of(roleA.getId()))
                .build();

        String promptA = promptBuilder.buildAgentPrompt(
                room, script, roleA, List.of(ownedClue), List.of(), List.of());
        String promptB = promptBuilder.buildAgentPrompt(
                room, script, roleB, List.of(ownedClue), List.of(), List.of());

        assertTrue(promptA.contains("血迹报告"));
        assertFalse(promptB.contains("血迹报告"));
    }

    @Test
    void buildAgentPrompt_filtersMessages() {
        // Public message
        GameMessage publicMsg = GameMessage.builder()
                .messageId(new ObjectId())
                .senderRoleId(roleA.getId())
                .senderRoleName("林默")
                .content("大家好")
                .timestamp(LocalDateTime.now())
                .build();

        // Private message to roleA only
        GameMessage privateMsg = GameMessage.builder()
                .messageId(new ObjectId())
                .senderRoleId(roleB.getId())
                .senderRoleName("苏婉")
                .content("我有话私下跟你说")
                .receiverRoleIds(List.of(roleA.getId()))
                .timestamp(LocalDateTime.now())
                .build();

        String promptA = promptBuilder.buildAgentPrompt(
                room, script, roleA, List.of(), List.of(), List.of(publicMsg, privateMsg));
        String promptB = promptBuilder.buildAgentPrompt(
                room, script, roleB, List.of(), List.of(), List.of(publicMsg, privateMsg));

        // A sees both
        assertTrue(promptA.contains("大家好"));
        assertTrue(promptA.contains("我有话私下跟你说"));
        // B sees only public
        assertTrue(promptB.contains("大家好"));
        assertFalse(promptB.contains("我有话私下跟你说"));
    }

    @Test
    void estimateTokens_chineseText() {
        // 3.5 chars per token default
        String text = "这是一段测试文本共十四个字符"; // 14 chars
        int tokens = promptBuilder.estimateTokens(text);
        assertEquals(4, tokens); // 14 / 3.5 = 4
    }

    @Test
    void loadTemplate_prefersOverride() {
        String result = promptBuilder.loadTemplate("prompts/dm-system.md", "自定义DM指令");
        assertEquals("自定义DM指令", result);
    }

    @Test
    void loadTemplate_fallsBackToClasspath() {
        String result = promptBuilder.loadTemplate("prompts/dm-system.md", null);
        assertTrue(result.contains("主持人"));
    }
}
