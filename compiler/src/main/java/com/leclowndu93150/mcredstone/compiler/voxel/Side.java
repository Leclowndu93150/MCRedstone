package com.leclowndu93150.mcredstone.compiler.voxel;

public enum Side {
    NONE,
    SIDE,
    UP;

    public boolean connected() {
        return this != NONE;
    }
}
