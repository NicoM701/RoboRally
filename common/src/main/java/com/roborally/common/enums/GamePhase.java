package com.roborally.common.enums;

public enum GamePhase {
    WAITING,           // In lobby, not started
    DEALING_CARDS,     // Phase I: Cards being dealt
    PROGRAMMING,       // Phase II: Players programming robots
    EXECUTING,         // Phase III: Program execution
    ROUND_CLEANUP,     // Phase IV: Repair & cleanup
    GAME_OVER          // Game finished
}
