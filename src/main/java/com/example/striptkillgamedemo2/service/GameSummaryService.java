package com.example.striptkillgamedemo2.service;

import org.bson.types.ObjectId;

import java.util.List;

/**
 * 游戏总结服务接口。
 *
 * <p>将游戏聊天记录总结为关键信息，存入 {@link com.example.striptkillgamedemo2.entity.mongo.GameRecord#getFullChatLog()}。
 * 当前实现返回原始消息作为占位；后续可替换为 AI 智能总结。</p>
 */
public interface GameSummaryService {

    /**
     * 总结游戏会话的聊天记录。
     *
     * @param roomId 游戏房间 ID
     * @return 关键信息字符串列表
     */
    List<String> summarize(ObjectId roomId);
}
