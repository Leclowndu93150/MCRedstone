package com.leclowndu93150.mcredstone.compiler.voxel;

public record Voxel(Kind kind, Dir dir, int delay, boolean subtract) {
    public static final Voxel SOLID = new Voxel(Kind.SOLID, null, 0, false);
    public static final Voxel GLASS = new Voxel(Kind.GLASS, null, 0, false);
    public static final Voxel WIRE = new Voxel(Kind.WIRE, null, 0, false);
    public static final Voxel LAMP = new Voxel(Kind.LAMP, null, 0, false);
    public static final Voxel REDSTONE_BLOCK = new Voxel(Kind.REDSTONE_BLOCK, null, 0, false);
    public static final Voxel TORCH = new Voxel(Kind.TORCH, Dir.UP, 0, false);

    public static Voxel repeater(Dir output, int delay) {
        return new Voxel(Kind.REPEATER, output, delay, false);
    }

    public static Voxel comparator(Dir output, boolean subtract) {
        return new Voxel(Kind.COMPARATOR, output, 0, subtract);
    }

    public static Voxel wallTorch(Dir away) {
        return new Voxel(Kind.WALL_TORCH, away, 0, false);
    }

    public static Voxel wallLever(Dir away) {
        return new Voxel(Kind.LEVER, away, 0, false);
    }

    public static Voxel wallButton(Dir away) {
        return new Voxel(Kind.STONE_BUTTON, away, 0, false);
    }

    public static Voxel observer(Dir output) {
        return new Voxel(Kind.OBSERVER, output, 0, false);
    }

    public static Voxel copperBulb() {
        return new Voxel(Kind.COPPER_BULB, null, 0, false);
    }

    public long support(long pos) {
        return switch (kind) {
            case WALL_TORCH, LEVER, STONE_BUTTON -> Pos.offset(pos, dir.opposite());
            default -> Pos.offset(pos, Dir.DOWN);
        };
    }
}
