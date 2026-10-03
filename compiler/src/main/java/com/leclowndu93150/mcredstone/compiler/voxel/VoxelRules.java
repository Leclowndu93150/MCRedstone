package com.leclowndu93150.mcredstone.compiler.voxel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class VoxelRules {
    private VoxelRules() {
    }

    public static boolean conductor(Voxel voxel) {
        return voxel != null && voxel.kind().conductor;
    }

    public static boolean wire(Voxel voxel) {
        return voxel != null && voxel.kind() == Kind.WIRE;
    }

    public static boolean placeableAbove(Voxel voxel) {
        return voxel != null && voxel.kind().sturdy;
    }

    public static boolean wireConnectsTo(Voxel voxel, Dir fromWire) {
        if (voxel == null) {
            return false;
        }
        return switch (voxel.kind()) {
            case WIRE -> true;
            case REPEATER -> fromWire != null && voxel.dir().sameAxis(fromWire);
            case OBSERVER -> fromWire != null && fromWire == voxel.dir().opposite();
            default -> voxel.kind().signalSource && fromWire != null;
        };
    }

    public static Side connectingSide(VoxelGrid grid, long pos, Dir dir, boolean canConnectUp) {
        long relative = Pos.offset(pos, dir);
        Voxel relativeVoxel = grid.get(relative);
        if (canConnectUp && placeableAbove(relativeVoxel) && wireConnectsTo(grid.get(Pos.offset(relative, Dir.UP)), null)) {
            return Side.UP;
        }
        boolean none = !wireConnectsTo(relativeVoxel, dir)
            && (conductor(relativeVoxel) || !wireConnectsTo(grid.get(Pos.offset(relative, Dir.DOWN)), null));
        return none ? Side.NONE : Side.SIDE;
    }

    public static Map<Dir, Side> wireSides(VoxelGrid grid, long pos) {
        boolean canConnectUp = !conductor(grid.get(Pos.offset(pos, Dir.UP)));
        Map<Dir, Side> sides = new EnumMap<>(Dir.class);
        for (Dir dir : Dir.HORIZONTAL) {
            sides.put(dir, connectingSide(grid, pos, dir, canConnectUp));
        }
        boolean north = sides.get(Dir.NORTH).connected();
        boolean south = sides.get(Dir.SOUTH).connected();
        boolean east = sides.get(Dir.EAST).connected();
        boolean west = sides.get(Dir.WEST).connected();
        boolean northSouthEmpty = !north && !south;
        boolean eastWestEmpty = !east && !west;
        if (!west && northSouthEmpty) {
            sides.put(Dir.WEST, Side.SIDE);
        }
        if (!east && northSouthEmpty) {
            sides.put(Dir.EAST, Side.SIDE);
        }
        if (!north && eastWestEmpty) {
            sides.put(Dir.NORTH, Side.SIDE);
        }
        if (!south && eastWestEmpty) {
            sides.put(Dir.SOUTH, Side.SIDE);
        }
        return sides;
    }

    public static boolean wirePowers(VoxelGrid grid, long wirePos, Dir toward) {
        if (toward == Dir.DOWN) {
            return true;
        }
        if (toward == Dir.UP) {
            return false;
        }
        return wireSides(grid, wirePos).get(toward).connected();
    }

    public static List<Long> incomingWires(VoxelGrid grid, long pos) {
        List<Long> wires = new ArrayList<>();
        long above = Pos.offset(pos, Dir.UP);
        boolean aboveConductor = conductor(grid.get(above));
        for (Dir dir : Dir.HORIZONTAL) {
            long neighbor = Pos.offset(pos, dir);
            Voxel neighborVoxel = grid.get(neighbor);
            if (wire(neighborVoxel)) {
                wires.add(neighbor);
            }
            if (conductor(neighborVoxel) && !aboveConductor) {
                long diagonal = Pos.offset(neighbor, Dir.UP);
                if (wire(grid.get(diagonal))) {
                    wires.add(diagonal);
                }
            } else if (!conductor(neighborVoxel)) {
                long diagonal = Pos.offset(neighbor, Dir.DOWN);
                if (wire(grid.get(diagonal))) {
                    wires.add(diagonal);
                }
            }
        }
        return wires;
    }

    public static int weakEmission(Voxel voxel, Dir toward) {
        return switch (voxel.kind()) {
            case TORCH -> toward != Dir.DOWN ? 15 : 0;
            case WALL_TORCH -> toward != voxel.dir().opposite() ? 15 : 0;
            case REPEATER, COMPARATOR, OBSERVER -> toward == voxel.dir() ? 15 : 0;
            case LEVER, STONE_BUTTON, REDSTONE_BLOCK -> 15;
            default -> 0;
        };
    }

    public static int strongEmission(Voxel voxel, Dir toward) {
        return switch (voxel.kind()) {
            case TORCH, WALL_TORCH -> toward == Dir.UP ? 15 : 0;
            case REPEATER, COMPARATOR, OBSERVER -> toward == voxel.dir() ? 15 : 0;
            case LEVER, STONE_BUTTON -> toward == voxel.dir().opposite() ? 15 : 0;
            default -> 0;
        };
    }

    public static boolean isSource(Voxel voxel) {
        return switch (voxel.kind()) {
            case TORCH, WALL_TORCH, REPEATER, COMPARATOR, OBSERVER, LEVER, STONE_BUTTON -> true;
            default -> false;
        };
    }

    public static boolean isReader(Voxel voxel) {
        return switch (voxel.kind()) {
            case TORCH, WALL_TORCH, REPEATER, COMPARATOR, LAMP, OBSERVER, COPPER_BULB -> true;
            default -> false;
        };
    }

    public static long torchSupport(Voxel torch, long pos) {
        return torch.support(pos);
    }

    public static long diodeInput(Voxel diode, long pos) {
        return Pos.offset(pos, diode.dir().opposite());
    }
}
