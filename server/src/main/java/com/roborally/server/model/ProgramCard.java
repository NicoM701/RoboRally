package com.roborally.server.model;

import com.roborally.common.enums.CardType;

/**
 * A single program card with type and priority.
 */
public class ProgramCard {

    private final int id;
    private final CardType type;
    private final int priority;
    private boolean blocked = false;
    private Long assignedPlayerId;

    public ProgramCard(int id, CardType type, int priority) {
        this.id = id;
        this.type = type;
        this.priority = priority;
    }

    public int getId() {
        return id;
    }

    public CardType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Long getAssignedPlayerId() {
        return assignedPlayerId;
    }

    public void setAssignedPlayerId(Long pid) {
        this.assignedPlayerId = pid;
    }
}
