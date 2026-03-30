package com.example.striptkillgamedemo2.ai.event;

import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEvent;

@Getter
public class DmRequestEvent extends ApplicationEvent {
    private final String roomId;
    private final ObjectId triggerRoleId;
    private final String reason;

    public DmRequestEvent(Object source, String roomId, ObjectId triggerRoleId, String reason) {
        super(source);
        this.roomId = roomId;
        this.triggerRoleId = triggerRoleId;
        this.reason = reason;
    }
}
