package com.cube.memorygames.logic;

public interface GameFlowState {
    void applyState();

    int getDuration();
}
