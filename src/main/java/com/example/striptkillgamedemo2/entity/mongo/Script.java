package com.example.striptkillgamedemo2.entity.mongo;

import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/**
 * 剧本实体。
 * <p>
 * 对应 MongoDB 集合 {@code scripts}，一个剧本模板包含完整的静态游戏内容：角色库、
 * 线索库、阶段流程等。真人/AI 玩家进入房间后，运行时状态在 {@code LiveGameRoom} 中演化，
 * 但剧本模板内容在这里是只读的。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scripts")
public class Script {
    /** 剧本主键。 */
    @Id
    private ObjectId id;

    /** 剧本标题。 */
    @NotBlank
    private String title;
    /** 剧本简介。 */
    private String description;
    /** 剧本难度。 */
    private ScriptDifficulty difficulty;
    /** 所需玩家人数。 */
    private int playerCount;
    /** 封面图 URL。 */
    private String coverImage;

    // --- 核心内嵌数据 ---

    /** 角色库：定义剧本中所有角色的设定与 AI 灵魂。长度需在 2~15 之间。 */
    @Size(min = 2, max = 15)
    private List<Role> roles;

    /** 线索库：存储本剧本所有静态线索定义。 */
    private List<Clue> clues;

    /** 阶段流转定义：描述每一幕对不同角色解锁的内容与子阶段。 */
    private List<ScriptStage> stages;

    // --- 其他配置 ---
    /** DM AI 全局设定的 JSON 配置文本。 */
    private String dmConfig;
    /** 剧本版本号，递增以便于跟踪改动、避免破坏性升级。 */
    private int version = 1;
}
