package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChatMessageEvent extends ApplicationEvent {
    private final String roomId;
    private final ObjectId senderRoleId;
    private final String content;
    private final boolean fromAi;
    private final boolean lastAiRound;

    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi) {
        this(source, roomId, senderRoleId, content, fromAi, false);
    }

    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi, boolean lastAiRound) {
        super(source);
        this.roomId = roomId;
        this.senderRoleId = senderRoleId;
        this.content = content;
        this.fromAi = fromAi;
        this.lastAiRound = lastAiRound;
    }
}
