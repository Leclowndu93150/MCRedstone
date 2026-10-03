package com.leclowndu93150.mcredstone.compiler.route;

public enum MoveType {
    DUST(0),
    REPEATER(3),
    STAIR_UP(4),
    STAIR_DOWN(4);

    final float penalty;

    MoveType(float penalty) {
        this.penalty = penalty;
    }
}
