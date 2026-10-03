package com.leclowndu93150.mcredstone.compiler.route;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;

final class Move {
    static final Voxel RESERVE = new Voxel(null, null, 0, false);
    private static final Voxel[] REPEATERS = new Voxel[Dir.values().length];

    static {
        for (Dir dir : Dir.HORIZONTAL) {
            REPEATERS[dir.ordinal()] = Voxel.repeater(dir, 1);
        }
    }

    final int[] positions = new int[8];
    final Voxel[] voxels = new Voxel[8];
    int count;
    int landing;
    int strength;
    int hanging;

    boolean build(RouteGrid grid, MoveType type, int from, int fromStrength, Dir dir) {
        count = 0;
        hanging = 0;
        int step = grid.delta(dir.dx, 0, dir.dz);
        int up = grid.strideY;
        int e1 = from + step;
        int e2 = e1 + step;
        switch (type) {
            case DUST -> {
                if (fromStrength < 2) {
                    return false;
                }
                hang(grid, e1, up);
                add(e1, Voxel.WIRE);
                landing = e1;
                strength = fromStrength - 1;
            }
            case REPEATER -> {
                hang(grid, e1, up);
                hang(grid, e2, up);
                add(e1, REPEATERS[dir.ordinal()]);
                add(e2, Voxel.WIRE);
                landing = e2;
                strength = 15;
            }
            case STAIR_UP -> {
                if (fromStrength < 3) {
                    return false;
                }
                if (grid.woolAbove(from)) {
                    add(from + up, RESERVE);
                }
                add(e1, Voxel.GLASS);
                add(e1 + up, Voxel.WIRE);
                add(e1 + 2 * up, RESERVE);
                landing = e2 + 2 * up;
                if (grid.unsupported(landing)) {
                    add(e2 + up, Voxel.SOLID);
                }
                add(landing, Voxel.WIRE);
                strength = fromStrength - 2;
            }
            case STAIR_DOWN -> {
                if (fromStrength < 3) {
                    return false;
                }
                add(from - up, Voxel.SOLID);
                add(e1, RESERVE);
                add(e1 - up, Voxel.WIRE);
                add(e1 - 2 * up, Voxel.SOLID);
                landing = e2 - 2 * up;
                if (grid.woolAbove(landing)) {
                    add(e2 - up, RESERVE);
                }
                if (grid.unsupported(landing)) {
                    add(e2 - 3 * up, Voxel.SOLID);
                }
                add(landing, Voxel.WIRE);
                strength = fromStrength - 2;
            }
        }
        return true;
    }

    private void hang(RouteGrid grid, int position, int up) {
        if (grid.unsupported(position)) {
            add(position - up, Voxel.GLASS);
            hanging++;
        }
    }

    private void add(int position, Voxel voxel) {
        positions[count] = position;
        voxels[count] = voxel;
        count++;
    }
}
