package com.leclowndu93150.mcredstone.compiler.lib;

public record Edge(int fromDx, int fromDy, int fromDz, int toDx, int toDy, int toDz, boolean side) {
    public Edge(int fromDx, int fromDy, int fromDz, int toDx, int toDy, int toDz) {
        this(fromDx, fromDy, fromDz, toDx, toDy, toDz, false);
    }
}
