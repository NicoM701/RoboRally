package com.roborally.server.model;

import com.roborally.common.enums.Direction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's robot on the board.
 */
public class Robot {

    private final Long playerId;
    private final int robotIndex; // 0-7 for color assignment
    private int x;
    private int y;
    private Direction direction;
    private int damage = 0;
    private int lives = 3;
    private int archiveX;
    private int archiveY;
    private boolean destroyed = false;
    private int nextCheckpoint = 1;
    private final ProgramCard[] programSlots = new ProgramCard[5];

    public Robot(Long playerId, int robotIndex, int startX, int startY, Direction startDir) {
        this.playerId = playerId;
        this.robotIndex = robotIndex;
        this.x = startX;
        this.y = startY;
        this.direction = startDir;
        this.archiveX = startX;
        this.archiveY = startY;
    }

    // ─── Position ───────────────────────────────────────

    public Long getPlayerId() {
        return playerId;
    }

    public int getRobotIndex() {
        return robotIndex;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction dir) {
        this.direction = dir;
    }

    // ─── Damage + Lives ─────────────────────────────────

    public int getDamage() {
        return damage;
    }

    public void addDamage(int amount) {
        this.damage = Math.min(10, this.damage + amount);
    }

    public void repair(int amount) {
        this.damage = Math.max(0, this.damage - amount);
    }

    public int getLives() {
        return lives;
    }

    public void loseLife() {
        this.lives--;
    }

    public boolean isAlive() {
        return lives > 0;
    }

    /** Number of cards dealt = 9 - damage */
    public int getCardsToReceive() {
        return Math.max(0, 9 - damage);
    }

    /**
     * Number of blocked register slots (from bottom: slot 5 blocks at 5 damage,
     * slot 4 at 6, etc.)
     */
    public int getBlockedSlots() {
        return Math.max(0, damage - 4);
    }

    // ─── Archive ────────────────────────────────────────

    public int getArchiveX() {
        return archiveX;
    }

    public int getArchiveY() {
        return archiveY;
    }

    public void setArchive(int x, int y) {
        this.archiveX = x;
        this.archiveY = y;
    }

    // ─── Destruction + Spawn ────────────────────────────

    public boolean isDestroyed() {
        return destroyed;
    }

    public void destroy() {
        this.destroyed = true;
    }

    public void respawn(int x, int y, Direction dir) {
        this.x = x;
        this.y = y;
        this.direction = dir;
        this.destroyed = false;
        this.damage = Math.min(this.damage + 2, 10); // +2 damage on respawn
    }

    // ─── Checkpoints ────────────────────────────────────

    public int getNextCheckpoint() {
        return nextCheckpoint;
    }

    public void advanceCheckpoint() {
        nextCheckpoint++;
    }

    // ─── Program Slots ──────────────────────────────────

    public ProgramCard[] getProgramSlots() {
        return programSlots;
    }

    public ProgramCard getSlot(int index) {
        return programSlots[index];
    }

    public void setSlot(int index, ProgramCard card) {
        programSlots[index] = card;
    }

    public void clearProgram() {
        int blocked = getBlockedSlots();
        // Only clear non-blocked slots
        for (int i = 0; i < 5 - blocked; i++) {
            programSlots[i] = null;
        }
    }

    /**
     * Serialize robot state for client.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("playerId", playerId);
        map.put("robotIndex", robotIndex);
        map.put("x", x);
        map.put("y", y);
        map.put("direction", direction.name());
        map.put("damage", damage);
        map.put("lives", lives);
        map.put("destroyed", destroyed);
        map.put("nextCheckpoint", nextCheckpoint);
        return map;
    }
}
