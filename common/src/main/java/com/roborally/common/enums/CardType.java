package com.roborally.common.enums;

public enum CardType {
    MOVE_1("Move 1", 1),
    MOVE_2("Move 2", 2),
    MOVE_3("Move 3", 3),
    BACKUP("Backup", -1),
    TURN_LEFT("Turn Left", 0),
    TURN_RIGHT("Turn Right", 0),
    U_TURN("U-Turn", 0);

    private final String displayName;
    private final int moveSteps; // 0 = rotation only, negative = backward

    CardType(String displayName, int moveSteps) {
        this.displayName = displayName;
        this.moveSteps = moveSteps;
    }

    public String getDisplayName() { return displayName; }
    public int getMoveSteps() { return moveSteps; }
    public boolean isMovement() { return moveSteps != 0; }
    public boolean isRotation() { return moveSteps == 0; }
}
