package com.leclowndu93150.mcredstone.compiler.voxel;

import java.util.List;

public enum Dir {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public static final List<Dir> HORIZONTAL = List.of(NORTH, EAST, SOUTH, WEST);
    public static final List<Dir> ALL = List.of(values());

    public final int dx;
    public final int dy;
    public final int dz;

    Dir(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public Dir opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public Dir clockwise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this;
        };
    }

    public boolean horizontal() {
        return dy == 0;
    }

    public boolean sameAxis(Dir other) {
        return this == other || this == other.opposite();
    }
}
