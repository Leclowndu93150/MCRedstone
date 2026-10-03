package com.leclowndu93150.mcredstone.compiler.layout;

import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;

public final class Ascii {
    private Ascii() {
    }

    public static String render(VoxelGrid grid) {
        int[] bounds = grid.bounds();
        StringBuilder out = new StringBuilder();
        for (int y = bounds[1]; y <= bounds[4]; y++) {
            out.append("y=").append(y).append('\n');
            out.append("     ");
            for (int x = bounds[0]; x <= bounds[3]; x++) {
                out.append(Math.floorMod(x, 10));
            }
            out.append('\n');
            for (int z = bounds[2]; z <= bounds[5]; z++) {
                out.append(String.format("%4d ", z));
                for (int x = bounds[0]; x <= bounds[3]; x++) {
                    out.append(symbol(grid.get(x, y, z), y));
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static char symbol(Voxel voxel, int y) {
        if (voxel == null) {
            return y % 2 == 0 ? ' ' : '.';
        }
        return switch (voxel.kind()) {
            case SOLID -> '#';
            case GLASS -> y % 2 == 0 ? '.' : 'g';
            case WIRE -> y % 2 == 0 ? '~' : 'w';
            case TORCH -> 'i';
            case WALL_TORCH -> 'T';
            case REPEATER -> switch (voxel.dir()) {
                case NORTH -> '^';
                case SOUTH -> 'v';
                case WEST -> '<';
                case EAST -> '>';
                default -> '?';
            };
            case COMPARATOR -> 'C';
            case LAMP -> 'L';
            case LEVER -> 'l';
            case STONE_BUTTON -> 'b';
            case REDSTONE_BLOCK -> 'R';
            case OBSERVER -> 'O';
            case COPPER_BULB -> 'B';
        };
    }
}
