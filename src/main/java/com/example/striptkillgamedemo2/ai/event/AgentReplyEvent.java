package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

@Getter
public class AgentReplyEvent extends ApplicationEvent {
    private final String roomId;
    private final ObjectId roleId;

    public AgentReplyEvent(Object source, String roomId, ObjectId roleId) {
        super(source);
        this.roomId = roomId;
        this.roleId = roleId;
    }
}
