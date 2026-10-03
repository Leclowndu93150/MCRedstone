package com.leclowndu93150.mcredstone.compiler.voxel;

public enum Kind {
    SOLID(true, true, false),
    GLASS(false, true, false),
    WIRE(false, false, true),
    TORCH(false, false, true),
    WALL_TORCH(false, false, true),
    REPEATER(false, false, true),
    COMPARATOR(false, false, true),
    LAMP(true, true, false),
    OBSERVER(false, true, true),
    COPPER_BULB(false, true, false),
    LEVER(false, false, true),
    STONE_BUTTON(false, false, true),
    REDSTONE_BLOCK(false, true, true);

    public final boolean conductor;
    public final boolean sturdy;
    public final boolean signalSource;

    Kind(boolean conductor, boolean sturdy, boolean signalSource) {
        this.conductor = conductor;
        this.sturdy = sturdy;
        this.signalSource = signalSource;
    }
}
