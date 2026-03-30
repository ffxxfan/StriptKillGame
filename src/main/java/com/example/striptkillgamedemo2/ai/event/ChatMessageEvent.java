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

    public ChatMessageEvent(Object source, String roomId, ObjectId senderRoleId,
                            String content, boolean fromAi) {
        super(source);
        this.roomId = roomId;
        this.senderRoleId = senderRoleId;
        this.content = content;
        this.fromAi = fromAi;
    }
}
